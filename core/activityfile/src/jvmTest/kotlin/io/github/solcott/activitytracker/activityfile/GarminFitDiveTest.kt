package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
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
        assertEquals(DiveTank(200.0, 60.0, 1680.0), dive.tanks.single())
        assertEquals(FitFixtures.DIVE_POINTS + 1, dive.profile.size)
        assertEquals(20.0, dive.profile.maxOf { it.depthMeters }, 1e-3)
        assertNull(activity.distanceMeters)
    }
}
