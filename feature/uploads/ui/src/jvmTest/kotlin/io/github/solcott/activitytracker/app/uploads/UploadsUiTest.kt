package io.github.solcott.activitytracker.app.uploads

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import io.github.solcott.activitytracker.lexicon.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.TimeZone

/** The upload queue (data/upload-queue), rendered from its samples. */
@OptIn(ExperimentalTestApi::class)
class UploadsUiTest {
    @Test
    fun theItemUploadingNowShowsItsProgress() = runComposeUiTest {
        setContent { AppPreview { UploadsUi(UploadsSamples.ownOnly) } }
        onNodeWithText("Uploading now").assertExists()
        onNodeWithText("Evening Run").assertExists()
        onNodeWithText("Uploading, 2 of 5").assertExists()
        // Uploading items can't be cancelled; waiting ones can.
        onNodeWithContentDescription("Cancel Evening Run").assertDoesNotExist()
        onNodeWithContentDescription("Cancel Morning Ride").assertIsEnabled()
        onNodeWithText("Updating Lunch Swim").assertExists()
    }

    @Test
    fun anImportShowsItsCountsAndTimeLeft() = runComposeUiTest {
        setContent { AppPreview { UploadsUi(UploadsSamples.importPartWay) } }
        onNodeWithText("Strava export").assertExists()
        onNodeWithText("412 of 1,362 uploaded").assertExists()
        onNodeWithText("1 refused · 3 skipped").assertExists()
        onNodeWithText("About 1 day left").assertExists()
        onNodeWithText("Uploads continue only while the app is open.").assertExists()
        onNodeWithText("Pause imports").assertExists()
    }

    @Test
    fun aLargeImportShowsTheFirstActivitiesThenShowAll() = runComposeUiTest {
        val events = mutableListOf<UploadsEvent>()
        setContent { AppPreview { UploadsUi(UploadsSamples.importPartWay.copy(eventSink = { events += it })) } }
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Show all (26)"))
        onNodeWithText("Show all (26)").performClick()
        assertEquals(UploadsEvent.ShowAll(UploadsSamples.IMPORT_ID), events.last())
    }

    @Test
    fun anExpandedImportHasNoShowAll() = runComposeUiTest {
        setContent { AppPreview { UploadsUi(UploadsSamples.importExpanded) } }
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Gravel Loop"))
        onNodeWithText("Show all", substring = true).assertDoesNotExist()
    }

    @Test
    fun bothPausesAreSaid() = runComposeUiTest {
        val events = mutableListOf<UploadsEvent>()
        setContent {
            AppPreview(now = Fixed.NOW, timeZone = TimeZone.UTC, use24Hour = false) {
                UploadsUi(UploadsSamples.paused.copy(eventSink = { events += it }))
            }
        }
        onNodeWithText("Imports paused by you").assertExists()
        onNodeWithText("Uploads paused by the server until 3:40 PM").assertExists()
        onNodeWithText("Strava and Garmin exports").assertExists()
        onNodeWithText("Resume").performClick()
        assertEquals(listOf<UploadsEvent>(UploadsEvent.ResumeImports), events)
    }

    @Test
    fun refusedItemsCanBeSentAgainOrCancelled() = runComposeUiTest {
        val events = mutableListOf<UploadsEvent>()
        setContent { AppPreview { UploadsUi(UploadsSamples.refused.copy(eventSink = { events += it })) } }
        onNodeWithText("Run, 2026-05-31").assertExists()
        onNodeWithText("A like").assertExists()
        onNodeWithContentDescription("Send Run, 2026-05-31 again").performClick()
        onNodeWithContentDescription("Cancel A like").performClick()
        assertEquals(listOf(UploadsEvent.SendAgain("3k5"), UploadsEvent.Cancel("3klike")), events)
    }

    @Test
    fun aChangeBeingSentCantBeTappedAgain() = runComposeUiTest {
        setContent { AppPreview { UploadsUi(UploadsSamples.sendingAgain) } }
        onNodeWithContentDescription("Send A like again").assertIsNotEnabled()
        onNodeWithContentDescription("Cancel A like").assertIsNotEnabled()
    }

    @Test
    fun finishedItemsOpenTheirActivityAndCanBeCleared() = runComposeUiTest {
        val events = mutableListOf<UploadsEvent>()
        setContent { AppPreview { UploadsUi(UploadsSamples.finished.copy(eventSink = { events += it })) } }
        onNodeWithText("Morning_Ride.fit and 14 more").assertExists()
        onNodeWithText("Hill Repeats").performClick()
        onNodeWithContentDescription("Clear finished uploads").performClick()
        assertEquals(
            listOf(UploadsEvent.OpenActivity(Fixed.uri(Fixed.ALICE, Nsid.FEED_ACTIVITY, "3k4").value), UploadsEvent.ClearFinished),
            events,
        )
    }

    @Test
    fun clearIsOffWithNothingFinished() = runComposeUiTest {
        setContent { AppPreview { UploadsUi(UploadsSamples.ownOnly) } }
        onNodeWithContentDescription("Clear finished uploads").assertIsNotEnabled()
    }

    @Test
    fun cancellingAsksFirst() = runComposeUiTest {
        val events = mutableListOf<UploadsEvent>()
        setContent { AppPreview { UploadsUi(UploadsSamples.confirmingCancel.copy(eventSink = { events += it })) } }
        onNodeWithText("Cancel this upload?").assertExists()
        onNode(hasText("Cancel upload") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(UploadsEvent.ConfirmCancel("3k2"), events.last())
    }

    @Test
    fun cancellingTheRestAsksFirst() = runComposeUiTest {
        val events = mutableListOf<UploadsEvent>()
        setContent { AppPreview { UploadsUi(UploadsSamples.confirmingCancelRest.copy(eventSink = { events += it })) } }
        onNodeWithText("Cancel the rest of this import?").assertExists()
        onNode(hasText("Cancel rest") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(UploadsEvent.ConfirmCancelRest(UploadsSamples.IMPORT_ID), events.last())
    }

    @Test
    fun aLateCancelSaysWhy() = runComposeUiTest {
        setContent { AppPreview { UploadsUi(UploadsSamples.cancelFailed) } }
        onNodeWithText("Couldn't cancel: it has started uploading", substring = true).assertExists()
    }

    @Test
    fun nothingToUploadSaysSo() = runComposeUiTest {
        setContent { AppPreview { UploadsUi(UploadsSamples.empty) } }
        onNodeWithText("Nothing to upload. Everything you saved has uploaded.").assertExists()
    }
}
