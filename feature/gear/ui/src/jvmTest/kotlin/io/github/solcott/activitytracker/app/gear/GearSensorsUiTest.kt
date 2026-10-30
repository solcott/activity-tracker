package io.github.solcott.activitytracker.app.gear

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

/** Linked devices on the gear page, and ignored devices (gear/gear-sensors). */
@OptIn(ExperimentalTestApi::class)
class GearSensorsUiTest {
    @Test
    fun theGearPageListsLinkedDevicesWithUnlink() = runComposeUiTest {
        val events = mutableListOf<GearEvent>()
        val state = GearSamples.linked.copy(eventSink = { events += it })
        setContent { AppPreview { GearUi(state) } }
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
        setContent { AppPreview { IgnoredSensorsUi(state) } }
        onNodeWithText("Garmin Edge 840").assertExists()
        onNodeWithContentDescription("Un-ignore Garmin Edge 840").performClick()
        assertEquals(listOf<IgnoredSensorsEvent>(IgnoredSensorsEvent.Unignore("edge")), events)
    }

    @Test
    fun theGearListOpensIgnoredDevicesFromItsMenu() = runComposeUiTest {
        val events = mutableListOf<GearListEvent>()
        val state = GearListSamples.all.getValue(SampleKind.LOADED).copy(eventSink = { events += it })
        setContent { AppPreview { GearListUi(state) } }
        onNodeWithContentDescription("More").performClick()
        onNodeWithText("Ignored devices").performClick()
        assertEquals(listOf<GearListEvent>(GearListEvent.OpenIgnoredSensors), events)
    }
}
