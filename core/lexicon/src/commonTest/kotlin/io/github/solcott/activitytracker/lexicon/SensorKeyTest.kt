package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ActivityDevice
import io.github.solcott.activitytracker.lexicon.app.DeviceRole
import io.github.solcott.activitytracker.lexicon.app.GearSuggestion
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.SensorGear
import io.github.solcott.activitytracker.lexicon.app.SensorKey
import io.github.solcott.activitytracker.lexicon.app.SensorKeyStrength
import io.github.solcott.activitytracker.lexicon.app.SensorType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How a device is identified across imports, and the gear it's suggested as (gear/gear-sensors). */
class SensorKeyTest {
    private fun sensor(kind: String?, maker: String? = null, model: String? = null) =
        ActivityDevice(role = DeviceRole.SENSOR, kind = kind, manufacturer = maker, model = model)

    @Test
    fun aSerialIsTheKey() {
        val meter =
            sensor(SensorType.POWER, "SRAM", "Power meter")
                .copy(serialNumber = "3236233426", antDeviceNumber = 12_345, antTransmissionType = 5)
        val key = assertNotNull(SensorKey.of(meter))
        assertEquals(SensorKey(manufacturer = "SRAM", serialNumber = "3236233426", model = "Power meter"), key, "the model only names it")
        assertTrue(key.matches(SensorKey(manufacturer = "SRAM", serialNumber = "3236233426")))
        assertEquals(SensorKeyStrength.SERIAL, key.strength)
        assertFalse(key.weak)
    }

    @Test
    fun withoutASerialTheAntIdsAreTheKey() {
        val speed = sensor(SensorType.SPEED, "Wahoo", "RPM Speed").copy(antDeviceNumber = 4_242, antTransmissionType = 1)
        val key = SensorKey.of(speed)
        assertEquals(SensorKey(manufacturer = "Wahoo", antDeviceNumber = 4_242, antTransmissionType = 1, model = "RPM Speed"), key)
        assertEquals(SensorKeyStrength.ANT, key?.strength)
    }

    @Test
    fun aModelOnlyStrapIsAWeakKey() {
        val key = SensorKey.of(sensor(SensorType.HEART_RATE, "Garmin", "HRM 200"))
        assertEquals(SensorKey(manufacturer = "Garmin", model = "HRM 200"), key)
        assertEquals(SensorKeyStrength.MODEL, key?.strength)
        assertTrue(key!!.weak)
    }

    @Test
    fun aDeviceKnownOnlyByItsKindHasNoKey() {
        assertNull(SensorKey.of(sensor(SensorType.HEART_RATE)))
        assertNull(SensorKey.of(sensor(SensorType.HEART_RATE, maker = "Garmin")), "a maker alone isn't enough")
        assertNull(SensorKey.of(sensor(SensorType.HEART_RATE, model = "HRM 200")), "a model needs its maker")
        assertNull(SensorKey.of(sensor(SensorType.HEART_RATE).copy(antDeviceNumber = 4_242)), "half an ANT ID")
    }

    @Test
    fun makersAndModelsMatchCaseFoldedSerialsAsWritten() {
        val a = SensorKey.of(sensor(SensorType.HEART_RATE, " garmin", "hrm 200 "))!!
        val b = SensorKey.of(sensor(SensorType.HEART_RATE, "Garmin", "HRM 200"))!!
        assertTrue(a.matches(b))
        val serial = SensorKey(manufacturer = "SRAM", serialNumber = "ABC1")
        assertTrue(serial.matches(SensorKey(manufacturer = "sram", serialNumber = "ABC1")))
        assertFalse(serial.matches(SensorKey(manufacturer = "SRAM", serialNumber = "abc1")), "serials match as written")
    }

    @Test
    fun keysOfDifferentStrengthsNeverMatch() {
        val bySerial = SensorKey(manufacturer = "Garmin", serialNumber = "1")
        val byModel = SensorKey(manufacturer = "Garmin", model = "HRM 200")
        assertFalse(bySerial.matches(byModel))
        assertFalse(byModel.matches(bySerial))
    }

    @Test
    fun everyKindHasASuggestion() {
        fun of(kind: String?) = SensorGear.suggestion(DeviceRole.SENSOR, kind)
        assertEquals(GearSuggestion(GearType.COMPONENT, "powerMeter"), of(SensorType.POWER))
        assertEquals(GearSuggestion(GearType.COMPONENT, "derailleur"), of(SensorType.SHIFTING))
        assertEquals(GearSuggestion(GearType.SENSOR, "heartRate"), of(SensorType.HEART_RATE))
        assertEquals(GearSuggestion(GearType.SENSOR, "speed"), of(SensorType.SPEED))
        assertEquals(GearSuggestion(GearType.SENSOR, "cadence"), of(SensorType.CADENCE))
        assertEquals(GearSuggestion(GearType.SENSOR, "speed"), of(SensorType.SPEED_CADENCE))
        assertEquals(GearSuggestion(GearType.SENSOR, "footPod"), of(SensorType.FOOTPOD))
        assertEquals(GearSuggestion(GearType.SENSOR, "radar"), of(SensorType.RADAR))
        assertEquals(GearSuggestion(GearType.DEVICE), of(SensorType.LIGHT))
        assertEquals(GearSuggestion(GearType.TRAINER, "smart"), of(SensorType.TRAINER))
        assertEquals(GearSuggestion(GearType.SENSOR), of(SensorType.OTHER))
        assertEquals(GearSuggestion(GearType.SENSOR), of(null))
        assertEquals(GearSuggestion(GearType.DEVICE), SensorGear.suggestion(DeviceRole.RECORDER, null), "the recording device")
        // Every suggested kind is one its type offers.
        listOf(
                SensorType.POWER,
                SensorType.SHIFTING,
                SensorType.HEART_RATE,
                SensorType.SPEED,
                SensorType.CADENCE,
                SensorType.FOOTPOD,
                SensorType.RADAR,
                SensorType.TRAINER,
            )
            .map(::of)
            .forEach { assertEquals(it.kind, GearType.knownKind(it.type, it.kind), "$it") }
    }
}
