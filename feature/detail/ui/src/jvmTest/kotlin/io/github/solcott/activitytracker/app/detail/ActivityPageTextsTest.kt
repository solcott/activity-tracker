package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allPluralStringResources
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.activity.CombineCue
import io.github.solcott.activitytracker.data.activity.ExportFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone

/** The activity page's words and the owner's menu, which both UIs ask (dev/shared-display-text). */
class ActivityPageTextsTest {
    private val manage = ActivityManage(otherRecordings = 0, canUngroup = true, members = 1, canCombine = true, busy = false, error = null)

    @Test
    fun theOwnersMenuListsWhatTheActivityAllows() {
        val page =
            ActivityDetailSamples.page.copy(
                isOwn = true,
                manage = manage,
                canHideRouteEnds = true,
                export = ActivityExportState(persistentListOf(ExportFormat.GPX)),
            )
        assertEquals(
            listOf(
                ActivityMenuAction.Edit,
                ActivityMenuAction.Combine,
                ActivityMenuAction.AddRecording,
                ActivityMenuAction.Split,
                ActivityMenuAction.Ungroup,
                ActivityMenuAction.HideStart,
                ActivityMenuAction.HideEnd,
                ActivityMenuAction.Export,
                ActivityMenuAction.Delete,
            ),
            ActivityPageTexts.ownerMenu(page).map { it.action },
        )
        // Split, Export and Delete are asked about first.
        assertTrue(ActivityPageTexts.ownerMenu(page).filter { it.event() == null }.all { it.action in ASKED })
        assertTrue(ActivityPageTexts.ownerMenu(ActivityDetailSamples.page).isEmpty())
    }

    @Test
    fun deletingAGroupedActivityAsksWhetherTheOthersGo() {
        val choices = ActivityPageTexts.deleteChoices(manage)
        assertEquals(listOf(ActivityDetailEvent.Delete(true), ActivityDetailEvent.Delete(false)), choices.map { it.event() })
        assertEquals(
            listOf(ActivityDetailEvent.Delete(false)),
            ActivityPageTexts.deleteChoices(manage.copy(members = 0)).map { it.event() },
        )
    }

    @Test
    fun reportIsOfferedOnSomeoneElsesActivity() {
        val page = ActivityDetailSamples.page
        assertEquals("Bob", ActivityPageTexts.reportEvent(page.copy(reporting = page.reporting.copy(available = true)), "Bob")?.authorName)
        assertNull(ActivityPageTexts.reportEvent(page.copy(isOwn = true), "Bob"))
    }

    @Test
    fun everyNameTheTextsChooseHasItsString() {
        val page = ActivityDetailSamples.page
        val export = ActivityExportState(persistentListOf(ExportFormat.GPX), original = OriginalFile("ride.fit", "fit"))
        val texts =
            listOf(
                ActivityPageTexts.title,
                ActivityPageTexts.more,
                ActivityPageTexts.likeButton(page.activity),
                ActivityPageTexts.likesToggle(true),
                ActivityPageTexts.likesToggle(false),
                ActivityPageTexts.togetherWith,
                ActivityPageTexts.groupLeft,
                ActivityPageTexts.groupButton(OwnGroupState(left = true)),
                ActivityPageTexts.groupButton(OwnGroupState(left = false)),
                ActivityPageTexts.leaveCopyGroup,
                ActivityPageTexts.addPeople,
                ActivityPageTexts.hiddenTitle,
                ActivityPageTexts.hiddenMessage,
                ActivityPageTexts.hiddenShow,
                ActivityPageTexts.report,
                ActivityPageTexts.cancel,
                ActivityPageTexts.deleteTitle,
                ActivityPageTexts.deleteMessage(manage.copy(members = 0)),
                ActivityPageTexts.splitTitle,
                ActivityPageTexts.splitMessage,
                ActivityPageTexts.split,
                ActivityPageTexts.exportTitle,
                ActivityPageTexts.exportAction(export),
                ActivityPageTexts.exportAction(export.copy(formats = persistentListOf())),
                ActivityPageTexts.exportOriginal,
                ActivityPageTexts.exportOriginalExplained,
                ActivityPageTexts.exportDownload,
                ActivityPageTexts.exportRebuilt,
                ActivityPageTexts.exportPreparing,
                ActivityPageTexts.exportNote(export),
                ActivityPageTexts.exportNote(export.copy(hasLaps = true)),
            ) +
                ActivityPageTexts.notices(page.copy(zoneAdded = true, segmentCreated = true, diveSiteCreated = true)) +
                ActivityPageTexts.ownerMenu(page.copy(manage = manage, canCreateSegment = true, canCreateDiveSite = true)).map {
                    it.label
                } +
                ActivityPageTexts.deleteChoices(manage).map { it.label } +
                ExportFormat.entries.map(ActivityPageTexts::formatName)
        assertAllExist(texts)
    }

    @Test
    fun everyNameTheRecordingAndCombineTextsChooseHasItsString() {
        val page = ActivityDetailSamples.page
        val texts =
            listOf(
                ActivityPageTexts.combine,
                ActivityPageTexts.notNow,
                ActivityPageTexts.dontCombine,
                ActivityPageTexts.combineMessage(
                    CombineSuggestionState(CombineCue.Break, page.activity, asPrompt = false),
                    NOW,
                    TimeZone.UTC,
                ),
                RecordingTexts.recordingOf,
                RecordingTexts.openRecorded,
                RecordingTexts.recordings,
                RecordingTexts.memberOf,
                RecordingTexts.openCombined,
                RecordingTexts.members,
            ) + CombineCue.entries.map(ActivityPageTexts::combineCue)
        assertAllExist(texts)
    }

    private fun assertAllExist(texts: List<AppText>) {
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources || it in Res.allPluralStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            is AppText.Plural -> listOf(text.name) + text.args.flatMap(::names)
            else -> emptyList()
        }

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-01T18:00:00Z")
        val ASKED = setOf(ActivityMenuAction.Split, ActivityMenuAction.Export, ActivityMenuAction.Delete)
    }
}
