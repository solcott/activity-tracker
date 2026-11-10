package io.github.solcott.activitytracker.app.gear

import io.github.solcott.activitytracker.app.text.AppText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What a device row says, which both UIs ask (dev/shared-display-text, "Devices read the same in both UIs"). */
class LinkedSensorTextsTest {
    private val edge = LinkedSensor("edge", "Garmin", "Edge 840", serialTail = "2071")

    @Test
    fun theNameIsTheMakerAndModel() {
        assertEquals(AppText.written("Garmin Edge 840"), LinkedSensorTexts.name(edge))
        assertEquals(
            AppText.written("Shearwater Perdix AI"),
            LinkedSensorTexts.name(LinkedSensor("perdix", "Shearwater", "Shearwater Perdix AI")),
        )
        assertEquals(AppText.written("H10"), LinkedSensorTexts.name(LinkedSensor("strap", null, "H10")))
    }

    @Test
    fun theLineIsTheSerialOrTheWeakNote() {
        assertEquals(AppText.named("new_device_serial", AppText.written("2071")), LinkedSensorTexts.detail(edge))
        assertEquals(AppText.named("new_device_weak"), LinkedSensorTexts.detail(LinkedSensor("strap", "Polar", "H10", weak = true)))
        assertNull(LinkedSensorTexts.detail(LinkedSensor("strap", "Polar", "H10")))
    }

    @Test
    fun theActionsAreReadAloudWithTheName() {
        assertEquals(AppText.named("gear_unignore_named", AppText.written("Garmin Edge 840")), LinkedSensorTexts.unignore(edge))
        assertEquals(
            AppText.named("gear_unlink_named", AppText.written("SRAM 1052")),
            LinkedSensorTexts.unlink(LinkedSensor("power", "SRAM", "1052")),
        )
    }
}
