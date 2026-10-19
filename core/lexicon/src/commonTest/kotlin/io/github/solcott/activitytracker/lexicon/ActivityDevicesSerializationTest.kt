package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ActivityDevice
import io.github.solcott.activitytracker.lexicon.app.ActivityDevicesRecord
import io.github.solcott.activitytracker.lexicon.app.DeviceBattery
import io.github.solcott.activitytracker.lexicon.app.DeviceConnection
import io.github.solcott.activitytracker.lexicon.app.DeviceRole
import io.github.solcott.activitytracker.lexicon.app.SensorType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Instant
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `feed.activityDevices`, the athlete's private record of an activity's devices. */
class ActivityDevicesSerializationTest {
    @Test
    fun devicesRoundTripWithTypeAndOmitNulls() {
        val devices =
            ActivityDevicesRecord(
                activity = AtUri("at://did:plc:alice/${Nsid.FEED_ACTIVITY}/3kride"),
                devices =
                    listOf(
                        ActivityDevice(
                            role = DeviceRole.RECORDER,
                            manufacturer = "Garmin",
                            model = "Edge 840",
                            serialNumber = "3510682071",
                            connection = DeviceConnection.LOCAL,
                        ),
                        ActivityDevice(
                            role = DeviceRole.SENSOR,
                            kind = SensorType.POWER,
                            manufacturer = "SRAM",
                            serialNumber = "3236233426",
                            connection = DeviceConnection.ANT_PLUS,
                            battery = DeviceBattery.OK,
                            batteryVoltage = 1574,
                            operatingTime = 2_221_408,
                        ),
                    ),
                createdAt = Instant.parse("2026-10-18T20:00:00Z"),
                activityProfile = "MOUNTAIN",
            )
        val json = AtJson.encodeToJsonElement(ActivityDevicesRecord.serializer(), devices).jsonObject
        assertEquals(Nsid.FEED_ACTIVITY_DEVICES, json["\$type"]?.jsonPrimitive?.content)
        val recorder = json["devices"]!!.jsonArray[0].jsonObject
        assertEquals("3510682071", recorder["serialNumber"]?.jsonPrimitive?.content)
        assertFalse("kind" in recorder)
        assertFalse("antDeviceNumber" in recorder)
        assertEquals(
            devices,
            AtJson.decodeFromString(ActivityDevicesRecord.serializer(), AtJson.encodeToString(ActivityDevicesRecord.serializer(), devices)),
        )
    }
}
