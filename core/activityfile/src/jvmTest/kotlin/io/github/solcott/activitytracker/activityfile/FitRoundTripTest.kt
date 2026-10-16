package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.activityfile.FitExportFixtures.dive
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.ride
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.start
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.write
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.GearChange
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

/** Exported FIT files read back through the import, with Garmin's Java SDK decoding. */
class FitRoundTripTest {
    private val reader = ActivityFileReader(GarminJavaFitDecoder())

    /** A ride's stored streams, in lexicon units, at values FIT's scales hold exactly. */
    private val streams =
        ActivityStreamData(
            time = (0 until 10).toList(),
            distance = (0 until 10).map { it * 1100 },
            altitude = (0 until 10).map { 10_000 + it * 20 },
            speed = (0 until 10).map { 11_000 },
            heartRate = (0 until 10).map { 120 + it },
            power = (0 until 10).map { 200 + it * 10 },
            cadence = (0 until 10).map { 85 + it },
            temperature = (0 until 10).map { 180 },
            gears = listOf(GearChange(time = 2, front = 2, rear = 5), GearChange(time = 6, front = 1, rear = 5)),
        )

    private val track = (0 until 10).map { LocationSample(47.6 + it * 0.0001, -122.3, null, null, null, start + it.seconds) }

    @Test
    fun rideComesBackAsTheSameActivity() = runTest {
        val exported = ride.copy(points = ExportPoints.join(start, track, streams), gearChanges = streams.gears.orEmpty())
        val read = reader.read(write(exported)).single()
        assertEquals(ActivityType(Sport.RIDE, SubSport.ROAD), read.type)
        assertEquals(start, read.startedAt)
        assertEquals(10.seconds, read.elapsed)
        assertEquals(9L, read.movingSeconds)
        assertEquals(99.0, read.distanceMeters)
        assertEquals(125, read.avgHeartRate)
        assertEquals(129, read.maxHeartRate)
        assertEquals(track.map { it.latitude.round7() }, read.samples.map { it.latitude.round7() })
        val back = read.streams!!
        assertEquals(streams.time, back.time)
        assertEquals(streams.distance, back.distance)
        assertEquals(streams.altitude, back.altitude)
        assertEquals(streams.speed, back.speed)
        assertEquals(streams.heartRate, back.heartRate)
        assertEquals(streams.power, back.power)
        assertEquals(streams.cadence, back.cadence)
        assertEquals(streams.temperature, back.temperature)
        assertEquals(streams.gears, back.gears)
    }

    @Test
    fun diveComesBackWithItsSummaryAndFirstGas() = runTest {
        val read = reader.read(write(dive)).single()
        assertEquals(ActivityType(Sport.DIVE, SubSport.CCR), read.type)
        assertEquals(dive.points.map { (it.depthMeters!! * 100).toInt() }, read.streams!!.depth)
        val back = read.dive!!
        assertEquals(30.0, back.maxDepthMeters)
        assertEquals(25.0, back.avgDepthMeters)
        assertEquals(90.0, back.bottomTimeSeconds)
        assertEquals(60, back.surfaceIntervalMinutes)
        assertEquals(42, back.diveNumber)
        assertEquals(30, back.gradientFactorLow)
        assertEquals(70, back.gradientFactorHigh)
        assertEquals(5, back.startCns)
        assertEquals(18, back.endCns)
        assertEquals(25, back.oxygenToxicityOtu)
        assertEquals(true, back.closedCircuit)
        // Later gases are in the file, but with no switch times the import counts only the first as breathed.
        assertEquals(DiveGas(21, 0), back.gases.first())
        assertEquals(listOf(DiveTank(200.0, 80.0, 1200.0)), back.tanks)
    }

    private fun Double.round7() = kotlin.math.round(this * 1e7) / 1e7
}
