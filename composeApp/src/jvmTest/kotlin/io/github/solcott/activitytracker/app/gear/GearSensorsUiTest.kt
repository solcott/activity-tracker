package io.github.solcott.activitytracker.app.gear

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.preview.SessionPreviewSubUis
import io.github.solcott.activitytracker.app.record.ImportActivityEvent
import io.github.solcott.activitytracker.app.record.ImportActivitySamples
import io.github.solcott.activitytracker.app.record.ImportActivityUi
import io.github.solcott.activitytracker.app.record.NewDeviceEvent
import io.github.solcott.activitytracker.app.record.NewDevicesForm
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

/** New devices on the import screen, linked devices on the gear page, and ignored devices (gear/gear-sensors). */
@OptIn(ExperimentalTestApi::class)
class GearSensorsUiTest {
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

    @Test
    fun theGearPageListsLinkedDevicesWithUnlink() = runComposeUiTest {
        val events = mutableListOf<GearEvent>()
        val state = GearSamples.linked.copy(eventSink = { events += it })
        setContent { AppPreview(subUis = SessionPreviewSubUis) { GearUi(state) } }
        onNodeWithText("Linked devices").performScrollTo()
        onNodeWithText("SRAM 1052").assertExists()
        onNodeWithText("Serial …3426").assertExists()
        onNodeWithContentDescription("Unlink SRAM 1052").performScrollTo().performClick()
        onNodeWithText("ROAD").performScrollTo().assertExists()
        assertEquals(listOf<GearEvent>(GearEvent.UnlinkSensor("meter")), events)
    }

    @Test
    fun anIgnoredDeviceCanBeUnignored() = runComposeUiTest {
        val events = mutableListOf<IgnoredSensorsEvent>()
        val state = IgnoredSensorsSamples.all.getValue(SampleKind.LOADED).copy(eventSink = { events += it })
        setContent { AppPreview(subUis = SessionPreviewSubUis) { IgnoredSensorsUi(state) } }
        onNodeWithText("Garmin Edge 840").assertExists()
        onNodeWithContentDescription("Un-ignore Garmin Edge 840").performClick()
        assertEquals(listOf<IgnoredSensorsEvent>(IgnoredSensorsEvent.Unignore("edge")), events)
    }

    @Test
    fun theGearListOpensIgnoredDevicesFromItsMenu() = runComposeUiTest {
        val events = mutableListOf<GearListEvent>()
        val state = GearListSamples.all.getValue(SampleKind.LOADED).copy(eventSink = { events += it })
        setContent { AppPreview(subUis = SessionPreviewSubUis) { GearListUi(state) } }
        onNodeWithContentDescription("More").performClick()
        onNodeWithText("Ignored devices").performClick()
        assertEquals(listOf<GearListEvent>(GearListEvent.OpenIgnoredSensors), events)
    }
}
