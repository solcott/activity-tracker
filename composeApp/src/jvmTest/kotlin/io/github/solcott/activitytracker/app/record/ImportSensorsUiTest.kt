package io.github.solcott.activitytracker.app.record

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.preview.SessionPreviewSubUis
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

/** New devices on the import screen (gear/gear-sensors). */
@OptIn(ExperimentalTestApi::class)
class ImportSensorsUiTest {
    @Test
    fun anImportOffersItsNewDevicesAndFlagsTwoBikes() = runComposeUiTest {
        val events = mutableListOf<NewDeviceEvent>()
        val state = ImportActivitySamples.all.getValue("newDevices")
        val fields = state.newDevices.copy(eventSink = { events += it })
        setContent { AppPreview(subUis = SessionPreviewSubUis) { NewDevicesForm(fields) } }
        onNodeWithText("Edge 840", substring = true).assertExists()
        onNodeWithText("Recording device · Serial …2071").assertExists()
        onNodeWithText("Known only by its model, so it may match other units").assertExists()
        onNodeWithText("Link the MOUNTAIN profile").assertExists()
        onNodeWithText("Tarmac").assertExists()
        onNodeWithText("Race kit").assertExists()
        onAllNodesWithText("Not gear").assertCountEquals(3)
        assertEquals(emptyList(), events)
    }

    @Test
    fun theImportScreenShowsTheConflictAndAfterSavingTheOffer() = runComposeUiTest {
        val events = mutableListOf<ImportActivityEvent>()
        val state = ImportActivitySamples.all.getValue("afterImport").copy(eventSink = { events += it })
        setContent { AppPreview(subUis = SessionPreviewSubUis) { ImportActivityUi(state) } }
        onNodeWithText("Add Tarmac to 10 earlier activities that name this device?").assertExists()
        onNodeWithText("Add").performClick()
        onNodeWithText("Not now").performClick()
        assertEquals(listOf<ImportActivityEvent>(ImportActivityEvent.After.ApplyToPast, ImportActivityEvent.After.Done), events)
    }
}
