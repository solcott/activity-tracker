package io.github.solcott.activitytracker.app.segment

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Segment page's layout (segments/segment-page): the heading with its trail, the figure row, the boards row with the Leaderboard and
 * Mayor cards, and Trail details in cards, in that order.
 */
@OptIn(ExperimentalTestApi::class)
class SegmentPageUiTest {
    @Test
    fun thePartsShowInOrder() = runComposeUiTest {
        setContent { AppPreview { SegmentUi(SegmentSamples.openStreetMap) } }
        val list = onNode(hasScrollToNodeAction())
        val heading = onNodeWithText("Ride · Active").fetchSemanticsNode().boundsInRoot.top
        list.performScrollToNode(hasText("Mayor · last 90 days"))
        val leaderboard = onNodeWithText("Leaderboard").fetchSemanticsNode().boundsInRoot
        val mayor = onNodeWithText("Mayor · last 90 days").fetchSemanticsNode().boundsInRoot
        assertEquals(leaderboard.top, mayor.top, "the two cards side by side")
        assertTrue(leaderboard.left < mayor.left, "Leaderboard first")
        assertTrue(heading < leaderboard.top, "the heading before the boards")
        list.performScrollToNode(hasText("Surface & width"))
        val details = onNodeWithText("Surface & width").fetchSemanticsNode().boundsInRoot.top
        list.performScrollToNode(hasText("Your efforts"))
        assertTrue(onNodeWithText("Your efforts").fetchSemanticsNode().boundsInRoot.top >= details, "Trail details before the efforts")
    }

    @Test
    fun theLeaderboardCardShowsItsLeaderAndTheViewersPlace() = runComposeUiTest {
        val events = mutableListOf<SegmentEvent>()
        setContent { AppPreview { SegmentUi(SegmentSamples.openStreetMap.copy(eventSink = { events += it })) } }
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("4:12"))
        onNodeWithText("You: 5:12 · #3").assertExists()
        onNode(hasText("Leaderboard") and hasClickAction(), useUnmergedTree = false).performClick()
        assertEquals(SegmentEvent.OpenLeaderboard, events.last())
    }

    @Test
    fun aFailedBoardReadLeavesTheLeaderboardCardAlone() = runComposeUiTest {
        val events = mutableListOf<SegmentEvent>()
        setContent { AppPreview { SegmentUi(SegmentSamples.boardsFailed.copy(eventSink = { events += it })) } }
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Leaderboard"))
        onNodeWithText("Leaderboard").performClick()
        assertEquals(SegmentEvent.OpenLeaderboard, events.last())
        onNodeWithText("Mayor · last 90 days").assertDoesNotExist()
        onNodeWithText("No one is on this leaderboard yet").assertDoesNotExist()
    }

    @Test
    fun aLiftHasNoBoardsRow() = runComposeUiTest {
        setContent { AppPreview { SegmentUi(SegmentSamples.lift) } }
        onNodeWithText("Leaderboard").assertDoesNotExist()
        onNodeWithText("Mayor · last 90 days").assertDoesNotExist()
    }

    @Test
    fun theTrailLinkOpensItsPage() = runComposeUiTest {
        val events = mutableListOf<SegmentEvent>()
        setContent { AppPreview { SegmentUi(SegmentSamples.openStreetMap.copy(eventSink = { events += it })) } }
        onNodeWithText("Notch Road").performClick()
        assertEquals(SegmentEvent.OpenTrail(SegmentSamples.openStreetMap.trails.single().uri), events.last())
    }

    @Test
    fun aSegmentOnNoTrailHasNoOnLine() = runComposeUiTest {
        setContent { AppPreview { SegmentUi(SegmentSamples.loaded) } }
        onNodeWithText("on").assertDoesNotExist()
    }
}
