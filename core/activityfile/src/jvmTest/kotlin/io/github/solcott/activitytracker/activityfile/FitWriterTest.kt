package io.github.solcott.activitytracker.activityfile

import com.garmin.fit.ActivityMesg
import com.garmin.fit.BenchPressExerciseName
import com.garmin.fit.Bool
import com.garmin.fit.DisplayMeasure
import com.garmin.fit.DiveAlert
import com.garmin.fit.DiveGasMesg
import com.garmin.fit.DiveGasMode
import com.garmin.fit.DiveGasStatus
import com.garmin.fit.DiveSettingsMesg
import com.garmin.fit.DiveSummaryMesg
import com.garmin.fit.Event
import com.garmin.fit.EventMesg
import com.garmin.fit.EventType
import com.garmin.fit.ExerciseCategory
import com.garmin.fit.File
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Intensity
import com.garmin.fit.LapMesg
import com.garmin.fit.LapTrigger
import com.garmin.fit.LeftRightBalance
import com.garmin.fit.LengthMesg
import com.garmin.fit.LengthType
import com.garmin.fit.Mesg
import com.garmin.fit.MesgNum
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.SetMesg
import com.garmin.fit.SetType
import com.garmin.fit.Sport as GarminSport
import com.garmin.fit.SubSport as GarminSubSport
import com.garmin.fit.SwimStroke
import com.garmin.fit.TankSummaryMesg
import com.garmin.fit.TankUpdateMesg
import com.garmin.fit.TissueModelType
import com.garmin.fit.WaterType
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.alertingDive
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.benchWorkout
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.decoDive
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.decode
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.dive
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.indoorRide
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.intervalRide
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.intervalRun
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.poolSwim
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.ride
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.start
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.triathlon
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.write
import io.github.solcott.activitytracker.lexicon.app.DecoModel
import io.github.solcott.activitytracker.lexicon.app.GearChange
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class FitWriterTest {
    private inline fun <T> List<Mesg>.of(num: Int, wrap: (Mesg) -> T): List<T> = filter { it.num == num }.map(wrap)

    private val Float.round3: Double
        get() = kotlin.math.round(this * 1000.0) / 1000.0

    @Test
    fun rideIsACompletedActivityFile() {
        val messages = decode(write(ride))
        val fileId = messages.of(MesgNum.FILE_ID, ::FileIdMesg).single()
        assertEquals(File.ACTIVITY, fileId.type)
        val timers = messages.of(MesgNum.EVENT, ::EventMesg).filter { it.event == Event.TIMER }
        assertEquals(listOf(EventType.START, EventType.STOP_ALL), timers.map { it.eventType })
        assertEquals(listOf(start, start + 10.seconds), timers.map { it.timestamp.date.toInstant().toKotlinInstant() })
        val session = messages.of(MesgNum.SESSION, ::SessionMesg).single()
        val lap = messages.of(MesgNum.LAP, ::LapMesg).single()
        assertEquals(start, session.startTime.date.toInstant().toKotlinInstant())
        assertEquals(session.startTime.timestamp, lap.startTime.timestamp)
        assertEquals(1, session.numLaps)
        val activity = messages.of(MesgNum.ACTIVITY, ::ActivityMesg).single()
        assertEquals(1, activity.numSessions)
        assertEquals(session.timestamp.timestamp, activity.timestamp.timestamp)
        // Summary messages come last.
        assertEquals(MesgNum.ACTIVITY, messages.last().num)
    }

    @Test
    fun recordsCarryEveryChannel() {
        val records = decode(write(ride)).of(MesgNum.RECORD, ::RecordMesg)
        assertEquals(10, records.size)
        val third = records[2]
        val point = ride.points[2]
        assertEquals(point.latitude!!, FitUnits.semicirclesToDegrees(third.positionLat), 1e-7)
        assertEquals(point.longitude!!, FitUnits.semicirclesToDegrees(third.positionLong), 1e-7)
        assertEquals(102.0, third.enhancedAltitude.toDouble(), 0.2)
        assertEquals(22.0, third.distance.toDouble(), 0.01)
        assertEquals(11.0, third.enhancedSpeed.round3)
        assertEquals(122, third.heartRate.toInt())
        assertEquals(87, third.cadence.toInt())
        assertEquals(220, third.power)
        assertEquals(18, third.temperature.toInt())
        assertNull(third.depth)
        assertEquals(start + 2.seconds, third.timestamp.date.toInstant().toKotlinInstant())
    }

    @Test
    fun indoorRecordsHaveNoPosition() {
        val records = decode(write(indoorRide)).of(MesgNum.RECORD, ::RecordMesg)
        assertTrue(records.all { it.positionLat == null && it.positionLong == null })
        assertEquals(ride.points.map { it.powerWatts }, records.map { it.power })
    }

    @Test
    fun sessionCarriesTheSummaryAndSport() {
        val session = decode(write(ride)).of(MesgNum.SESSION, ::SessionMesg).single()
        assertEquals(GarminSport.CYCLING, session.sport)
        assertEquals(GarminSubSport.ROAD, session.subSport)
        assertEquals(10f, session.totalElapsedTime)
        assertEquals(9f, session.totalTimerTime)
        assertEquals(99f, session.totalDistance)
        assertEquals(9, session.totalAscent)
        assertEquals(11.0, session.enhancedAvgSpeed.round3)
        assertEquals(12.5, session.enhancedMaxSpeed.round3)
        assertEquals(125, session.avgHeartRate.toInt())
        assertEquals(129, session.maxHeartRate.toInt())
        assertEquals(18, session.avgTemperature.toInt())
    }

    @Test
    fun trailRunAndMovingTime() {
        val run = ride.copy(sport = Sport.RUN, subSport = SubSport.TRAIL, elapsed = 50.minutes, moving = 45.minutes)
        val session = decode(write(run)).of(MesgNum.SESSION, ::SessionMesg).single()
        assertEquals(GarminSport.RUNNING, session.sport)
        assertEquals(GarminSubSport.TRAIL, session.subSport)
        assertEquals(3000f, session.totalElapsedTime)
        assertEquals(2700f, session.totalTimerTime)
    }

    @Test
    fun aTypeFitLacksIsGeneric() {
        val session = decode(write(ride.copy(sport = Sport.GAMING, subSport = null))).of(MesgNum.SESSION, ::SessionMesg).single()
        assertEquals(GarminSport.GENERIC, session.sport)
    }

    @Test
    fun gearChangesAreEvents() {
        val gears = decode(write(ride)).of(MesgNum.EVENT, ::EventMesg).filter { it.eventType == EventType.MARKER }
        assertEquals(listOf(Event.REAR_GEAR_CHANGE, Event.FRONT_GEAR_CHANGE), gears.map { it.event })
        assertEquals(listOf(start + 2.seconds, start + 6.seconds), gears.map { it.timestamp.date.toInstant().toKotlinInstant() })
        assertEquals(listOf(2, 1), gears.map { it.frontGearNum.toInt() })
        assertEquals(listOf(5, 5), gears.map { it.rearGearNum.toInt() })
    }

    @Test
    fun aRepeatedRearGearOnA1xIsNoFrontShift() {
        val oneBy = ride.copy(gearChanges = listOf(GearChange(2, null, 5), GearChange(4, null, 5), GearChange(6, null, 4)))
        val gears = decode(write(oneBy)).of(MesgNum.EVENT, ::EventMesg).filter { it.eventType == EventType.MARKER }
        assertEquals(List(3) { Event.REAR_GEAR_CHANGE }, gears.map { it.event })
    }

    @Test
    fun twoPointsInOneSecondWriteTheFirst() {
        val first = ride.points[1]
        val second = first.copy(time = first.time + 400.milliseconds, latitude = 48.0)
        val records = decode(write(ride.copy(points = ride.points.take(2) + second + ride.points.drop(2)))).of(MesgNum.RECORD, ::RecordMesg)
        assertEquals(10, records.size)
        assertEquals(first.latitude!!, FitUnits.semicirclesToDegrees(records[1].positionLat), 1e-7)
    }

    @Test
    fun diveCarriesItsSettingsGasesTanksAndSummary() {
        val messages = decode(write(dive))
        val settings = messages.of(MesgNum.DIVE_SETTINGS, ::DiveSettingsMesg).single()
        assertEquals(30, settings.gfLow.toInt())
        assertEquals(70, settings.gfHigh.toInt())
        val gases = messages.of(MesgNum.DIVE_GAS, ::DiveGasMesg)
        assertEquals(listOf(21 to 0, 50 to 0), gases.map { it.oxygenContent.toInt() to it.heliumContent.toInt() })
        assertEquals(listOf(0, 1), gases.map { it.messageIndex })
        assertTrue(gases.all { it.status == DiveGasStatus.ENABLED && it.mode == DiveGasMode.CLOSED_CIRCUIT_DILUENT })
        val tank = messages.of(MesgNum.TANK_SUMMARY, ::TankSummaryMesg).single()
        assertEquals(200f, tank.startPressure)
        assertEquals(80f, tank.endPressure)
        assertEquals(1200f, tank.volumeUsed)
        val summary = messages.of(MesgNum.DIVE_SUMMARY, ::DiveSummaryMesg).single()
        assertEquals(MesgNum.SESSION, summary.referenceMesg)
        assertEquals(30f, summary.maxDepth)
        assertEquals(25f, summary.avgDepth)
        assertEquals(90f, summary.bottomTime)
        assertEquals(3600L, summary.surfaceInterval)
        assertEquals(42L, summary.diveNumber)
        assertEquals(5, summary.startCns.toInt())
        assertEquals(18, summary.endCns.toInt())
        assertEquals(25, summary.o2Toxicity)
        val session = messages.of(MesgNum.SESSION, ::SessionMesg).single()
        assertEquals(GarminSport.DIVING, session.sport)
        assertEquals(GarminSubSport.CCR_DIVING, session.subSport)
        assertEquals(30.0, messages.of(MesgNum.RECORD, ::RecordMesg).maxOf { it.depth.toDouble() }, 0.001)
    }

    @Test
    fun gasSwitchesAreEventsNamingTheirSlot() {
        val messages = decode(write(decoDive))
        val gases = messages.of(MesgNum.DIVE_GAS, ::DiveGasMesg)
        assertEquals(listOf(21, 50, 100), gases.map { it.oxygenContent.toInt() }, "the record's gases, then the one only a switch names")
        assertTrue(gases.all { it.mode == DiveGasMode.OPEN_CIRCUIT })
        val switches = messages.of(MesgNum.EVENT, ::EventMesg).filter { it.event == Event.DIVE_GAS_SWITCHED }
        assertEquals(listOf(0L, 1L, 2L), switches.map { it.data })
        assertEquals(
            listOf(start, start + 60.seconds, start + 100.seconds),
            switches.map { it.timestamp.date.toInstant().toKotlinInstant() },
        )
    }

    @Test
    fun aDivesComputerSettingsAreInItsDiveSettings() {
        val settings = decode(write(alertingDive)).of(MesgNum.DIVE_SETTINGS, ::DiveSettingsMesg).single()
        assertEquals(TissueModelType.ZHL_16C, settings.model)
        assertEquals(1.4f, settings.po2Warn)
        assertEquals(1.6f, settings.po2Critical)
        assertEquals(1.6f, settings.po2Deco)
        assertEquals(Bool.TRUE, settings.safetyStopEnabled)
        assertEquals(180, settings.safetyStopTime)
        assertEquals(0.7f, settings.ccrLowSetpoint)
        assertEquals(1.3f, settings.ccrHighSetpoint)

        val vpm = decode(write(alertingDive.copy(dive = alertingDive.dive!!.copy(decoModel = DecoModel.VPM_B))))
        val vpmSettings = vpm.of(MesgNum.DIVE_SETTINGS, ::DiveSettingsMesg).single()
        assertNull(vpmSettings.model, "FIT names only ZHL-16C")
        assertEquals(1.4f, vpmSettings.po2Warn)
        assertNull(decode(write(decoDive)).of(MesgNum.DIVE_SETTINGS, ::DiveSettingsMesg).single().safetyStopEnabled)
    }

    @Test
    fun alertsFitNamesAreDiveAlertEvents() {
        val alerts = decode(write(alertingDive)).of(MesgNum.EVENT, ::EventMesg).filter { it.event == Event.DIVE_ALERT }
        assertEquals(listOf(DiveAlert.NDL_REACHED, DiveAlert.SAFETY_STOP_COMPLETE), alerts.map { DiveAlert.getByValue(it.data.toShort()) })
        assertEquals(listOf(start + 60.seconds, start + 100.seconds), alerts.map { it.timestamp.date.toInstant().toKotlinInstant() })
    }

    @Test
    fun aDivesRecordsCarryItsDecoState() {
        val records = decode(write(decoDive)).of(MesgNum.RECORD, ::RecordMesg)
        val seventh = records[6]
        assertEquals(0.8, seventh.po2.toDouble(), 1e-6)
        assertEquals(11, seventh.cnsLoad.toInt())
        assertEquals(34, seventh.n2Load)
        assertEquals(0L, seventh.ndlTime)
        assertEquals(6.0, seventh.nextStopDepth.toDouble(), 1e-6)
        assertEquals(60L, seventh.nextStopTime)
        assertEquals(180L, seventh.timeToSurface)
        assertNull(records[0].nextStopDepth, "no stop before deco")
    }

    @Test
    fun eachTanksPressuresAreUpdatesFromItsOwnSensor() {
        val messages = decode(write(decoDive))
        val updates = messages.of(MesgNum.TANK_UPDATE, ::TankUpdateMesg)
        val bySensor = updates.groupBy({ it.sensor }, { it.pressure.toDouble() })
        assertEquals(setOf(1L, 2L), bySensor.keys)
        assertEquals(13, bySensor.getValue(1L).size)
        assertEquals(listOf(210.0, 200.0, 190.0, 180.0, 170.0, 160.0, 150.0), bySensor.getValue(2L), "only while the second tank reads")
        assertEquals(listOf(1L, 2L), messages.of(MesgNum.TANK_SUMMARY, ::TankSummaryMesg).map { it.sensor })
    }

    @Test
    fun aDivesWaterAndRatesAreInItsSettingsAndSummary() {
        val messages = decode(write(decoDive))
        val settings = messages.of(MesgNum.DIVE_SETTINGS, ::DiveSettingsMesg).single()
        assertEquals(WaterType.SALT, settings.waterType)
        assertEquals(1025f, settings.waterDensity)
        val summary = messages.of(MesgNum.DIVE_SUMMARY, ::DiveSummaryMesg).single()
        assertEquals(10, summary.startN2)
        assertEquals(58, summary.endN2)
        assertEquals(20f, summary.descentTime)
        assertEquals(0.15f, summary.avgAscentRate)
        assertEquals(0.4f, summary.maxDescentRate)
    }

    @Test
    fun storedLapsAreWrittenInsteadOfOneSpanningLap() {
        val messages = decode(write(intervalRide))
        val laps = messages.of(MesgNum.LAP, ::LapMesg)
        assertEquals(listOf(0, 1), laps.map { it.messageIndex })
        assertEquals(listOf(start, start + 5.seconds), laps.map { it.startTime.date.toInstant().toKotlinInstant() })
        assertEquals(listOf(5f, 5f), laps.map { it.totalElapsedTime })
        assertEquals(listOf(4f, 5f), laps.map { it.totalTimerTime })
        assertEquals(listOf(44f, 55f), laps.map { it.totalDistance })
        assertEquals(11.0, laps[0].enhancedAvgSpeed.round3)
        assertEquals(12.5, laps[1].enhancedMaxSpeed.round3)
        assertEquals(listOf(122, null), laps.map { it.avgHeartRate?.toInt() })
        assertEquals(listOf(220, null), laps.map { it.avgPower })
        assertEquals(listOf(null, 92), laps.map { it.avgCadence?.toInt() })
        assertEquals(listOf(4, null), laps.map { it.totalAscent })
        assertTrue(laps.all { it.lapTrigger == null && it.intensity == null }, "nothing the app didn't store")
        assertEquals(2, messages.of(MesgNum.SESSION, ::SessionMesg).single().numLaps)
    }

    @Test
    fun aLapsCaloriesIntensityAndTriggerAreWritten() {
        val laps = decode(write(intervalRun)).of(MesgNum.LAP, ::LapMesg)
        assertEquals(listOf(12, 3), laps.map { it.totalCalories })
        assertEquals(listOf(Intensity.ACTIVE, Intensity.REST), laps.map { it.intensity })
        assertEquals(listOf(LapTrigger.DISTANCE, LapTrigger.MANUAL), laps.map { it.lapTrigger })
        assertEquals(listOf(GarminSport.RUNNING, GarminSport.RUNNING), laps.map { it.sport })
    }

    @Test
    fun aPoolSwimCarriesItsPoolLengthAndLengths() {
        val messages = decode(write(poolSwim))
        val session = messages.of(MesgNum.SESSION, ::SessionMesg).single()
        assertEquals(25f, session.poolLength)
        assertEquals(DisplayMeasure.METRIC, session.poolLengthUnit)
        assertEquals(FitExportFixtures.POOL_LENGTHS, session.numLengths)
        assertEquals(FitExportFixtures.POOL_LENGTHS - 1, session.numActiveLengths)
        val lengths = messages.of(MesgNum.LENGTH, ::LengthMesg)
        assertEquals(FitExportFixtures.POOL_LENGTHS, lengths.size)
        assertEquals((0 until FitExportFixtures.POOL_LENGTHS).toList(), lengths.map { it.messageIndex })
        assertEquals(SwimStroke.FREESTYLE, lengths.first().swimStroke)
        assertEquals(15, lengths.first().totalStrokes)
        assertEquals(30f, lengths.first().totalElapsedTime)
        assertEquals(start, lengths.first().startTime.date.toInstant().toKotlinInstant())
        assertEquals(LengthType.IDLE, lengths[20].lengthType)
        assertNull(lengths[20].swimStroke)
        assertEquals(SwimStroke.BREASTSTROKE, lengths.last().swimStroke)
        val laps = messages.of(MesgNum.LAP, ::LapMesg)
        assertEquals(listOf(0, 20), laps.map { it.firstLengthIndex })
        assertEquals(listOf(20, 20), laps.map { it.numLengths })
        assertEquals(listOf(20, 19), laps.map { it.numActiveLengths })
    }

    @Test
    fun aYardPoolIsStatute() {
        val session = decode(write(poolSwim.copy(poolLengthCentimeters = 2286))).of(MesgNum.SESSION, ::SessionMesg).single()
        assertEquals(22.86, session.poolLength.toDouble(), 1e-4)
        assertEquals(DisplayMeasure.STATUTE, session.poolLengthUnit)
    }

    @Test
    fun aWorkoutsSetsAreSetMessages() {
        val sets = decode(write(benchWorkout)).of(MesgNum.SET, ::SetMesg)
        assertEquals(listOf(SetType.ACTIVE, SetType.REST, SetType.ACTIVE, SetType.REST, SetType.ACTIVE), sets.map { it.setType })
        val active = sets.filter { it.setType == SetType.ACTIVE }
        assertTrue(active.all { it.getCategory(0) == ExerciseCategory.BENCH_PRESS })
        assertTrue(active.all { it.getCategorySubtype(0) == BenchPressExerciseName.BARBELL_BENCH_PRESS })
        assertEquals(listOf(8, 8, 8), active.map { it.repetitions })
        assertEquals(listOf(60f, 60f, 60f), active.map { it.weight })
        assertEquals(listOf(40f, 90f, 40f, 90f, 40f), sets.map { it.duration })
        assertEquals(start + 40.seconds, sets[1].startTime.date.toInstant().toKotlinInstant())
        assertNull(sets[1].getCategory(0))
    }

    @Test
    fun aTriathlonHasASessionPerLeg() {
        val messages = decode(write(triathlon))
        val sessions = messages.of(MesgNum.SESSION, ::SessionMesg)
        assertEquals(
            listOf(GarminSport.SWIMMING, GarminSport.TRANSITION, GarminSport.CYCLING, GarminSport.TRANSITION, GarminSport.RUNNING),
            sessions.map { it.sport },
        )
        assertEquals(listOf(1800f, 120f, 3600f, 90f, 2700f), sessions.map { it.totalElapsedTime })
        assertEquals(listOf(1500f, null, 40_000f, null, 10_000f), sessions.map { it.totalDistance })
        assertEquals(listOf(0, 1, 2, 3, 4), sessions.map { it.firstLapIndex })
        assertEquals(listOf(1, 1, 1, 1, 5), sessions.map { it.numLaps })
        assertEquals(5, messages.of(MesgNum.ACTIVITY, ::ActivityMesg).single().numSessions)
        val laps = messages.of(MesgNum.LAP, ::LapMesg)
        assertEquals(List(5) { GarminSport.RUNNING }, laps.takeLast(5).map { it.sport }, "each lap with its leg's sport")
        assertEquals(GarminSport.TRANSITION, laps[1].sport)
    }

    @Test
    fun sessionCarriesTheRecordsTotalsAndTheTrainingLoad() {
        val session = decode(write(intervalRide)).of(MesgNum.SESSION, ::SessionMesg).single()
        assertEquals(300, session.totalCalories)
        assertEquals(89 to 94, session.avgCadence.toInt() to session.maxCadence.toInt())
        assertEquals(Triple(245, 290, 250), Triple(session.avgPower, session.maxPower, session.normalizedPower))
        assertEquals(2000L, session.totalWork)
        assertEquals(3, session.totalDescent)
        assertEquals(Triple(1, 1, 4), Triple(session.frontGearShiftCount, session.rearGearShiftCount, session.jumpCount))
        assertEquals(39.6f to 2.1f, session.totalGrit to session.avgFlow)
        assertEquals(250, session.thresholdPower)
        assertEquals(0.84f, session.intensityFactor)
        assertEquals(112.4f, session.trainingStressScore)
        assertEquals(3.4f, session.totalTrainingEffect)
        assertEquals(1.2f, session.totalAnaerobicTrainingEffect)
        assertEquals(187f, session.trainingLoadPeak)
        assertEquals(listOf(764L, 100L, 70L, 48L), listOf(178, 205, 206, 207).map { session.getFieldLongValue(it) })
        val lap = decode(write(ride)).of(MesgNum.LAP, ::LapMesg).single()
        assertNull(lap.totalCalories, "a ride without totals writes none")
    }

    @Test
    fun aRunsDynamicsAndBalanceAreRecordFields() {
        val run =
            ride.copy(
                sport = Sport.RUN,
                subSport = null,
                points =
                    ride.points.map {
                        it.copy(
                            respirationRate = 30.5,
                            verticalOscillationMm = 85.0,
                            stanceTimeMs = 245.5,
                            stepLengthMm = 1250.0,
                            verticalRatioPercent = 6.83,
                            stanceTimeBalancePercent = 49.75,
                            rightBalancePercent = 52,
                        )
                    },
            )
        val record = decode(write(run)).of(MesgNum.RECORD, ::RecordMesg).first()
        assertEquals(30.5f, record.enhancedRespirationRate)
        assertEquals(85f, record.verticalOscillation)
        assertEquals(245.5f, record.stanceTime)
        assertEquals(1250f, record.stepLength)
        assertEquals(6.83f, record.verticalRatio)
        assertEquals(49.75f, record.stanceTimeBalance)
        assertEquals(LeftRightBalance.RIGHT.toInt() or 52, record.leftRightBalance.toInt())
    }

    @Test
    fun aDiveWithoutBottomTimeWritesItsElapsedTime() {
        val logbookDive = dive.copy(dive = dive.dive!!.copy(bottomTime = null))
        val summary = decode(write(logbookDive)).of(MesgNum.DIVE_SUMMARY, ::DiveSummaryMesg).single()
        assertEquals(120f, summary.bottomTime)
    }

    @Test
    fun aRunHasNoDiveMessages() {
        val nums = decode(write(ride.copy(sport = Sport.RUN, subSport = null))).map { it.num }.toSet()
        listOf(MesgNum.DIVE_SETTINGS, MesgNum.DIVE_GAS, MesgNum.TANK_SUMMARY, MesgNum.DIVE_SUMMARY).forEach { assertTrue(it !in nums) }
    }

    private fun java.time.Instant.toKotlinInstant() = kotlin.time.Instant.fromEpochMilliseconds(toEpochMilli())
}
