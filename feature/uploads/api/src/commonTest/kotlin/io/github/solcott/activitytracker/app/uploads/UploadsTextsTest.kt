package io.github.solcott.activitytracker.app.uploads

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.DateText
import io.github.solcott.activitytracker.data.activity.ImportSaveStatus
import io.github.solcott.activitytracker.data.activity.RejectedChange
import io.github.solcott.activitytracker.data.activity.UploadImport
import io.github.solcott.activitytracker.data.activity.UploadItem
import io.github.solcott.activitytracker.data.activity.UploadQueueView
import io.github.solcott.activitytracker.data.activity.UploadSource
import io.github.solcott.activitytracker.data.activity.UploadState
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.dataresult.Origin
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.LoadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone

/** What the upload queue says and offers, which both UIs ask (dev/shared-display-text, "The upload queue reads the same in both UIs"). */
class UploadsTextsTest {
    private val now = Instant.parse("2026-06-01T12:00:00Z")
    private val utc = TimeZone.UTC

    private fun written(text: String) = AppText.written(text)

    @Suppress("LongParameterList") // The item's fields a test varies, defaulted.
    private fun item(id: Long, state: UploadState, title: String? = null, isEdit: Boolean = false, sent: Int = 0, rkey: String? = "3k$id") =
        UploadItem(
            id = id,
            uri = rkey?.let { AtUri("at://did:plc:alice/app.activitytracker.feed.activity/$it") },
            batchId = null,
            isEdit = isEdit,
            state = state,
            title = title,
            sport = "ride",
            subSport = null,
            startedAt = Instant.parse("2026-05-31T07:00:00Z"),
            sent = sent,
            total = 5,
            finishedAt = null,
        )

    @Suppress("LongParameterList") // The import's fields a test varies, defaulted.
    private fun group(
        live: List<UploadItem> = emptyList(),
        finished: List<UploadItem> = emptyList(),
        save: ImportSaveStatus? = null,
        refused: Int = 0,
        skipped: Int = 0,
        source: UploadSource = UploadSource.Export(listOf("Strava")),
    ) =
        UploadImport(
            id = "batch1",
            source = source,
            savedAt = Instant.parse("2026-06-01T07:00:00Z"),
            saved = 1362,
            skipped = skipped,
            uploaded = 412,
            refused = refused,
            live = live,
            finished = finished,
            save = save,
        )

    private fun state(vararg imports: UploadImport, finished: List<UploadItem> = emptyList()) =
        UploadsState(
            content =
                ContentState(
                    UploadQueueView(
                        current = null,
                        own = emptyList(),
                        imports = imports.toList(),
                        finished = finished,
                        refusedOther = emptyList(),
                    ),
                    Origin.Cache,
                    LoadStatus.Idle,
                ),
        )

    @Test
    fun anItemReadsByItsTitleOrSportAndDay() {
        assertEquals(written("Evening Run"), UploadsTexts.itemName(item(1, UploadState.Waiting, title = "Evening Run"), utc))
        assertEquals(
            AppText.named(
                "unsent_change_activity_named",
                AppText.Named("sport_ride", fallback = written("Ride")),
                DateText.date(2026, 5, 31),
            ),
            UploadsTexts.itemName(item(1, UploadState.Waiting), utc),
        )
        assertEquals(
            AppText.named("uploads_updating", written("Lunch Swim")),
            UploadsTexts.itemName(item(1, UploadState.Waiting, title = "Lunch Swim", isEdit = true), utc),
        )
        assertEquals(
            AppText.named("unsent_change_like"),
            UploadsTexts.changeName(RejectedChange("3klike", RejectedChange.Kind.Like, now), utc),
        )
    }

    @Test
    fun anUploadingItemSaysHowFarItIs() =
        assertEquals(
            AppText.named("uploads_state_uploading", written("2"), written("5")),
            UploadsTexts.state(item(1, UploadState.Uploading, sent = 2)),
        )

    @Test
    fun anImportsLines() {
        val group = group(refused = 1, skipped = 3)
        assertEquals(AppText.named("uploads_source_export", written("Strava")), UploadImportTexts.source(group.source))
        assertEquals(
            AppText.named(
                "uploads_import_saved",
                AppText.named("page_start_value", AppText.named("start_today"), AppText.Time(group.savedAt.epochSeconds)),
            ),
            UploadImportTexts.saved(group, now, utc),
        )
        assertEquals(AppText.named("uploads_import_progress", written("412"), written("1,362")), UploadImportTexts.progress(group))
        assertEquals(
            AppText.named(
                "uploads_import_counts",
                AppText.Plural("uploads_import_refused", 1, persistentListOf(written("1"))),
                AppText.Plural("uploads_import_skipped", 3, persistentListOf(written("3"))),
            ),
            UploadImportTexts.counts(group),
        )
        assertNull(UploadImportTexts.counts(group()))
        assertNull(UploadImportTexts.saveStatus(group))
        assertEquals(
            AppText.named("uploads_import_stopped", written("120"), written("400")),
            UploadImportTexts.saveStatus(group(save = ImportSaveStatus.Stopped(120, 400))),
        )
    }

    @Test
    fun sourcesAreListed() {
        assertEquals(
            AppText.named(
                "uploads_source_exports",
                AppText.named(
                    "uploads_list_last",
                    AppText.named("uploads_list_pair", written("Strava"), written("Garmin")),
                    written("Wahoo"),
                ),
            ),
            UploadImportTexts.source(UploadSource.Export(listOf("Strava", "Garmin", "Wahoo"))),
        )
        assertEquals(written("ride.fit"), UploadImportTexts.source(UploadSource.Files("ride.fit", 1)))
        assertEquals(
            AppText.Plural("uploads_source_files", 11, persistentListOf(written("ride.fit"), written("11"))),
            UploadImportTexts.source(UploadSource.Files("ride.fit", 12)),
        )
    }

    @Test
    fun theServerPauseSaysTomorrowPastMidnight() {
        val today = UploadsState(serverPausedUntil = now + 3.hours + 40.minutes)
        assertEquals(
            AppText.named("uploads_server_paused", AppText.Time((now + 3.hours + 40.minutes).epochSeconds)),
            UploadsTexts.serverPaused(today, now, utc),
        )
        val tomorrow = UploadsState(serverPausedUntil = now + 13.hours)
        assertEquals("uploads_server_paused_tomorrow", (UploadsTexts.serverPaused(tomorrow, now, utc) as AppText.Named).name)
        assertNull(UploadsTexts.serverPaused(UploadsState(), now, utc))
    }

    @Test
    fun theTimeLeftIsRough() {
        assertEquals(
            AppText.named("uploads_time_left", AppText.Plural("duration_days", 1, persistentListOf(written("1")))),
            UploadsTexts.timeLeft(UploadsState(timeLeft = 26.hours)),
        )
        assertNull(UploadsTexts.timeLeft(UploadsState()))
    }

    @Test
    fun aLargeImportShowsItsFirstActivitiesUntilShowAll() {
        val group = group(live = (1L..26L).map { item(it, UploadState.Waiting) })
        val state = state(group)
        assertEquals(UploadsState.IMPORT_PREVIEW, state.shownLive(group).size)
        assertTrue(state.showsShowAll(group))
        val expanded = state.copy(expandedImports = persistentListOf("batch1"))
        assertEquals(26, expanded.shownLive(group).size)
        assertFalse(expanded.showsShowAll(group))
        assertTrue(state.showsPauseImports)
        assertFalse(state.copy(importsPaused = true).showsPauseImports)
    }

    @Test
    fun cancelRestIsOfferedWhileAnythingCanBeCancelled() {
        val state = UploadsState()
        assertTrue(state.offersCancelRest(group(live = listOf(item(1, UploadState.Waiting)))))
        assertTrue(state.offersCancelRest(group(save = ImportSaveStatus.Saving(500, 1362))))
        assertFalse(state.offersCancelRest(group(live = listOf(item(1, UploadState.Uploading, sent = 2)))))
    }

    @Test
    fun theCancelRestQuestionSaysWhenTheImportIsStillSaving() {
        val saving = state(group(save = ImportSaveStatus.Saving(500, 1362))).copy(confirmingCancelRest = "batch1")
        assertEquals(AppText.named("uploads_cancel_rest_message_saving"), UploadsTexts.cancelRestMessage(saving))
        assertEquals(
            AppText.named("uploads_cancel_rest_message"),
            UploadsTexts.cancelRestMessage(state(group()).copy(confirmingCancelRest = "batch1")),
        )
    }

    @Test
    fun clearNeedsSomethingFinished() {
        assertFalse(UploadsState().showsClear)
        assertFalse(state(group()).canClear)
        val done = state(finished = listOf(item(4, UploadState.Uploaded)))
        assertTrue(done.canClear)
        assertFalse(done.copy(clearing = true).canClear)
    }

    @Test
    fun anItemsActions() {
        val state = UploadsState(busy = persistentListOf("3k2"))
        assertTrue(state.opens(item(1, UploadState.Uploaded)))
        assertFalse(state.opens(item(1, UploadState.Waiting)))
        assertTrue(state.offersSendAgain(item(1, UploadState.Refused)))
        assertTrue(state.offersCancel(item(1, UploadState.Refused)))
        assertFalse(state.offersCancel(item(1, UploadState.Uploading)))
        assertFalse(state.offersCancel(item(1, UploadState.Skipped, rkey = null)))
        assertTrue(state.isBusy("3k2"))
        assertFalse(state.isBusy("3k1"))
    }
}
