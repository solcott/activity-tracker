package io.github.solcott.activitytracker.app.record

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test

/** What the recording screen says about a recording offered back or one that couldn't be saved (record/gps-recording). */
@OptIn(ExperimentalTestApi::class)
class RecordUiTest {
    @Test
    fun aRecoveredRecordingOffersResumeFinishOrDiscard() = runComposeUiTest {
        setContent { AppPreview { RecordUi(RecordSamples.all.getValue("recovered")) } }
        onNodeWithText("This recording stopped when the app closed", substring = true).assertExists()
        onNodeWithText("Resume").assertExists()
        onNodeWithText("Finish & save").assertExists()
        onNodeWithText("Discard").assertExists()
        onNodeWithText("Start").assertDoesNotExist()
    }

    @Test
    fun aFailedSaveIsShown() = runComposeUiTest {
        setContent { AppPreview { RecordUi(RecordSamples.all.getValue("saveFailed")) } }
        onNodeWithText("Couldn't save the recording on this device", substring = true).assertExists()
    }
}
