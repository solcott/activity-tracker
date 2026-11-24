package io.github.solcott.activitytracker.app.segment

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.CLOSED_MAP_LABEL_TAG
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.lexicon.app.TrailCondition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/**
 * Trail reports in the shared UI (segments/trail-reports): the Segment page's Conditions card in each of its states, the report sheet and
 * its share question, the reports list, and the Trail page's card, row line and map label.
 */
@OptIn(ExperimentalTestApi::class)
class TrailReportsUiTest {
    private fun SemanticsNodeInteractionsProvider.scrollToCard() =
        onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(CONDITIONS_CARD_TAG))

    @Test
    fun muddyLeadsWithItsRidersTagsNoteAndPhotos() = runComposeUiTest {
        val events = mutableListOf<ReportEvent>()
        val state = TrailReportSamples.conditions.let { it.copy(reports = it.reports.copy(eventSink = { e -> events += e })) }
        setContent { AppPreview { SegmentUi(state) } }
        scrollToCard()
        onNodeWithText("Conditions").assertExists()
        onNodeWithText("Muddy").assertExists()
        onNodeWithText("3 riders · latest 2 days ago").assertExists()
        onNodeWithText("Downed tree · 2").assertExists()
        onNodeWithText("Big tree down", substring = true).assertExists()
        onNodeWithText("+12").assertExists()
        onNodeWithText("See all 16").performClick()
        assertEquals(ReportEvent.OpenPhoto(0), events.last())
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Report conditions"))
        onNodeWithText("Report conditions").performClick()
        assertEquals(ReportEvent.Open, events.last())
        onNodeWithText("Add photos").performClick()
        assertEquals(ReportEvent.OpenAddPhotos, events.last())
    }

    @Test
    fun theCardSitsAfterTheProfileAndBeforeTheBoards() = runComposeUiTest {
        setContent { AppPreview { SegmentUi(TrailReportSamples.conditions) } }
        val list = onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasTestTag(CONDITIONS_CARD_TAG))
        val card = onNodeWithTag(CONDITIONS_CARD_TAG).fetchSemanticsNode().boundsInRoot.top
        list.performScrollToNode(hasText("Leaderboard"))
        assertEquals(true, onNodeWithText("Leaderboard").fetchSemanticsNode().boundsInRoot.top > card, "Conditions before the boards")
    }

    @Test
    fun closedLeadsInWordsAndOffersNoReportToANonRider() = runComposeUiTest {
        setContent { AppPreview { SegmentUi(TrailReportSamples.closed) } }
        scrollToCard()
        onNodeWithText("Closed").assertExists()
        onNodeWithText("1 rider · latest yesterday").assertExists()
        onNodeWithText("Gate locked", substring = true).assertExists()
        onNodeWithText("Report conditions").assertDoesNotExist()
    }

    @Test
    fun nothingCurrentAsksARecentRider() = runComposeUiTest {
        setContent { AppPreview { SegmentUi(TrailReportSamples.ask) } }
        scrollToCard()
        onNodeWithText("No reports in the last 14 days").assertExists()
        onNodeWithText("Last report: Dry, 5 weeks ago").assertExists()
        onNodeWithText("You rode it 3 days ago. How was it?").assertExists()
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Report conditions"))
        onNodeWithText("Report conditions").assertExists()
    }

    @Test
    fun aSegmentWithNoReportsHasNoCard() = runComposeUiTest {
        setContent { AppPreview { SegmentUi(SegmentSamples.loaded) } }
        onNodeWithTag(CONDITIONS_CARD_TAG).assertDoesNotExist()
        onNodeWithText("Conditions").assertDoesNotExist()
    }

    @Test
    fun theSheetTogglesTagsAndPosts() = runComposeUiTest {
        val events = mutableListOf<ReportEvent>()
        val state = TrailReportSamples.sheet.let { it.copy(reports = it.reports.copy(eventSink = { e -> events += e })) }
        setContent { AppPreview { SegmentUi(state) } }
        onNodeWithText("How's the trail?").assertExists()
        onNodeWithText("· you rode it 3 days ago", substring = true).assertExists()
        onNodeWithText("Surface").assertExists()
        onNodeWithText("On the trail").assertExists()
        onNode(hasText("Wet") and hasClickAction()).performClick()
        assertEquals(ReportEvent.Toggle(TrailCondition.WET), events.last())
        onNodeWithText("Posted publicly", substring = true).assertExists()
        onNode(hasText("Post report") and hasClickAction()).assertIsEnabled().performClick()
        assertEquals(ReportEvent.Post, events.last())
    }

    @Test
    fun postWaitsForATagOrAPhoto() = runComposeUiTest {
        val base = TrailReportSamples.sheet
        val sheet = checkNotNull(base.reports.sheet)
        val empty =
            sheet.copy(
                surface = TrailReportTexts.toggles(TrailCondition.surface, emptySet()),
                onTrail = TrailReportTexts.toggles(TrailCondition.onTrail, emptySet()),
                photos = persistentListOf(),
            )
        setContent { AppPreview { SegmentUi(base.copy(reports = base.reports.copy(sheet = empty))) } }
        onNode(hasText("Post report") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun sharingAPhotoFromANonPublicRideAsksFirst() = runComposeUiTest {
        val events = mutableListOf<ReportEvent>()
        val state = TrailReportSamples.confirmShare.let { it.copy(reports = it.reports.copy(eventSink = { e -> events += e })) }
        setContent { AppPreview { SegmentUi(state) } }
        onNodeWithText("Share a photo from a non-public ride?").assertExists()
        onNodeWithText("Share and post").performClick()
        assertEquals(ReportEvent.ConfirmShare(true), events.last())
    }

    @Test
    fun addPhotosListsRidesAndAddsTheTickedOnes() = runComposeUiTest {
        val events = mutableListOf<ReportEvent>()
        val state = TrailReportSamples.addPhotos.let { it.copy(reports = it.reports.copy(eventSink = { e -> events += e })) }
        setContent { AppPreview { SegmentUi(state) } }
        onNodeWithText("Choose from library").assertExists()
        onNodeWithText("From your rides here").assertExists()
        onNodeWithText("Evening loop").assertExists()
        onNodeWithText("Add 2").performClick()
        assertEquals(ReportEvent.AddTicked, events.last())
    }

    @Test
    fun theReportsListHasItsSectionsAndMenus() = runComposeUiTest {
        val events = mutableListOf<TrailReportsEvent>()
        setContent { AppPreview { TrailReportsUi(TrailReportSamples.reports.copy(eventSink = { events += it })) } }
        onNodeWithText("Last 14 days").assertExists()
        onNodeWithText("Maya Chen").assertExists()
        onNodeWithText("Roots slick", substring = true).assertExists()
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Older"))
        onNodeWithText("Older").assertExists()
    }

    @Test
    fun theTrailPageLeadsWithItsConditions() = runComposeUiTest {
        setContent { AppPreview { PathUi(PathSamples.conditions) } }
        onNodeWithTag(PATH_CONDITIONS_TAG).assertExists()
        onNodeWithText("Reports on 3 of 8 segments, last 14 days").assertExists()
        onNodeWithText("Wiggly Woods").assertExists()
        onNodeWithText("Photos from all segments").assertExists()
        onAllNodesWithTag(CLOSED_MAP_LABEL_TAG, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun aClosedSegmentsRowSaysSo() = runComposeUiTest {
        setContent { AppPreview { PathUi(PathSamples.conditions) } }
        onNodeWithText("Closed · yesterday").assertExists()
    }
}
