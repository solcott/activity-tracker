package io.github.solcott.activitytracker.app.feed

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

/** The changes the server refused (data/outbox), and the feed's row that leads to them. */
@OptIn(ExperimentalTestApi::class)
class UnsentChangesUiTest {
    @Test
    fun eachChangeIsNamedAndSaysTheServerRefusedIt() = runComposeUiTest {
        val events = mutableListOf<UnsentChangesEvent>()
        setContent { AppPreview { UnsentChangesUi(UnsentChangesSamples.loaded.copy(eventSink = { events += it })) } }
        onNodeWithText("Morning Ride").assertExists()
        onNodeWithText("Run, 2026-05-31").assertExists()
        onNodeWithText("A like").assertExists()
        onAllNodesWithText("The server refused this change.").assertCountEquals(3)
        onNodeWithText("InvalidRecord", substring = true).assertDoesNotExist()

        onNodeWithContentDescription("Retry Morning Ride").performClick()
        onNodeWithContentDescription("Discard A like").performClick()
        assertEquals(listOf(UnsentChangesEvent.Retry("3kride"), UnsentChangesEvent.Discard("3klike")), events)
    }

    @Test
    fun aChangeBeingSentCantBeTappedAgain() = runComposeUiTest {
        setContent { AppPreview { UnsentChangesUi(UnsentChangesSamples.retrying) } }
        onNodeWithContentDescription("Retry Morning Ride").assertIsNotEnabled()
        onNodeWithContentDescription("Discard Morning Ride").assertIsNotEnabled()
    }

    @Test
    fun discardingAsksFirst() = runComposeUiTest {
        val events = mutableListOf<UnsentChangesEvent>()
        setContent { AppPreview { UnsentChangesUi(UnsentChangesSamples.confirmingDiscard.copy(eventSink = { events += it })) } }
        onNodeWithText("Discard this change?").assertExists()
        onNode(hasText("Discard") and hasAnyAncestor(isDialog())).performClick()
        // The dialog dismisses before it acts, so the confirm names the change.
        assertEquals(UnsentChangesEvent.ConfirmDiscard("3kride"), events.last())
    }

    @Test
    fun aFailedRetrySaysWhy() = runComposeUiTest {
        setContent { AppPreview { UnsentChangesUi(UnsentChangesSamples.retryFailed) } }
        onNodeWithText("Couldn't send the change again", substring = true).assertExists()
    }

    @Test
    fun nothingWaitingSaysSo() = runComposeUiTest {
        setContent { AppPreview { UnsentChangesUi(UnsentChangesSamples.empty) } }
        onNodeWithText("Nothing is waiting. Every change was sent.").assertExists()
    }

    @Test
    fun theFeedCountsRefusedChangesAndOpensThem() = runComposeUiTest {
        val events = mutableListOf<FeedEvent>()
        setContent { AppPreview { FeedUi(FeedSamples.unsent.copy(eventSink = { events += it })) } }
        onNodeWithText("Uploading 1 change…").assertExists()
        onNodeWithText("2 changes couldn't be published").performClick()
        assertEquals(listOf<FeedEvent>(FeedEvent.UnsentChanges), events)
    }
}
