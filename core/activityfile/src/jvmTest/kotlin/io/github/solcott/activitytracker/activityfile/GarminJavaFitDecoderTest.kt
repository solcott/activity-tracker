package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class GarminJavaFitDecoderTest {
    private val decoder = GarminJavaFitDecoder()
    private val start = Instant.parse("2026-09-20T07:00:00Z")

    @Test
    fun decodesARunWithGpsAndHeartRate() {
        val fit = decoder.decode(FitFixtures.run(start))

        assertEquals("ACTIVITY", fit.fileId?.type)
        assertEquals(1, fit.sessions.size)
        assertEquals(1, fit.laps.size)
        assertEquals(FitFixtures.RUN_POINTS, fit.records.size)
        val first = fit.records.first()
        assertEquals(start.toEpochMilliseconds(), first.timestampMillis)
        assertEquals(47.6, first.latitude!!, 1e-6)
        assertEquals(-122.3, first.longitude!!, 1e-6)
        assertEquals(120, first.heartRate)

        val activity = DecodedActivityMapper.map(fit)
        assertEquals(Sport.RUN, activity.type.sport)
        assertEquals(start, activity.startedAt)
        assertEquals(1000.0, activity.distanceMeters!!, 0.01)
        assertEquals(155, activity.avgHeartRate)
        assertEquals("Garmin Fenix 7", activity.device)
        assertEquals(FitFixtures.RUN_POINTS, activity.samples.size)
    }

    @Test
    fun decodesAnIndoorRideWithPowerAndNoGps() {
        val fit = decoder.decode(FitFixtures.indoorRide(start))

        assertTrue(fit.records.all { it.latitude == null })
        assertEquals(200, fit.records.first().powerWatts)

        val activity = DecodedActivityMapper.map(fit)
        assertEquals(ActivityType(Sport.RIDE, SubSport.INDOOR), activity.type, "the Java SDK's INDOOR_CYCLING sub-sport")
        assertTrue(activity.samples.isEmpty())
    }

    @Test
    fun decodesAPoolSwimsLengthsAndPoolLength() {
        val fit = decoder.decode(FitFixtures.poolSwim(start))

        assertEquals(25.0, fit.sessions.single().poolLengthMeters!!, 1e-6)
        assertEquals(FitFixtures.POOL_LENGTHS.size, fit.lengths.size)
        val first = fit.lengths.first()
        assertEquals(start.toEpochMilliseconds(), first.startTimeMillis)
        assertEquals(30.0, first.totalElapsedSeconds!!, 1e-6)
        assertEquals("FREESTYLE", first.swimStroke)
        assertEquals(15, first.totalStrokes)
        assertEquals("ACTIVE", first.lengthType)
        val idle = fit.lengths[4]
        assertEquals("IDLE", idle.lengthType)
        assertNull(idle.swimStroke)
        assertEquals(listOf("ACTIVE", "REST", "ACTIVE"), fit.laps.map { it.intensity })
    }

    @Test
    fun decodesAStrengthWorkoutsSetsWithTheirExerciseNames() {
        val fit = decoder.decode(FitFixtures.strengthWorkout(start))

        assertEquals(9, fit.sets.size)
        val bench = fit.sets.first()
        assertEquals("ACTIVE", bench.setType)
        assertEquals("BENCH_PRESS", bench.category)
        assertEquals("BARBELL_BENCH_PRESS", bench.exercise, "resolved through the bench press category")
        assertEquals(8, bench.repetitions)
        assertEquals(60.0, bench.weightKg!!, 1e-6)
        assertEquals(40.0, bench.durationSeconds!!, 1e-6)
        assertEquals("REST", fit.sets[1].setType)
        assertNull(fit.sets[1].category)
        assertEquals("PUSH_UP" to "PUSH_UP", fit.sets[6].category to fit.sets[6].exercise)
        assertNull(fit.sets[6].weightKg)
        assertEquals("SQUAT", fit.sets.last().category)
        assertNull(fit.sets.last().exercise)
    }

    @Test
    fun decodesASkiDaysRunAndLiftSplits() {
        val fit = decoder.decode(FitFixtures.resortSkiDay(start))

        assertEquals(FitFixtures.SKI_RUNS * 2, fit.splits.size)
        assertEquals("SKI_LIFT_SPLIT", fit.splits[0].splitType)
        assertEquals(300.0, fit.splits[0].totalAscentMeters!!, 1e-6)
        val run = fit.splits[1]
        assertEquals("SKI_RUN_SPLIT", run.splitType)
        assertEquals(start.toEpochMilliseconds() + 300_000, run.startTimeMillis)
        assertEquals(240.0, run.totalElapsedSeconds!!, 1e-6)
        assertEquals(300.0, run.totalDescentMeters!!, 1e-6)
        assertEquals(1500.0, run.totalDistanceMeters!!, 1e-6)
    }

    @Test
    fun decodesEveryLegOfATriathlon() {
        val fit = decoder.decode(FitFixtures.triathlon(start))

        assertEquals(listOf("SWIMMING", "TRANSITION", "CYCLING", "TRANSITION", "RUNNING"), fit.sessions.map { it.sport })
        assertEquals(9, fit.laps.size)
        assertEquals(List(5) { "DISTANCE" }, fit.laps.takeLast(5).map { it.lapTrigger })
    }

    @Test
    fun decodesHeartRateAStrapStoredInHrMessages() {
        val fit = decoder.decode(SensorFitFixtures.strapSwim(start))

        assertTrue(fit.records.all { it.heartRate == null })
        val anchor = fit.hrMessages.first()
        assertEquals(start.toEpochMilliseconds(), anchor.timestampMillis)
        assertEquals(listOf(1000.0), anchor.eventTimestamps)
        assertEquals(listOf(120), anchor.filteredBpm)
        assertTrue(fit.hrMessages.drop(1).all { it.timestampMillis == null })
        assertEquals(SensorFitFixtures.STRAP_SWIM_SECONDS.toInt() + 1, fit.hrMessages.sumOf { it.eventTimestamps.size })
        assertEquals(1001.0, fit.hrMessages[1].eventTimestamps.first(), 1e-3)
    }

    @Test
    fun decodesHrvActivityTimeDeveloperFieldsAndStrides() {
        val fit = decoder.decode(SensorFitFixtures.sensorRun(start))

        assertEquals(
            SensorFitFixtures.SENSOR_RUN_RR.map { (it * 1000).roundToLong() },
            fit.rrIntervalsSeconds.map { (it * 1000).roundToLong() },
        )
        val end = start + ((FitFixtures.RUN_POINTS - 1) * 30).seconds
        assertEquals(end.toEpochMilliseconds(), fit.activityTimestampMillis)
        assertEquals(end.epochSeconds - FIT_EPOCH_SECONDS + SensorFitFixtures.SENSOR_RUN_OFFSET_SECONDS, fit.activityLocalTimestampSeconds)
        assertEquals(SensorFitFixtures.SENSOR_RUN_STRIDES, fit.sessions.single().totalCycles)
        val fields = fit.records.first().developerFields.associateBy { it.name }
        assertEquals(setOf("Power", "Form Power", "Leg Spring Stiffness"), fields.keys)
        assertEquals("Watts", fields.getValue("Power").units)
        assertEquals(250.0, fields.getValue("Power").value!!, 1e-6)
        assertEquals(70.0, fields.getValue("Form Power").value!!, 1e-6)
        assertEquals("KN/m", fields.getValue("Leg Spring Stiffness").units)
        assertEquals(9.5, fields.getValue("Leg Spring Stiffness").value!!, 1e-6)
    }

    @Test
    fun decodesCyclingDynamicsPhysiologyAndEBikeFields() {
        val fit = decoder.decode(SensorFitFixtures.dynamicsRide(start))

        val first = fit.records.first()
        assertEquals(80.0, first.leftTorqueEffectiveness!!, 1e-6)
        assertEquals(76.5, first.rightTorqueEffectiveness!!, 1e-6)
        assertEquals(22.0, first.leftPedalSmoothness!!, 1e-6)
        assertEquals(20.5, first.rightPedalSmoothness!!, 1e-6)
        assertEquals(-2, first.leftPcoMm)
        assertEquals(3, first.rightPcoMm)
        assertEquals(38.25, first.coreTemperatureCelsius!!, 1e-6)
        assertEquals(65.5, first.saturatedHemoglobinPercent!!, 1e-6)
        assertEquals(12.34, first.totalHemoglobinConc!!, 1e-6)
        assertEquals(90, first.ebikeBatteryPercent)
        assertEquals(2, first.ebikeAssistMode)
        assertEquals(50, first.ebikeAssistLevelPercent)
        assertEquals(1500L, fit.sessions.single().totalCycles)
    }

    @Test
    fun aStrapSwimImportsWithHeartRate() {
        val activity = DecodedActivityMapper.map(decoder.decode(SensorFitFixtures.strapSwim(start)))

        val hr = activity.streams!!.heartRate!!
        assertTrue(hr.all { it != null }, "every record gets the strap's heart rate")
        assertEquals(120, hr.first())
        assertEquals(149, activity.maxHeartRate, "the last record averages its 10 s: 149 bpm until the 150 at 300 s")
        assertTrue(activity.avgHeartRate!! in 134..136)
    }

    @Test
    fun aSensorRunImportsItsHrvStepsOffsetAndStrydFields() {
        val activity = DecodedActivityMapper.map(decoder.decode(SensorFitFixtures.sensorRun(start)))

        assertEquals(20, activity.hrvRmssd)
        assertEquals((SensorFitFixtures.SENSOR_RUN_STRIDES * 2).toInt(), activity.cycles)
        assertEquals(120, activity.utcOffsetMinutes)
        val streams = activity.streams!!
        assertEquals(List(FitFixtures.RUN_POINTS - 1) { 250 } + 300, streams.power)
        assertEquals(70, streams.formPower!!.first())
        assertEquals(950, streams.legSpringStiffness!!.first())
    }

    @Test
    fun rejectsATruncatedFile() {
        val bytes = FitFixtures.run(start)
        assertFailsWith<ActivityFileException> { decoder.decode(bytes.copyOf(bytes.size - 20)) }
    }

    @Test
    fun rejectsAFileThatIsNotFit() {
        assertFailsWith<ActivityFileException> { decoder.decode("<gpx></gpx>".encodeToByteArray()) }
    }
}

private const val FIT_EPOCH_SECONDS = 631_065_600L
