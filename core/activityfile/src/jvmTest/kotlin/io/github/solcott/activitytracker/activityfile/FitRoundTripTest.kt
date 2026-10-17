package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.activityfile.FitExportFixtures.benchWorkout
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.decoDive
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.dive
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.intervalRide
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.intervalRun
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.poolSwim
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.ride
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.start
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.triathlon
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.write
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.GearChange
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.WaterType
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
        val back = assertNotNull(read.dive)
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
        assertEquals(listOf(DiveTank(200.0, 80.0, 1200.0, sensor = "1")), back.tanks)
    }

    @Test
    fun aDecoDiveComesBackWithItsSwitchesTanksAndState() = runTest {
        val read = reader.read(write(decoDive)).single()
        val streams = read.streams!!
        assertEquals(decoDive.gasSwitches, streams.gasSwitches)
        assertEquals(listOf(DiveGas(21, 0), DiveGas(50, 0), DiveGas(100, 0)), read.dive!!.gases)
        assertEquals(
            decoDive.points.map { p -> p.tankPressuresBar.map { it?.let { bar -> (bar * 1000).toInt() } } },
            streams.tankPressures!!.let { tanks -> streams.time.indices.map { i -> tanks.map { it.pressure[i] } } },
        )
        assertEquals(decoDive.points.map { (it.ppO2Bar!! * 1000).roundToInt() }, streams.ppO2)
        assertEquals(decoDive.points.map { it.cnsPercent }, streams.cns)
        assertEquals(decoDive.points.map { it.nextStopDepthMeters?.let { m -> (m * 100).toInt() } }, streams.decoStopDepth)
        assertEquals(decoDive.points.map { it.ttsSeconds?.toInt() }, streams.tts)
        val back = assertNotNull(read.dive)
        assertEquals(listOf("1", "2"), back.tanks.map { it.sensor })
        assertEquals(WaterType.SALT, back.waterType)
        assertEquals(1025.0, back.waterDensityKgPerM3)
        assertEquals(10 to 58, back.startN2 to back.endN2)
        assertEquals(20.0, back.descentTimeSeconds)
        assertEquals(0.15, back.avgAscentRateMps!!, 1e-6)
    }

    @Test
    fun aRidesLapsAndTotalsComeBack() = runTest {
        val read = reader.read(write(intervalRide)).single()
        // FIT's lap always has a timer time, so a lap stored without a moving time comes back moving all of it.
        assertEquals(intervalRide.laps.map { it.copy(moving = it.moving ?: it.elapsed) }, read.streams!!.laps)
        assertEquals(300, read.calories)
        assertEquals(3.4, read.aerobicTrainingEffect!!, 1e-6)
        assertEquals(1.2, read.anaerobicTrainingEffect!!, 1e-6)
        assertEquals(3.0, read.elevationLossMeters)
        assertEquals(89 to 94, read.avgCadence to read.maxCadence)
        val power = read.power!!
        assertEquals(Triple(245, 290, 250), Triple(power.avgWatts, power.maxWatts, power.normalizedWatts))
        assertEquals(2000.0, power.workJoules)
        assertEquals(250, power.ftpWatts)
        val shifting = assertNotNull(read.shifting)
        assertEquals(1 to 1, shifting.frontShifts to shifting.rearShifts)
        val mtb = assertNotNull(read.mtb)
        assertEquals(4, mtb.jumps)
        assertEquals(39.6, mtb.grit!!, 1e-4)
        assertEquals(2.1, mtb.flow!!, 1e-4)
    }

    @Test
    fun a1xRidesRepeatedRearGearAddsNoFrontShift() = runTest {
        val oneBy = ride.copy(gearChanges = listOf(GearChange(2, null, 5), GearChange(4, null, 5), GearChange(6, null, 4)))
        val shifting = assertNotNull(reader.read(write(oneBy)).single().shifting)
        assertEquals(null to 3, shifting.frontShifts to shifting.rearShifts)
    }

    private fun Double.round7() = kotlin.math.round(this * 1e7) / 1e7

    @Test
    fun aRunsLapDetailsComeBack() = runTest {
        val read = reader.read(write(intervalRun.copy(points = ExportPoints.join(start, track, streams)))).single()
        // FIT always carries timer time: a lap stored without moving time comes back with its elapsed time.
        assertEquals(intervalRun.laps.map { it.copy(moving = it.moving ?: it.elapsed) }, read.streams?.laps)
    }

    @Test
    fun aPoolSwimsLengthsAndPoolLengthComeBack() = runTest {
        val read = reader.read(write(poolSwim)).single()
        assertEquals(ActivityType(Sport.SWIM, SubSport.POOL), read.type)
        assertEquals(2500, read.poolLengthCentimeters)
        assertEquals(poolSwim.lengths, read.streams?.lengths)
        assertEquals(poolSwim.laps, read.streams?.laps)
    }

    @Test
    fun aWorkoutsSetsComeBack() = runTest {
        val read = reader.read(write(benchWorkout)).single()
        assertEquals(benchWorkout.sets, read.streams?.sets)
    }

    @Test
    fun aTriathlonsLegsAndLapsComeBack() = runTest {
        val read = reader.read(write(triathlon)).single()
        assertEquals(ActivityType(Sport.MULTISPORT, SubSport.TRIATHLON), read.type)
        assertEquals(triathlon.legs, read.streams?.legs)
        assertEquals(triathlon.laps, read.streams?.laps)
    }
}
