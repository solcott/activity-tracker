package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.DeviceConnection
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** A mountain bike ride's power, shifting, trail dynamics and sensors, decoded by the Java SDK. */
class GarminFitRideTest {
    private val ride = DecodedActivityMapper.map(GarminJavaFitDecoder().decode(FitFixtures.mtbRide(Instant.parse("2026-10-02T19:52:33Z"))))

    @Test
    fun readsTheSessionsPowerTotals() {
        assertEquals(ActivityType(Sport.RIDE, SubSport.MOUNTAIN), ride.type)
        assertEquals(
            ImportedPower(180, 650, 210, 300_000.0, 250, 0.84, 45.0),
            ride.power?.let { it.copy(intensityFactor = it.intensityFactor?.round2()) },
        )
        assertEquals(80, ride.avgCadence)
        assertEquals(110, ride.maxCadence)
        assertEquals(120.0, ride.elevationLossMeters)
    }

    @Test
    fun readsShiftingAndTimeInEachGear() {
        val shifting = ride.shifting!!
        assertEquals(3, shifting.rearShifts)
        assertEquals(listOf(32), shifting.chainrings)
        assertEquals(listOf(17, 19, 21), shifting.cogs)
        // Gear 5 for a minute, 7 for a minute, then 6 to the end at 10 min.
        assertEquals(listOf(GearTime(5, 21, 60), GearTime(6, 19, 480), GearTime(7, 17, 60)), shifting.timeInGears)
    }

    @Test
    fun readsJumpsGritAndFlow() {
        assertEquals(
            ImportedMtb(jumps = 2, longestHangTimeSeconds = 0.9, longestJumpMeters = 8.2, grit = 12.5, flow = 2.5),
            ride.mtb?.rounded(),
        )
    }

    @Test
    fun listsThePairedSensorsWithTheirBatteryAtTheEnd() {
        assertEquals(
            listOf(
                ImportedSensor(SensorKind.HeartRate, "Garmin", "HRM 200", SensorBattery.Good, 75),
                ImportedSensor(SensorKind.Power, "SRAM", null, SensorBattery.Low, null),
                ImportedSensor(SensorKind.Shifting, "SRAM", null, SensorBattery.Ok, null),
            ),
            ride.sensors,
        )
        assertEquals("Garmin Edge 840", ride.device)
    }

    @Test
    fun decodesEachDevicesIdentifiersFirmwareAndOperatingTime() {
        val decoded = GarminJavaFitDecoder().decode(FitFixtures.mtbRide(Instant.parse("2026-10-02T19:52:33Z")))
        assertEquals(Edge840Devices.EDGE_SERIAL.toString(), decoded.fileId?.serialNumber)
        assertEquals("MOUNTAIN", decoded.sessions.single().sportProfileName)
        val power = decoded.sensors.filter { it.deviceIndex == 3 }
        assertEquals(listOf(null, Edge840Devices.POWER_METER_SERIAL, Edge840Devices.POWER_METER_SERIAL), power.map { it.serialNumber })
        assertEquals(listOf(2_215_232L, 2_215_232L, 2_221_408L), power.map { it.cumulativeOperatingSeconds })
        assertEquals(1.574, power.last().batteryVolts!!, 1e-3)
        assertEquals(1.4, power.last().softwareVersion!!, 1e-3)
        assertTrue(decoded.sensors.all { it.antDeviceNumber == null && it.antTransmissionType == null }, "the Edge 840 writes no ANT IDs")
        val edge = decoded.sensors.first { it.deviceIndex == 0 }
        assertEquals(Edge840Devices.EDGE_SERIAL, edge.serialNumber)
        assertEquals(31.33, edge.softwareVersion!!, 1e-3)
        assertEquals(Edge840Devices.DERAILLEUR_SERIAL, decoded.sensors.last { it.deviceIndex == 4 }.serialNumber)
    }

    @Test
    fun keepsEachDeviceOfTheEdge840RideWithItsIdentifiers() {
        assertEquals(
            ImportedDevices(
                listOf(
                    ImportedDevice(
                        recorder = true,
                        manufacturer = "Garmin",
                        model = "Edge 840",
                        serialNumber = "3510682071",
                        connection = DeviceConnection.LOCAL,
                        softwareVersion = "31.33",
                    ),
                    ImportedDevice(
                        recorder = false,
                        kind = SensorKind.HeartRate,
                        manufacturer = "Garmin",
                        model = "HRM 200",
                        serialNumber = "3629252158",
                        connection = DeviceConnection.BLUETOOTH_LOW_ENERGY,
                        softwareVersion = "5.4",
                        battery = SensorBattery.Good,
                        batteryPercent = 75,
                    ),
                    ImportedDevice(
                        recorder = false,
                        kind = SensorKind.Power,
                        manufacturer = "SRAM",
                        model = "1052",
                        serialNumber = "3236233426",
                        connection = DeviceConnection.ANT_PLUS,
                        softwareVersion = "1.4",
                        battery = SensorBattery.Low,
                        batteryMillivolts = 1574,
                        operatingSeconds = 2_221_408,
                    ),
                    ImportedDevice(
                        recorder = false,
                        kind = SensorKind.Shifting,
                        manufacturer = "SRAM",
                        model = "1060",
                        serialNumber = "1518497200",
                        connection = DeviceConnection.ANT_PLUS,
                        softwareVersion = "2.05",
                        battery = SensorBattery.Ok,
                        batteryMillivolts = 2559,
                        operatingSeconds = 25_872,
                    ),
                ),
                activityProfile = "MOUNTAIN",
            ),
            ride.devices,
        )
    }

    @Test
    fun decodesAnAntSensorsIdsAndABluetoothSensorWithoutASerial() {
        val decoded = GarminJavaFitDecoder().decode(SensorFitFixtures.antSensorRide(Instant.parse("2026-10-02T19:52:33Z")))
        val speed = decoded.sensors.single { it.deviceIndex == 1 }
        assertEquals(51_234, speed.antDeviceNumber)
        assertEquals(5, speed.antTransmissionType)
        assertEquals(3_912_345_678L, speed.serialNumber)
        val strap = decoded.sensors.single { it.deviceIndex == 2 }
        assertNull(strap.serialNumber)
        assertNull(strap.antDeviceNumber)
        assertNull(decoded.sessions.single().sportProfileName)
    }

    @Test
    fun decodesLapsCaloriesTrainingEffectAndPowerBalance() {
        val decoded = GarminJavaFitDecoder().decode(FitFixtures.mtbRide(Instant.parse("2026-10-02T19:52:33Z")))
        assertEquals(listOf(300.0 to 1_200.0, 300.0 to 1_800.0), decoded.laps.map { it.totalElapsedSeconds to it.totalDistanceMeters })
        assertEquals(listOf(170, 190), decoded.laps.map { it.avgPowerWatts })
        val session = decoded.sessions.single()
        assertEquals(450, session.totalCalories)
        assertEquals(3.4, session.aerobicTrainingEffect!!, 1e-3)
        assertEquals(1.2, session.anaerobicTrainingEffect!!, 1e-3)
        assertEquals(52, FitBalance.rightPercent(decoded.records.first().leftRightBalance!!))
    }

    @Test
    fun decodesARunsDynamicsAndRespiration() {
        val decoded = GarminJavaFitDecoder().decode(FitFixtures.intervalRun(Instant.parse("2026-10-02T19:52:33Z")))
        assertEquals(FitFixtures.INTERVAL_LAPS, decoded.laps.size)
        val sample = decoded.records.first()
        assertEquals(30.5, sample.respirationRate!!, 1e-3)
        assertEquals(85.4, sample.verticalOscillationMm!!, 0.1)
        assertEquals(245.5, sample.stanceTimeMs!!, 0.1)
        assertEquals(49.75, sample.stanceTimeBalancePercent!!, 0.01)
        assertEquals(1250.0, sample.stepLengthMm!!, 0.1)
        assertEquals(6.83, sample.verticalRatioPercent!!, 0.01)
    }

    private fun Double.round2() = kotlin.math.round(this * 100) / 100

    private fun ImportedMtb.rounded() =
        copy(
            longestHangTimeSeconds = longestHangTimeSeconds?.round2(),
            longestJumpMeters = longestJumpMeters?.round2(),
            grit = grit?.round2(),
            flow = flow?.round2(),
        )
}
