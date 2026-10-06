package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals("Garmin Fenix 7", ride.device, "the file_id's device, not the device_info list")
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
