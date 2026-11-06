package io.github.solcott.activitytracker.app.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

/** The leave screen (auth/leaving). */
@OptIn(ExperimentalTestApi::class)
class LeaveUiTest {
    private val leaveButton = hasText("Leave Activity Tracker") and hasClickAction()

    @Test
    fun listsWhatsDeletedAndWhatStays() = runComposeUiTest {
        setContent { AppPreview { LeaveUi(LeaveSamples.default) } }
        onNodeWithText("Every activity, of every visibility", substring = true).assertExists()
        onNodeWithText("Your profile and its private details", substring = true).assertExists()
        onNodeWithText("Your gear", substring = true).assertExists()
        onNodeWithText("Your follows", substring = true).assertExists()
        onNodeWithText("comments", substring = true).performScrollTo().assertExists()
        onNodeWithText("Your atproto account and its handle, alice.test", substring = true).performScrollTo().assertExists()
        onNodeWithText("Bluesky posts", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun saysWhichSegmentsAndSitesStay() = runComposeUiTest {
        setContent { AppPreview { LeaveUi(LeaveSamples.default) } }
        onNodeWithText("3 segments others use stay, without your name", substring = true).performScrollTo().assertExists()
        onNodeWithText("1 dive site others use stays, without your name", substring = true).performScrollTo().assertExists()
        onNodeWithText("Your other 2 segments are deleted.").performScrollTo().assertExists()
    }

    @Test
    fun countsThatCouldntLoadSaySo() = runComposeUiTest {
        setContent { AppPreview { LeaveUi(LeaveSamples.all.getValue("placesFailed")) } }
        onNodeWithText("Couldn't count your segments and dive sites. Couldn't reach the server.").performScrollTo().assertExists()
    }

    @Test
    fun saysWhatThisDeviceLoses() = runComposeUiTest {
        setContent { AppPreview { LeaveUi(LeaveSamples.all.getValue("pendingWork")) } }
        onNodeWithText("2 changes that haven't uploaded are discarded and never uploaded.", substring = true)
            .performScrollTo()
            .assertExists()
        onNodeWithText("The import still saving stops", substring = true).performScrollTo().assertExists()
        onNodeWithText("The recording in progress is discarded.", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun leavingStaysOffUntilTheHandleMatches() = runComposeUiTest {
        setContent { AppPreview { LeaveUi(LeaveSamples.all.getValue("wrongHandle")) } }
        onNode(leaveButton).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aMatchingHandleConfirms() = runComposeUiTest {
        val events = mutableListOf<LeaveEvent>()
        setContent { AppPreview { LeaveUi(LeaveSamples.all.getValue("confirmed").copy(eventSink = { events += it })) } }
        onNode(leaveButton).performScrollTo().assertIsEnabled().performClick()
        assertEquals(LeaveEvent.Leave, events.last())
    }

    @Test
    fun aRunningExportHoldsLeavingBack() = runComposeUiTest {
        val events = mutableListOf<LeaveEvent>()
        setContent { AppPreview { LeaveUi(LeaveSamples.all.getValue("exportRunning").copy(eventSink = { events += it })) } }
        onNodeWithText("Wait for it to finish, or cancel it", substring = true).performScrollTo().assertExists()
        onNode(leaveButton).performScrollTo().assertIsNotEnabled()
        onNodeWithText("Cancel export").performScrollTo().performClick()
        assertEquals(LeaveEvent.CancelExport, events.last())
    }

    @Test
    fun anUnsavedExportIsOfferedFirst() = runComposeUiTest {
        val events = mutableListOf<LeaveEvent>()
        setContent { AppPreview { LeaveUi(LeaveSamples.all.getValue("exportReady").copy(eventSink = { events += it })) } }
        onNodeWithText("Leaving deletes it", substring = true).performScrollTo().assertExists()
        onNodeWithText("Save it on the Account page").performScrollTo().performClick()
        assertEquals(LeaveEvent.SaveExport, events.last())
    }

    @Test
    fun anOldGrantPointsToSignInAgain() = runComposeUiTest {
        val events = mutableListOf<LeaveEvent>()
        setContent { AppPreview { LeaveUi(LeaveSamples.all.getValue("signInAgain").copy(eventSink = { events += it })) } }
        onNode(leaveButton).assertDoesNotExist()
        onNodeWithText("Sign in again").performScrollTo().performClick()
        assertEquals(LeaveEvent.SignInAgain, events.last())
    }

    @Test
    fun anUnreachablePdsSaysSo() = runComposeUiTest {
        setContent { AppPreview { LeaveUi(LeaveSamples.all.getValue("unreachable")) } }
        onNodeWithText("Couldn't reach the server", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun cancellingGoesBack() = runComposeUiTest {
        val events = mutableListOf<LeaveEvent>()
        setContent { AppPreview { LeaveUi(LeaveSamples.default.copy(eventSink = { events += it })) } }
        onNodeWithTag(TestTags.BACK, useUnmergedTree = true).performClick()
        assertEquals(LeaveEvent.Back, events.last())
    }

    @Test
    fun progressShowsTheStepAndRecordsDeleted() = runComposeUiTest {
        setContent { AppPreview { LeaveUi(LeaveSamples.all.getValue("deletingRecords")) } }
        onNodeWithText("Deleting your public records…").assertExists()
        onNodeWithText("1,200 records deleted").assertExists()
        onNodeWithTag(TestTags.BACK, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aRateLimitShowsTheLeaveStillWorking() = runComposeUiTest {
        setContent { AppPreview { LeaveUi(LeaveSamples.all.getValue("rateLimited")) } }
        onNodeWithText("Deleting your public records…").assertExists()
        onNodeWithText("Your server asked to slow down", substring = true).assertExists()
    }

    @Test
    fun aStoppedLeaveOffersRetry() = runComposeUiTest {
        val events = mutableListOf<LeaveEvent>()
        setContent { AppPreview { LeaveUi(LeaveSamples.all.getValue("stopped").copy(eventSink = { events += it })) } }
        onNodeWithText("Leaving stopped").assertExists()
        onNodeWithText("Retry").performClick()
        assertEquals(LeaveEvent.Retry, events.last())
    }
}
