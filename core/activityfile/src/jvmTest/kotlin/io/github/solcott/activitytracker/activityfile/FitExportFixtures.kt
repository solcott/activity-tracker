package io.github.solcott.activitytracker.activityfile

import com.garmin.fit.Decode
import com.garmin.fit.Mesg
import com.garmin.fit.MesgListener
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveGas
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveTank
import io.github.solcott.activitytracker.lexicon.app.DecoModel
import io.github.solcott.activitytracker.lexicon.app.DiveAlert
import io.github.solcott.activitytracker.lexicon.app.DiveAlertKind
import io.github.solcott.activitytracker.lexicon.app.GasSwitch
import io.github.solcott.activitytracker.lexicon.app.GearChange
import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.LapIntensity
import io.github.solcott.activitytracker.lexicon.app.LapTrigger
import io.github.solcott.activitytracker.lexicon.app.Leg
import io.github.solcott.activitytracker.lexicon.app.Length
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.SwimStroke
import io.github.solcott.activitytracker.lexicon.app.TrainingLoad
import io.github.solcott.activitytracker.lexicon.app.WaterType
import io.github.solcott.activitytracker.lexicon.app.WorkoutSet
import java.io.ByteArrayInputStream
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/** Activities to export as FIT, and Garmin's Java SDK to read the result. */
object FitExportFixtures {
    val start: Instant = Instant.parse("2026-09-20T07:00:00Z")

    /** 10 s of a road ride north from 47.6, -122.3 with every channel, and two shifts. */
    val ride =
        ExportActivity(
            name = "Morning Ride",
            sport = Sport.RIDE,
            subSport = SubSport.ROAD,
            tcxSport = TcxSport.Biking,
            startedAt = start,
            elapsed = 10.seconds,
            moving = 9.seconds,
            distanceMeters = 99.0,
            avgHeartRate = 125,
            maxHeartRate = 129,
            elevationGainMeters = 9.0,
            avgSpeedMps = 11.0,
            maxSpeedMps = 12.5,
            avgTemperatureCelsius = 18.0,
            isDive = false,
            points =
                (0 until 10).map {
                    ExportPoint(
                        time = start + it.seconds,
                        latitude = 47.6 + it * 0.0001,
                        longitude = -122.3,
                        altitudeMeters = 100.0 + it,
                        distanceMeters = it * 11.0,
                        speedMps = 11.0,
                        heartRate = 120 + it,
                        cadence = 85 + it,
                        powerWatts = 200 + it * 10,
                        temperatureCelsius = 18.0,
                    )
                },
            gearChanges = listOf(GearChange(time = 2, front = 2, rear = 5), GearChange(time = 6, front = 1, rear = 5)),
        )

    /** The same ride indoors: streams only, no positions. */
    val indoorRide =
        ride.copy(
            subSport = SubSport.INDOOR,
            points = ride.points.map { it.copy(latitude = null, longitude = null, altitudeMeters = null) },
            gearChanges = emptyList(),
        )

    /** A 2-minute closed-circuit dive to 30 m on air and 50% nitrox, with one tank. */
    val dive =
        ExportActivity(
            name = "Reef",
            sport = Sport.DIVE,
            subSport = SubSport.CCR,
            tcxSport = TcxSport.Other,
            startedAt = start,
            elapsed = 120.seconds,
            distanceMeters = null,
            avgHeartRate = null,
            maxHeartRate = null,
            isDive = true,
            points =
                (0..120 step 10).map {
                    ExportPoint(time = start + it.seconds, depthMeters = if (it in 1..119) 30.0 else 0.0, temperatureCelsius = 12.0)
                },
            dive =
                ActivityDive(
                    maxDepth = 3000,
                    avgDepth = 2500,
                    bottomTime = 90,
                    surfaceInterval = 3600,
                    diveNumber = 42,
                    closedCircuit = true,
                    gases = listOf(ActivityDiveGas(21, 0), ActivityDiveGas(50, 0)),
                    gradientFactorLow = 30,
                    gradientFactorHigh = 70,
                    startCns = 5,
                    endCns = 18,
                    otu = 25,
                    tanks = listOf(ActivityDiveTank(startPressure = 200_000, endPressure = 80_000, volumeUsed = 1200)),
                ),
        )

    /**
     * [dive] as a decompression dive: air, then 50% nitrox at 60 s and oxygen at 100 s (a gas the record doesn't list, as from a merged
     * file), two tanks with transmitters, deco state on every record, in salt water.
     */
    /** [decoDive] with its computer's settings and an alert of each sort: FIT names the first two, only UDDF the third. */
    val alertingDive by lazy {
        decoDive.copy(
            dive =
                decoDive.dive!!.copy(
                    decoModel = DecoModel.ZHL16C,
                    ppO2Warn = 1400,
                    ppO2Critical = 1600,
                    ppO2Deco = 1600,
                    lowSetpoint = 700,
                    highSetpoint = 1300,
                    safetyStopTime = 180,
                ),
            diveAlerts =
                listOf(
                    DiveAlert(60, DiveAlertKind.NDL_REACHED),
                    DiveAlert(90, DiveAlertKind.MICROBUBBLES),
                    DiveAlert(100, DiveAlertKind.SAFETY_STOP_COMPLETE),
                ),
        )
    }

    val decoDive =
        dive.copy(
            subSport = SubSport.MULTI_GAS,
            points =
                dive.points.mapIndexed { i, point ->
                    point.copy(
                        ppO2Bar = 0.2 + i * 0.1,
                        cnsPercent = 5 + i,
                        n2LoadPercent = 10 + i * 4,
                        ndlSeconds = if (i < 6) 600L - i * 100 else 0L,
                        nextStopDepthMeters = if (i in 6..11) 6.0 else null,
                        nextStopSeconds = if (i in 6..11) 60L else null,
                        ttsSeconds = 300L - i * 20,
                        tankPressuresBar = listOf(200.0 - i * 5, if (i < 6) null else 210.0 - (i - 6) * 10),
                    )
                },
            dive =
                dive.dive!!.copy(
                    closedCircuit = false,
                    tanks =
                        listOf(
                            ActivityDiveTank(startPressure = 200_000, endPressure = 140_000),
                            ActivityDiveTank(startPressure = 210_000, endPressure = 150_000),
                        ),
                    waterType = WaterType.SALT,
                    waterDensity = 1025,
                    startN2 = 10,
                    endN2 = 58,
                    descentTime = 20,
                    avgAscentRate = 150,
                    maxDescentRate = 400,
                ),
            gasSwitches = listOf(GasSwitch(0, 21, 0), GasSwitch(60, 50, 0), GasSwitch(100, 100, 0)),
        )

    /** [ride] as two laps of 5 s, with the record's power, calories and shifts and the zones record's training load. */
    val intervalRide =
        ride.copy(
            laps =
                listOf(
                    Lap(
                        start = 0,
                        elapsed = 5,
                        moving = 4,
                        distance = 44,
                        avgSpeed = 11_000,
                        avgHeartRate = 122,
                        avgPower = 220,
                        ascent = 4,
                    ),
                    Lap(start = 5, elapsed = 5, distance = 55, maxSpeed = 12_500, maxHeartRate = 129, avgCadence = 92, descent = 1),
                ),
            totals =
                ExportTotals(
                    elevationLossMeters = 3,
                    avgCadence = 89,
                    maxCadence = 94,
                    avgPower = 245,
                    maxPower = 290,
                    normalizedPower = 250,
                    work = 2,
                    calories = 300,
                    frontShifts = 1,
                    rearShifts = 1,
                    jumps = 4,
                    grit = 396,
                    flow = 21,
                ),
            trainingLoad =
                TrainingLoad(
                    ftp = 250,
                    intensityFactor = 84,
                    trainingStressScore = 1124,
                    aerobicTrainingEffect = 34,
                    anaerobicTrainingEffect = 12,
                    exerciseLoad = 187,
                    staminaStart = 100,
                    staminaEnd = 70,
                    staminaMin = 48,
                    sweatLoss = 764,
                ),
        )

    /** [intervalRide]'s laps as an interval run's: active and rest, with calories and what ended each. */
    val intervalRun =
        intervalRide.copy(
            sport = Sport.RUN,
            subSport = null,
            tcxSport = TcxSport.Running,
            laps =
                listOf(
                    intervalRide.laps[0].copy(calories = 12, intensity = LapIntensity.ACTIVE, trigger = LapTrigger.DISTANCE),
                    intervalRide.laps[1].copy(calories = 3, intensity = LapIntensity.REST, trigger = LapTrigger.MANUAL),
                ),
        )

    /** A point every [every] seconds of [seconds], with heart rate only. */
    private fun points(seconds: Int, every: Int = 10) =
        (0..seconds step every).map { ExportPoint(time = start + it.seconds, heartRate = 120 + it % 20) }

    const val POOL_LENGTHS = 40

    /**
     * 40 lengths of a 25 m pool: twenty of freestyle (30 s, 15 strokes), a 20 s rest at the wall, then nineteen of breaststroke (35 s, 12
     * strokes); its laps are the freestyle and the rest with the breaststroke.
     */
    val poolSwim: ExportActivity = run {
        var t = 0
        val lengths =
            (0 until POOL_LENGTHS).map { i ->
                when {
                    i < 20 -> Length(t, 30, SwimStroke.FREESTYLE, 15)
                    i == 20 -> Length(t, 20, rest = true)
                    else -> Length(t, 35, SwimStroke.BREASTSTROKE, 12)
                }.also { t += it.elapsed }
            }
        ExportActivity(
            name = "Pool Swim",
            sport = Sport.SWIM,
            subSport = SubSport.POOL,
            tcxSport = TcxSport.Other,
            startedAt = start,
            elapsed = t.seconds,
            distanceMeters = 975.0,
            avgHeartRate = 130,
            maxHeartRate = 139,
            isDive = false,
            points = points(t),
            laps = listOf(Lap(0, 600, moving = 600, distance = 500), Lap(600, t - 600, moving = t - 600, distance = 475)),
            lengths = lengths,
            poolLengthCentimeters = 2500,
        )
    }

    /** Three 40 s sets of 8 barbell bench presses at 60 kg, with 90 s rests between. */
    val benchWorkout: ExportActivity = run {
        fun bench(start: Int) = WorkoutSet(start, 40, category = "bench_press", exercise = "barbell_bench_press", reps = 8, weight = 60_000)
        val sets = listOf(bench(0), WorkoutSet(40, 90, rest = true), bench(130), WorkoutSet(170, 90, rest = true), bench(260))
        ExportActivity(
            name = "Strength",
            sport = Sport.STRENGTH,
            tcxSport = TcxSport.Other,
            startedAt = start,
            elapsed = 300.seconds,
            distanceMeters = null,
            avgHeartRate = 110,
            maxHeartRate = 139,
            isDive = false,
            points = points(300, every = 30),
            sets = sets,
        )
    }

    /** An Olympic triathlon of five legs, the run with five laps of 2 km. */
    val triathlon: ExportActivity = run {
        val legs =
            listOf(
                Leg(0, 1800, Sport.SWIM, SubSport.OPEN_WATER, moving = 1800, distance = 1500, avgHeartRate = 140),
                Leg(1800, 120, Sport.MULTISPORT, transition = true, moving = 120),
                Leg(1920, 3600, Sport.RIDE, SubSport.ROAD, moving = 3600, distance = 40_000, avgHeartRate = 150, maxHeartRate = 170),
                Leg(5520, 90, Sport.MULTISPORT, transition = true, moving = 90),
                Leg(5610, 2700, Sport.RUN, moving = 2700, distance = 10_000, avgHeartRate = 160, calories = 700),
            )
        val laps =
            listOf(
                Lap(0, 1800, moving = 1800, distance = 1500),
                Lap(1800, 120, moving = 120),
                Lap(1920, 3600, moving = 3600, distance = 40_000),
                Lap(5520, 90, moving = 90),
            ) + (0 until 5).map { Lap(5610 + it * 540, 540, moving = 540, distance = 2000, trigger = LapTrigger.DISTANCE) }
        ExportActivity(
            name = "Triathlon",
            sport = Sport.MULTISPORT,
            subSport = SubSport.TRIATHLON,
            tcxSport = TcxSport.Other,
            startedAt = start,
            elapsed = 8310.seconds,
            distanceMeters = 51_500.0,
            avgHeartRate = 150,
            maxHeartRate = 170,
            isDive = false,
            points = points(8310, every = 30),
            laps = laps,
            legs = legs,
        )
    }

    fun write(activity: ExportActivity): ByteArray = FitWriter.write(activity, TimeZone.UTC, createdAt = start)

    /** Every message in [bytes], after checking the file's integrity with Garmin's SDK. */
    fun decode(bytes: ByteArray): List<Mesg> {
        assertTrue(Decode().checkFileIntegrity(ByteArrayInputStream(bytes)), "integrity")
        val messages = mutableListOf<Mesg>()
        Decode().read(ByteArrayInputStream(bytes), MesgListener { messages += it })
        return messages
    }
}
