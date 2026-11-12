package io.github.solcott.activitytracker.app.feed

import io.github.solcott.activitytracker.app.feedfilter.DateRange
import io.github.solcott.activitytracker.app.feedfilter.FeedFilter
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterScreen
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.activity.UploadSummary
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.CardRoute
import io.github.solcott.activitytracker.lexicon.app.ImportCardView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SportCount
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import io.github.solcott.dataresult.Origin
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.LoadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone

/** What the feed says and offers (feed/feed-filter, data/outbox, feed/import-cards, dev/shared-display-text). */
class FeedTextsTest {
    private fun state(
        content: ContentState<ImmutableList<TimelineItem>> = ContentState(persistentListOf(), Origin.Network, LoadStatus.Idle),
        canRecord: Boolean = true,
        filter: FeedFilter = FeedFilter.Default,
        scope: FeedScope = FeedScope.Following,
    ) =
        FeedState(
            content = content,
            error = null,
            viewerDid = "did:plc:alice",
            canRecord = canRecord,
            scope = scope,
            scopes = persistentListOf(FeedScope.Following, FeedScope.Mine),
            filter = filter,
            filterScreen = FeedFilterScreen(filter),
            eventSink = {},
        )

    private fun plural(name: String, n: Int) = AppText.Plural(name, n, persistentListOf(AppText.written(formatWhole(n))))

    @Test
    fun theTitleIsTheChosenScopeAndTheMenuMarksIt() {
        val mine = state(scope = FeedScope.Mine)
        assertEquals(AppText.named("feed_filter_mine"), FeedTexts.title(mine))
        assertEquals(listOf(false, true), FeedTexts.scopes(mine).map { it.chosen })
        assertEquals(listOf(FeedScope.Following, FeedScope.Mine), FeedTexts.scopes(mine).map { it.scope })
    }

    @Test
    fun recordIsInTheMenuOnlyWhereAskedAndPossible() {
        assertEquals(
            listOf(FeedAddKind.Record, FeedAddKind.Manual, FeedAddKind.Import),
            FeedTexts.addChoices(state(), recordInMenu = true).map { it.kind },
        )
        assertEquals(listOf(FeedAddKind.Manual, FeedAddKind.Import), FeedTexts.addChoices(state(), recordInMenu = false).map { it.kind })
        assertEquals(
            listOf(FeedAddKind.Manual, FeedAddKind.Import),
            FeedTexts.addChoices(state(canRecord = false), recordInMenu = true).map { it.kind },
        )
        assertEquals(FeedEvent.Import, FeedAddChoice(FeedAddKind.Import, AppText.written("")).event)
    }

    @Test
    fun refusedChangesAreCounted() {
        assertNull(FeedUploadTexts.refused(UploadSummary()))
        assertEquals(plural("feed_rejected_changes", 2), FeedUploadTexts.refused(UploadSummary(refused = 2)))
    }

    @Test
    fun uploadingNamesOwnImportedAndOtherChanges() {
        assertNull(FeedUploadTexts.uploading(UploadSummary(refused = 1)))
        val both = UploadSummary(own = 1, imported = 950, other = 3)
        assertEquals(
            AppText.named(
                "feed_uploading_with_other",
                AppText.named("feed_uploading_and_imported", plural("feed_uploading_activities", 1), AppText.written(formatWhole(950))),
                plural("feed_other_changes", 3),
            ),
            FeedUploadTexts.uploading(both),
        )
        assertEquals(
            AppText.named("feed_imports_waiting", plural("feed_uploading_imported", 5)),
            FeedUploadTexts.uploading(UploadSummary(imported = 5, importsPaused = true)),
        )
        assertEquals(plural("feed_pending_writes", 2), FeedUploadTexts.uploading(UploadSummary(other = 2)))
    }

    @Test
    fun pausesShowOnlyWhileUploading() {
        assertEquals(AppText.named("feed_imports_paused"), FeedUploadTexts.importsPaused(UploadSummary(imported = 5, importsPaused = true)))
        assertNull(FeedUploadTexts.importsPaused(UploadSummary(own = 1, importsPaused = true)))
        val now = Instant.parse("2026-11-11T12:00:00Z")
        val until = Instant.parse("2026-11-11T15:40:00Z")
        assertEquals(
            AppText.named("feed_upload_paused", AppText.Time(until.epochSeconds)),
            FeedUploadTexts.pausedUntil(UploadSummary(imported = 3, serverPausedUntil = until), now, TimeZone.UTC),
        )
        assertNull(FeedUploadTexts.pausedUntil(UploadSummary(serverPausedUntil = until), now, TimeZone.UTC))
    }

    @Test
    fun theEmptyFeedSaysWhyAndOffersClearOnlyWhenFiltered() {
        assertNull(FeedTexts.empty(state(content = ContentState(persistentListOf()))))
        assertEquals(AppText.named("feed_empty_can_record"), FeedTexts.empty(state()))
        assertEquals(AppText.named("feed_empty_manual_only"), FeedTexts.empty(state(canRecord = false)))
        assertFalse(FeedTexts.emptyOffersClear(state()))
        val filtered = state(filter = FeedFilter(range = DateRange.ThisYear))
        assertEquals(AppText.named("feed_empty_filtered"), FeedTexts.empty(filtered))
        assertTrue(FeedTexts.emptyOffersClear(filtered))
    }

    private val card =
        ImportCardView(
            author = ProfileViewBasic(Did("did:plc:bob"), Handle("bob.test")),
            batch = "b1",
            count = 1312,
            earliestStartedAt = Instant.parse("2019-11-05T12:00:00Z"),
            // Still October in Denver.
            latestStartedAt = Instant.parse("2026-11-01T03:00:00Z"),
            sports = listOf(SportCount("ride", 1201), SportCount("run", 111)),
            distance = 14_380_000,
            routes = List(6) { CardRoute("abc") },
            postedAt = Instant.parse("2026-11-01T03:00:00Z"),
        )

    @Test
    fun anImportCardReadsItsCountMonthsSportsAndDistance() {
        assertEquals(plural("import_card_title", 1312), ImportCardTexts.title(card))
        assertEquals(
            AppText.named(
                "import_card_range",
                AppText.named("import_batch_month", AppText.named("month_short_11"), AppText.written("2019")),
                AppText.named("import_batch_month", AppText.named("month_short_10"), AppText.written("2026")),
            ),
            ImportCardTexts.range(card, TimeZone.of("America/Denver")),
        )
        val summary = ImportCardTexts.summary(card, UnitSystem.Metric) as AppText.Named
        assertEquals("text_dot_pair", summary.name)
        assertEquals(StatText.distance(14_380_000.0, UnitSystem.Metric), summary.args[1])
        assertEquals(ImportCardView.MAX_ROUTES, ImportCardTexts.routes(card).size)
    }

    @Test
    fun anImportCardIsKeyedByItsAuthorAndBatch() {
        assertEquals("importCard:did:plc:bob/b1", state().keyOf(TimelineItem.ImportCard(card)))
    }
}
