package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.DeviceConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FitDevicesTest {
    private val fenix = DecodedSource("activity", "garmin", "fenix7", 0L, serialNumber = "3400000001")

    @Suppress("LongParameterList") // One device_info, field by field.
    private fun sensor(
        index: Int?,
        source: String,
        type: String?,
        manufacturer: String? = "garmin",
        product: String? = null,
        battery: String? = null,
        percent: Int? = null,
        serial: Long? = null,
        version: Double? = null,
    ) = DecodedSensor(index, source, type, manufacturer, product, battery, percent, serialNumber = serial, softwareVersion = version)

    @Test
    fun aWatchWithOnlyItsBuiltInSensorsIsOneRecorder() {
        val devices =
            FitDevices.interpret(
                fenix,
                listOf(
                    sensor(0, "local", null, product = "fenix7", serial = 3_400_000_001L, version = 21.19),
                    sensor(1, "local", "gps"),
                    sensor(2, "local", "wrist_hr"),
                ),
                emptyList(),
            )
        assertEquals(
            ImportedDevices(
                listOf(
                    ImportedDevice(
                        recorder = true,
                        manufacturer = "Garmin",
                        model = "Fenix 7",
                        serialNumber = "3400000001",
                        connection = DeviceConnection.LOCAL,
                        softwareVersion = "21.19",
                    )
                )
            ),
            devices,
        )
    }

    @Test
    fun aStrapListedTwiceIsOneDeviceWithItsBatteryAtTheEnd() {
        val devices =
            FitDevices.interpret(
                fenix,
                listOf(
                    sensor(1, "antplus", "heart_rate", product = "hrm_pro", battery = "good", version = 4.1),
                    sensor(1, "antplus", "heart_rate", product = "hrm_pro", battery = "low", percent = 20),
                ),
                emptyList(),
            )!!
        val strap = devices.devices.single { !it.recorder }
        assertEquals(SensorBattery.Low, strap.battery)
        assertEquals(20, strap.batteryPercent)
        assertEquals("4.1", strap.softwareVersion, "a field only the earlier message gives is kept")
        assertEquals(2, devices.devices.size)
    }

    @Test
    fun aSerialSentAfterTheFirstMessageIsKept() {
        val devices =
            FitDevices.interpret(
                fenix,
                listOf(
                    sensor(4, "antplus", "bike_power", "sram", "1052"),
                    sensor(4, "antplus", "bike_power", "sram", "1052", serial = 3_236_233_426L),
                    sensor(4, "antplus", "bike_power", "sram", "1052"),
                ),
                emptyList(),
            )!!
        val power = devices.devices.last()
        assertEquals(SensorKind.Power, power.kind)
        assertEquals("SRAM", power.manufacturer)
        assertEquals("1052", power.model, "the product number identifies a device only its maker can name")
        assertEquals("3236233426", power.serialNumber)
        assertEquals(DeviceConnection.ANT_PLUS, power.connection)
    }

    @Test
    fun aBluetoothSensorWithoutASerialIsKept() {
        val strap = FitDevices.interpret(null, listOf(sensor(2, "bluetooth_low_energy", "heart_rate", "polar")), emptyList())!!.devices
        assertEquals(
            listOf(
                ImportedDevice(
                    recorder = false,
                    kind = SensorKind.HeartRate,
                    manufacturer = "Polar",
                    connection = DeviceConnection.BLUETOOTH_LOW_ENERGY,
                )
            ),
            strap,
        )
    }

    @Test
    fun anAntSensorKeepsItsAntIds() {
        val speed =
            FitDevices.interpret(
                    null,
                    listOf(DecodedSensor(1, "antplus", "bike_speed", "wahoo_fitness", null, null, null, 3_912_345_678L, 51_234, 5)),
                    emptyList(),
                )!!
                .devices
                .single()
        assertEquals(SensorKind.Speed, speed.kind)
        assertEquals(51_234, speed.antDeviceNumber)
        assertEquals(5, speed.antTransmissionType)
        assertEquals("3912345678", speed.serialNumber)
    }

    @Test
    fun keepsTheActivityProfileWhenTheSessionNamesOne() {
        val session = DecodedSummary(null, null, null, null, null, null, null, null, null, null, null, null, null, null)
        assertEquals(
            "MOUNTAIN",
            FitDevices.interpret(fenix, emptyList(), listOf(session.copy(sportProfileName = "MOUNTAIN")))?.activityProfile,
        )
        assertNull(FitDevices.interpret(fenix, emptyList(), listOf(session))?.activityProfile)
    }

    @Test
    fun firmwareVersionsReadAsDevicesShowThem() {
        val versions =
            listOf(31.33, 5.4, 2.05, 3.0, 1.399999976158142).map { v ->
                FitDevices.interpret(null, listOf(sensor(1, "antplus", "heart_rate", version = v)), emptyList())!!
                    .devices
                    .single()
                    .softwareVersion
            }
        assertEquals(listOf("31.33", "5.4", "2.05", "3", "1.4"), versions)
    }

    @Test
    fun aFileNamingNoDeviceHasNone() {
        assertNull(FitDevices.interpret(null, emptyList(), emptyList()))
        assertNull(FitDevices.interpret(DecodedSource(null, null, null, null), emptyList(), emptyList()))
    }
}
