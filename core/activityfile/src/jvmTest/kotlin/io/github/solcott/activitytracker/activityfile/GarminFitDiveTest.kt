package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.DecoModel
import io.github.solcott.activitytracker.lexicon.app.DiveAlertKind
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.WaterType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** Dive logs from Garmin Descent watches (see resources/dives for where they come from) and an encoded one for what they lack. */
class GarminFitDiveTest {
    private val reader = ActivityFileReader(GarminJavaFitDecoder())

    private suspend fun read(name: String): ImportedActivity {
        val bytes = assertNotNull(javaClass.getResourceAsStream("/dives/$name.fit.gz")).use { it.readBytes() }
        return reader.read(bytes).single()
    }

    private suspend fun decode(name: String): DecodedActivity {
        val bytes = assertNotNull(javaClass.getResourceAsStream("/dives/$name.fit.gz")).use { it.readBytes() }
        return GarminJavaFitDecoder().decode(gunzip(bytes, Int.MAX_VALUE))
    }

    private fun DecodedActivity.secondsOf(millis: Long) = (millis - sessions.first().startTimeMillis!!) / 1000

    @Test
    fun aDescentKeepsItsSwitchTimesAndDecoState() = runTest {
        val decoded = decode("fit-parser-swift-TestDive3")
        assertEquals(
            listOf(3546L to DiveGas(52, 0)),
            decoded.gasSwitches.map { decoded.secondsOf(it.timestampMillis) to it.gas },
            "the bailout at 59 min; the diluent is never switched to",
        )
        val dive = assertNotNull(decoded.dive)
        assertEquals(0 to 104, dive.startN2 to dive.endN2)
        assertEquals(WaterType.SALT, dive.waterType)
        assertEquals(1025.0, dive.waterDensityKgPerM3!!, 1e-3)
        val deco = decoded.records.first { (it.nextStopDepthMeters ?: 0.0) > 0 }
        assertEquals(385L, decoded.secondsOf(deco.timestampMillis!!))
        assertEquals(3.0, deco.nextStopDepthMeters!!, 1e-3)
        assertEquals(0L, deco.ndlSeconds, "NDL is 0 while in deco")
        assertEquals(363L, deco.ttsSeconds)
        assertEquals(4 to 100, deco.cnsPercent to deco.n2LoadPercent)
        assertEquals(177, decoded.sessions.first().totalCalories)
    }

    @Test
    fun anAirIntegratedDiveKeepsItsTankReadings() = runTest {
        val decoded = decode("fit-parser-test-diving")
        val tank = assertNotNull(decoded.dive).tanks.single()
        assertEquals("1234567890", tank.sensor)
        assertEquals(112, decoded.tankUpdates.size)
        assertTrue(decoded.tankUpdates.all { it.sensor == tank.sensor })
        assertEquals(223.59, decoded.tankUpdates.first().pressureBar, 1e-2)
        assertEquals(100L, decoded.secondsOf(decoded.tankUpdates.first().timestampMillis))
        assertEquals(691.0, decoded.dive.descentTimeSeconds!!, 1e-3)
        assertTrue(decoded.records.any { it.respirationRate != null })
    }

    @Test
    fun aRebreatherDiveFromADescentMk2() = runTest {
        val activity = read("fit-parser-swift-TestDive3")
        assertEquals(ActivityType(Sport.DIVE, SubSport.CCR), activity.type)
        assertNull(activity.localStart, "FIT times are UTC")
        assertNull(activity.distanceMeters)

        val dive = assertNotNull(activity.dive)
        assertTrue(dive.closedCircuit)
        assertEquals(62.02, dive.maxDepthMeters!!, 1e-3)
        assertEquals(24.621, dive.avgDepthMeters!!, 1e-3)
        assertEquals(538, dive.diveNumber)
        assertEquals(21, dive.surfaceIntervalMinutes)
        assertEquals(50 to 70, dive.gradientFactorLow to dive.gradientFactorHigh)
        assertEquals(1 to 44, dive.startCns to dive.endCns)
        assertEquals(117, dive.oxygenToxicityOtu)
        assertEquals(6044.462, dive.bottomTimeSeconds!!, 1e-2)
        assertEquals(listOf(DiveGas(17, 30), DiveGas(52, 0)), dive.gases, "the diluent, then the bailout switched to")
        val entry = assertNotNull(dive.entry)
        assertEquals(-41.16569, entry.latitude, 1e-4)
        assertEquals(174.2197, entry.longitude, 1e-4)
        assertNull(dive.exit)
        assertTrue(dive.profile.size > 6000)
        assertTrue(dive.tanks.isEmpty())
    }

    @Test
    fun aDiveWithOnlyAnExitPosition() = runTest {
        val dive = assertNotNull(read("fit-parser-swift-TestDive4").dive)
        assertEquals(51.765, dive.maxDepthMeters!!, 1e-3)
        assertEquals(11, dive.diveNumber)
        assertEquals(0 to 51, dive.startCns to dive.endCns)
        assertEquals(136, dive.oxygenToxicityOtu)
        assertEquals(50 to 60, dive.gradientFactorLow to dive.gradientFactorHigh)
        assertEquals(listOf(DiveGas(18, 20), DiveGas(50, 0)), dive.gases)
        assertNull(dive.entry)
        val exit = assertNotNull(dive.exit)
        assertEquals(11.58715, exit.latitude, 1e-4)
        assertEquals(165.51056, exit.longitude, 1e-4)
    }

    @Test
    fun anAirIntegratedDive() = runTest {
        val activity = read("fit-parser-test-diving")
        assertEquals(ActivityType(Sport.DIVE, SubSport.SINGLE_GAS), activity.type, "one gas, though the watch was in Multi-Gas mode")

        val dive = assertNotNull(activity.dive)
        assertEquals(listOf(DiveGas(21, 0)), dive.gases)
        assertEquals(false, dive.closedCircuit)
        assertEquals(17.646, dive.maxDepthMeters!!, 1e-3)
        assertEquals(11.291, dive.avgDepthMeters!!, 1e-3)
        assertEquals(803, dive.diveNumber)
        assertEquals(17151, dive.surfaceIntervalMinutes)
        assertEquals(0 to 2, dive.startCns to dive.endCns)
        assertEquals(3, dive.oxygenToxicityOtu)
        assertEquals(40 to 85, dive.gradientFactorLow to dive.gradientFactorHigh)
        val tank = dive.tanks.single() // The second, unused transmitter reads 0 → 0.
        assertEquals(223.59, tank.startPressureBar!!, 1e-2)
        assertEquals(65.29, tank.endPressureBar!!, 1e-2)
        assertNull(tank.volumeUsedLiters, "no tank size was set")
        assertEquals(1.67, dive.sacBarPerMinute!!, 1e-3)
        assertNull(dive.rmvLitersPerMinute)
        val exit = assertNotNull(dive.exit)
        assertEquals(13.86622, exit.latitude, 1e-4)
        assertEquals(-61.07885, exit.longitude, 1e-4)
    }

    @Test
    fun anEncodedDiveWithEverythingTheSamplesLack() {
        val start = Instant.parse("2026-09-20T07:00:00Z")
        val activity = DecodedActivityMapper.map(GarminJavaFitDecoder().decode(FitFixtures.dive(start)))
        assertEquals(ActivityType(Sport.DIVE, SubSport.MULTI_GAS), activity.type)

        val dive = assertNotNull(activity.dive)
        assertEquals(listOf(DiveGas(21, 0), DiveGas(50, 0)), dive.gases, "the backup gas switched to; the disabled slot left out")
        assertEquals(7, dive.diveNumber, "the session's summary, not the lap's written before it")
        assertEquals(0 to 9, dive.startCns to dive.endCns)
        assertEquals(12, dive.oxygenToxicityOtu)
        assertEquals(60, dive.surfaceIntervalMinutes)
        assertEquals(35 to 75, dive.gradientFactorLow to dive.gradientFactorHigh)
        assertEquals(12.5, dive.avgDepthMeters!!, 1e-3, "the computer's average, not the profile's")
        assertEquals(14.2, dive.rmvLitersPerMinute!!, 1e-3)
        assertEquals(DiveTank(200.0, 60.0, 1680.0, sensor = "42"), dive.tanks.single())
        assertEquals(FitFixtures.DIVE_POINTS + 1, dive.profile.size)
        assertEquals(20.0, dive.profile.maxOf { it.depthMeters }, 1e-3)
        assertNull(activity.distanceMeters)
    }

    @Test
    fun anEncodedDivesSwitchesTankUpdatesAndDecoChannels() {
        val start = Instant.parse("2026-09-20T07:00:00Z")
        val decoded = GarminJavaFitDecoder().decode(FitFixtures.dive(start))
        assertEquals(
            listOf(420L to DiveGas(50, 0)),
            decoded.gasSwitches.map { decoded.secondsOf(it.timestampMillis) to it.gas },
            "the switch to slot 9, which has no dive_gas, is dropped",
        )
        assertEquals(
            (0..FitFixtures.DIVE_POINTS step 2).map { it * 60L + 5 },
            decoded.tankUpdates.map { decoded.secondsOf(it.timestampMillis) },
        )
        assertEquals(200.0, decoded.tankUpdates.first().pressureBar, 1e-3)
        val dive = assertNotNull(decoded.dive)
        assertEquals(10 to 55, dive.startN2 to dive.endN2)
        assertEquals(0.15, dive.avgAscentRateMps!!, 1e-3)
        assertEquals(0.4, dive.maxDescentRateMps!!, 1e-3)
        assertEquals(300.0, dive.descentTimeSeconds!!, 1e-3)
        assertEquals(WaterType.FRESH, dive.waterType)
        val deco = decoded.records[8]
        assertEquals(0.5 * (1 + 8 / 10.0), deco.ppO2Bar!!, 1e-2)
        assertEquals(3.0, deco.nextStopDepthMeters!!, 1e-3)
        assertEquals(120L to 240L, deco.nextStopSeconds to deco.ttsSeconds)
        assertEquals(8 to 42, deco.cnsPercent to deco.n2LoadPercent)
    }

    @Test
    fun anEncodedDivesAlertsAndSettings() {
        val start = Instant.parse("2026-09-20T07:00:00Z")
        val decoded = GarminJavaFitDecoder().decode(FitFixtures.dive(start))
        assertEquals(
            listOf(300L to DiveAlertKind.NDL_REACHED, 540L to DiveAlertKind.SAFETY_STOP_COMPLETE),
            decoded.diveAlerts.map { decoded.secondsOf(it.timestampMillis) to it.kind },
            "the dismissal is dropped",
        )
        val dive = assertNotNull(decoded.dive)
        assertEquals(DecoModel.ZHL16C, dive.decoModel)
        assertEquals(1.4, dive.ppO2WarnBar!!, 1e-3)
        assertEquals(1.6, dive.ppO2CriticalBar!!, 1e-3)
        assertEquals(1.6, dive.ppO2DecoBar!!, 1e-3)
        assertEquals(0.7, dive.lowSetpointBar!!, 1e-3)
        assertEquals(1.3, dive.highSetpointBar!!, 1e-3)
        assertEquals(180, dive.safetyStopSeconds)
    }
}
