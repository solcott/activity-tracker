package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.LapIntensity
import io.github.solcott.activitytracker.lexicon.app.LapTrigger
import io.github.solcott.activitytracker.lexicon.app.Leg
import io.github.solcott.activitytracker.lexicon.app.Length
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.SwimStroke
import io.github.solcott.activitytracker.lexicon.app.WorkoutSet
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Assume.assumeTrue

/**
 * Writes sample activity files for checking by hand how imports, the activity page and exports show workout structure: a pool swim, a
 * strength workout, a triathlon, a ski day and a mountain bike ride as FIT, and an interval run, a duathlon and a run sampled every 30 s as
 * TCX. Three more FIT files, written with Garmin's encoder ([SensorFitFixtures]), carry sensor data: a pool swim whose heart rate is only
 * in `hr` messages, a run with HRV, Stryd developer fields and a UTC+2 clock, and an e-bike ride with cycling dynamics, CORE and Moxy
 * channels. A FIT dive from Garmin's encoder ([FitFixtures.dive]) carries alerts and computer settings, and a Shearwater dive and a
 * Subsurface logbook carry deco state and tank pressure. Only on request (see the activity-files rule): `./gradlew
 * :core-activityfile:jvmTest --tests "*.WriteWorkoutSamples" -PworkoutSamples` writes them to `build/workout-fixtures`, and without the
 * property it's skipped. Each sample is built from steady stretches, one value per second, and every lap, leg and summary average is taken
 * from the points written, so they agree; each file is read back through [ActivityFileReader] and checked before it's written.
 */
class WriteWorkoutSamples {
    private val reader = ActivityFileReader(GarminJavaFitDecoder())

    @Test
    fun writeSamples() = runTest {
        val dir = System.getProperty(DIR_PROPERTY)
        assumeTrue("Run with -PworkoutSamples to write the sample files", dir != null)
        val samples =
            listOf(
                "pool-swim.fit" to fit(poolSwim()),
                "strength.fit" to fit(strength()),
                "triathlon.fit" to fit(triathlon()),
                "ski-day.fit" to fit(skiDay()),
                "mtb-ride.fit" to fit(mtbRide()),
                "interval-run.tcx" to TcxWriter.write(intervalRun()).encodeToByteArray(),
                "duathlon.tcx" to duathlon().encodeToByteArray(),
                "sparse-run.tcx" to TcxWriter.write(sparseRun()).encodeToByteArray(),
                "strap-swim.fit" to SensorFitFixtures.strapSwim(START),
                "sensor-run.fit" to SensorFitFixtures.sensorRun(START),
                "dynamics-ride.fit" to SensorFitFixtures.dynamicsRide(START),
                "alert-dive.fit" to FitFixtures.dive(START),
                "shearwater-dive.xml" to shearwaterDive().encodeToByteArray(),
                "subsurface-dive.ssrf" to SubsurfaceFixtures.logbook(SubsurfaceFixtures.decoDive).encodeToByteArray(),
            )
        for ((name, bytes) in samples) checkConsistent(name, bytes)
        val out = File(dir).apply { mkdirs() }
        for ((name, bytes) in samples) File(out, name).writeBytes(bytes)
        println("Wrote ${samples.size} samples to $out")
    }

    /** Fails unless [bytes] import as one activity whose every lap, leg and summary average lies within its samples' range. */
    private suspend fun checkConsistent(name: String, bytes: ByteArray) {
        val activity = reader.read(bytes).single()
        val streams = assertNotNull(activity.streams, "$name streams")
        fun window(start: Int, elapsed: Int) = streams.time.indices.filter { streams.time[it] in start until start + elapsed }
        fun inRange(what: String, average: Int?, values: List<Int?>?, at: List<Int>) {
            val samples = at.mapNotNull { values?.getOrNull(it) }
            if (average == null || samples.isEmpty()) return
            assertTrue(average in samples.min()..samples.max(), "$name $what $average outside ${samples.min()}..${samples.max()}")
        }
        fun speedMatches(what: String, speed: Int?, distance: Int?, seconds: Int) {
            if (speed == null || distance == null) return
            val expected = distance * MILLI / seconds
            assertTrue(abs(speed - expected) <= expected * SPEED_TOLERANCE, "$name $what speed $speed for $distance m in $seconds s")
        }
        val all = streams.time.indices.toList()
        inRange("heart rate", activity.avgHeartRate, streams.heartRate, all)
        inRange("cadence", activity.avgCadence, streams.cadence, all)
        inRange("power", activity.power?.avgWatts, streams.power, all)
        streams.laps.orEmpty().forEachIndexed { i, lap ->
            val what = "lap ${i + 1}"
            val at = window(lap.start, lap.elapsed)
            if ((lap.distance ?: 0) > 0) assertNotNull(lap.avgSpeed, "$name $what speed")
            speedMatches(what, lap.avgSpeed, lap.distance, lap.moving ?: lap.elapsed)
            inRange("$what speed", lap.avgSpeed, streams.speed, at)
            inRange("$what heart rate", lap.avgHeartRate, streams.heartRate, at)
            inRange("$what power", lap.avgPower, streams.power, at)
            inRange("$what cadence", lap.avgCadence, streams.cadence, at)
        }
        streams.legs.orEmpty().forEachIndexed { i, leg ->
            val what = "leg ${i + 1}"
            val at = window(leg.start, leg.elapsed)
            speedMatches(what, leg.avgSpeed, leg.distance, leg.moving ?: leg.elapsed)
            inRange("$what speed", leg.avgSpeed, streams.speed, at)
            inRange("$what heart rate", leg.avgHeartRate, streams.heartRate, at)
            inRange("$what power", leg.avgPower, streams.power, at)
            inRange("$what cadence", leg.avgCadence, streams.cadence, at)
        }
    }

    /** 40 lengths of a 25 m pool: twenty of freestyle, a 20 s rest at the wall, then nineteen of breaststroke; a lap per stroke. */
    private fun poolSwim(): ExportActivity {
        val free = Stretch(30, POOL / 30.0, 125 to 140)
        val breast = Stretch(35, POOL / 35.0, 130 to 145)
        val stretches = List(20) { free } + Stretch(20, 0.0, 140 to 115) + List(19) { breast }
        // A swim watch records no speed: the pool length and the lengths give distance.
        val w = Workout(stretches, every = 5) { it.copy(speedMps = null) }
        var t = 0
        val lengths = stretches.map { s ->
            when {
                s.speed == 0.0 -> Length(t, s.seconds, rest = true)
                s === free -> Length(t, s.seconds, SwimStroke.FREESTYLE, 15)
                else -> Length(t, s.seconds, SwimStroke.BREASTSTROKE, 12)
            }.also { t += s.seconds }
        }
        return w.activity("Pool Swim", Sport.SWIM, SubSport.POOL, TcxSport.Other)
            .copy(laps = listOf(w.lap(0, 600), w.lap(600, w.seconds - 600)), lengths = lengths, poolLengthCentimeters = POOL * 100)
    }

    /** Three sets of 8 bench presses at 60 kg and two of 10 squats at 80 kg, 90 s rests between, heart rate only. */
    private fun strength(): ExportActivity {
        val set = Stretch(40, 0.0, 100 to 135)
        val rest = Stretch(90, 0.0, 135 to 105)
        val stretches = listOf(set, rest, set, rest, set, rest, set, rest, set)
        val w = Workout(stretches, every = 5) { it.copy(speedMps = null, distanceMeters = null) }
        val sets =
            (0 until stretches.size).map { i ->
                val start = i / 2 * 130 + if (i % 2 == 1) 40 else 0
                when {
                    i % 2 == 1 -> WorkoutSet(start, 90, rest = true)
                    i < 6 -> WorkoutSet(start, 40, category = "bench_press", exercise = "barbell_bench_press", reps = 8, weight = 60_000)
                    else -> WorkoutSet(start, 40, category = "squat", exercise = "barbell_back_squat", reps = 10, weight = 80_000)
                }
            }
        return w.activity("Strength", Sport.STRENGTH, null, TcxSport.Other).copy(distanceMeters = null, moving = null, sets = sets)
    }

    /** An Olympic-distance triathlon: 1.5 km swim, transition, 39.6 km ride in two laps, transition, 10 km run in five 2 km laps. */
    private fun triathlon(): ExportActivity {
        val w =
            Workout(
                listOf(
                    Stretch(1875, 0.8, 130 to 150),
                    Stretch(120, 1.0, 150 to 145),
                    Stretch(3600, 11.0, 140 to 160, power = 220, cadence = 90),
                    Stretch(90, 1.0, 160 to 150),
                    Stretch(2500, 4.0, 155 to 172, cadence = 88),
                ),
                every = 5,
                gps = true,
            )
        val laps =
            listOf(w.lap(0, 1875), w.lap(1875, 120), w.lap(1995, 1800), w.lap(3795, 1800), w.lap(5595, 90)) +
                (0 until 5).map { w.lap(5685 + it * 500, 500, trigger = LapTrigger.DISTANCE) }
        val legs =
            listOf(
                w.leg(0, 1875, Sport.SWIM, SubSport.OPEN_WATER),
                w.leg(1875, 120, Sport.MULTISPORT, transition = true),
                w.leg(1995, 3600, Sport.RIDE, SubSport.ROAD),
                w.leg(5595, 90, Sport.MULTISPORT, transition = true),
                w.leg(5685, 2500, Sport.RUN),
            )
        return w.activity("Triathlon", Sport.MULTISPORT, SubSport.TRIATHLON, TcxSport.Other).copy(laps = laps, legs = legs)
    }

    /** Four runs, each after a lift up: the lifts are rest laps (as a ski day's lift splits import). */
    private fun skiDay(): ExportActivity {
        val lift = Stretch(420, 2.5, 100 to 92, climb = 0.8)
        val run = Stretch(240, 10.0, 120 to 150, climb = -1.4)
        val w = Workout(List(4) { listOf(lift, run) }.flatten(), every = 2, gps = true)
        val laps =
            (0 until 8).map { i ->
                val start = i / 2 * 660 + if (i % 2 == 1) 420 else 0
                if (i % 2 == 0) w.lap(start, 420, intensity = LapIntensity.REST) else w.lap(start, 240, intensity = LapIntensity.ACTIVE)
            }
        return w.activity("Ski Day", Sport.SKI, null, TcxSport.Other).copy(laps = laps)
    }

    /** Three laps of a loop: a 10-minute climb at 260 W, then a 5-minute descent, with power and cadence every second. */
    private fun mtbRide(): ExportActivity {
        val climb = Stretch(600, 3.0, 140 to 168, power = 260, cadence = 72, climb = 0.15)
        val descent = Stretch(300, 8.0, 160 to 135, power = 60, cadence = 55, climb = -0.3)
        val w = Workout(List(3) { listOf(climb, descent) }.flatten(), every = 1, gps = true)
        val laps = (0 until 3).map { w.lap(it * 900, 900, trigger = LapTrigger.POSITION) }
        return w.activity("Mountain Bike Ride", Sport.RIDE, SubSport.MOUNTAIN, TcxSport.Biking).copy(laps = laps, totals = w.totals())
    }

    /**
     * A 10-minute flat warm-up, five 4-minute hill repeats (60 m up each) with 2-minute jogs back down, and a cool-down that climbs 30 m,
     * so pace and elevation rise together on the repeats and part on the cool-down.
     */
    private fun intervalRun(): ExportActivity {
        val hard = Stretch(240, 4.5, 150 to 175, cadence = 90, climb = 0.25)
        val jog = Stretch(120, 2.0, 170 to 140, cadence = 78, climb = -0.5)
        val stretches =
            listOf(Stretch(600, 2.8, 110 to 140, cadence = 80)) +
                List(5) { listOf(hard, jog) }.flatten() +
                Stretch(300, 2.5, 140 to 125, cadence = 78, climb = 0.1)
        val w = Workout(stretches, every = 2, gps = true)
        var t = 0
        val laps = stretches.map { s ->
            val intensity =
                when {
                    t == 0 -> LapIntensity.WARMUP
                    s === hard -> LapIntensity.ACTIVE
                    s === jog -> LapIntensity.REST
                    else -> LapIntensity.COOLDOWN
                }
            w.lap(t, s.seconds, intensity = intensity, trigger = if (s === hard) LapTrigger.TIME else LapTrigger.MANUAL).also {
                t += s.seconds
            }
        }
        return w.activity("Interval Run", Sport.RUN, null, TcxSport.Running).copy(laps = laps)
    }

    /** A 6 km run in 1 km laps at changing paces, sampled every 30 s as some watches record. */
    private fun sparseRun(): ExportActivity {
        val paces = listOf(2.5, 3.125, 4.0, 5.0, 3.125, 2.5)
        val stretches = paces.mapIndexed { i, speed ->
            Stretch((1000 / speed).toInt(), speed, (130 + i * 6) to (136 + i * 6), cadence = 84)
        }
        val w = Workout(stretches, every = 30, gps = true)
        var t = 0
        val laps = stretches.map { s -> w.lap(t, s.seconds, trigger = LapTrigger.DISTANCE).also { t += s.seconds } }
        return w.activity("Sparse Run", Sport.RUN, null, TcxSport.Running).copy(laps = laps)
    }

    /** A run, ride, run duathlon as a TCX `MultiSportSession`: each sport [TcxWriter]'s `Activity`, with a `Transition` between. */
    private fun duathlon(): String {
        val w =
            Workout(
                listOf(
                    Stretch(1250, 4.0, 140 to 165, cadence = 88),
                    Stretch(60, 1.0, 165 to 155),
                    Stretch(2000, 10.0, 145 to 160, power = 200, cadence = 88),
                    Stretch(45, 1.0, 160 to 150),
                    Stretch(800, 3.125, 160 to 175, cadence = 86),
                ),
                every = 2,
                gps = true,
            )
        // A sport (null for a transition), when it starts and its laps' lengths.
        val parts =
            listOf(
                Triple(TcxSport.Running, 0, listOf(625, 625)),
                Triple(null, 1250, listOf(60)),
                Triple(TcxSport.Biking, 1310, listOf(1000, 1000)),
                Triple(null, 3310, listOf(45)),
                Triple(TcxSport.Running, 3355, listOf(800)),
            )
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<TrainingCenterDatabase xmlns="${TcxWriter.TCX_NS}" xmlns:ns3="${TcxWriter.ACTIVITY_EXTENSION_NS}">""")
            append("<Activities><MultiSportSession><Id>$START</Id>")
            parts.forEachIndexed { index, (sport, start, lapSeconds) ->
                if (index == 0) append("<FirstSport>")
                if (sport == null) {
                    val lap = w.lap(start, lapSeconds.single())
                    append("""<NextSport><Transition StartTime="${START + start.seconds}">""")
                    append("<TotalTimeSeconds>${lap.elapsed}</TotalTimeSeconds><DistanceMeters>${lap.distance}</DistanceMeters>")
                    append("<Calories>0</Calories><AverageHeartRateBpm><Value>${lap.avgHeartRate}</Value></AverageHeartRateBpm>")
                    append("<Intensity>Active</Intensity><TriggerMethod>Manual</TriggerMethod>")
                    append("<Extensions><ns3:LX><ns3:AvgSpeed>${lap.avgSpeed!! / MILLI}</ns3:AvgSpeed></ns3:LX></Extensions></Transition>")
                    return@forEachIndexed
                }
                var t = start
                val laps = lapSeconds.map { s -> w.lap(t, s).also { t += s }.let { it.copy(start = it.start - start) } }
                val part =
                    w.activity("Duathlon", Sport.MULTISPORT, SubSport.DUATHLON, sport, start until start + lapSeconds.sum())
                        .copy(laps = laps)
                val xml = TcxWriter.write(part)
                append(xml.substring(xml.indexOf("<Activity "), xml.indexOf("</Activity>") + "</Activity>".length))
                append(if (index == 0) "</FirstSport>" else "</NextSport>")
            }
            append("</MultiSportSession></Activities></TrainingCenterDatabase>")
        }
    }

    /** A metric Shearwater dive into deco on one reporting transmitter, a second slot reading `AI is off`. */
    private fun shearwaterDive(): String =
        ShearwaterFixtures.dive(
            (0..30).map { i ->
                val seconds = i * 60
                val depth = if (i == 0 || i == 30) 0.0 else if (i < 15) 30.0 else 6.0
                val deco = i in 12..27
                ShearwaterFixtures.record(
                    seconds * 1000L,
                    depth,
                    20.0,
                    firstStopDepth = if (deco) "6" else "0",
                    tts = if (deco) "9" else "1",
                    ppO2 = if (i < 15) "0.84" else "1.2",
                    ndl = if (deco) "0" else "${(25 - i).coerceAtLeast(0)}",
                    tank0 = "${3000 - i * 60}",
                )
            },
            imperial = false,
            maxDepth = "30.0",
            maxTime = "1800",
        )

    private fun fit(activity: ExportActivity) = FitWriter.write(activity, TimeZone.UTC, createdAt = START)

    /**
     * [seconds] at a steady [speed] (m/s), heart rate drifting from [heartRate]'s first to its second, climbing [climb] m/s. Power and
     * cadence wobble a little.
     */
    private class Stretch(
        val seconds: Int,
        val speed: Double,
        val heartRate: Pair<Int, Int>,
        val power: Int? = null,
        val cadence: Int? = null,
        val climb: Double = 0.0,
    )

    /**
     * A workout of [stretches], a value a second, and the [points] a device sampling every [every] seconds writes ([sampled] dropping what
     * it doesn't record). Laps, legs and summaries average those points, and take distance and climb from the per-second values.
     */
    private class Workout(stretches: List<Stretch>, every: Int, gps: Boolean = false, sampled: (ExportPoint) -> ExportPoint = { it }) {
        val seconds = stretches.sumOf { it.seconds }
        private val perSecond = stretches.flatMap { s -> List(s.seconds) { i -> s to i } }
        private val distance = perSecond.runningFold(0.0) { meters, (s, _) -> meters + s.speed }
        private val altitude = perSecond.runningFold(START_ALTITUDE) { meters, (s, _) -> meters + s.climb }

        /** The second each point was taken, with it. */
        val points: List<Pair<Int, ExportPoint>> =
            (0 until seconds step every).map { t ->
                val (s, i) = perSecond[t]
                val (from, to) = s.heartRate
                val heartRate = from + (to - from) * i / s.seconds
                t to
                    sampled(
                        ExportPoint(
                            time = START + t.seconds,
                            latitude = if (gps) START_LATITUDE + distance[t] / METERS_PER_DEGREE else null,
                            longitude = if (gps) START_LONGITUDE else null,
                            altitudeMeters = if (gps) altitude[t] else null,
                            distanceMeters = distance[t],
                            speedMps = s.speed,
                            heartRate = heartRate,
                            cadence = s.cadence?.let { it + WOBBLE[t % WOBBLE.size] / 5 },
                            powerWatts = s.power?.let { it + WOBBLE[t % WOBBLE.size] },
                        )
                    )
            }

        private inner class Span(start: Int, elapsed: Int) {
            val at = points.filter { (t, _) -> t >= start && t < start + elapsed }.map { it.second }
            val distance = (this@Workout.distance[start + elapsed] - this@Workout.distance[start]).roundToInt()
            val moving = perSecond.subList(start, start + elapsed).count { it.first.speed > 0 }
            val speed = if (moving > 0 && distance > 0) (distance * MILLI / moving).roundToInt() else null
            val maxSpeed = at.mapNotNull { it.speedMps }.maxOrNull()?.let { (it * MILLI).roundToInt() }
            val heartRate = at.mapNotNull { it.heartRate }
            val power = at.mapNotNull { it.powerWatts }
            val cadence = at.mapNotNull { it.cadence }
            val climbs = (start until start + elapsed).map { altitude[it + 1] - altitude[it] }
            val ascent = climbs.filter { it > 0 }.sum().roundToInt()
            val descent = -climbs.filter { it < 0 }.sum().roundToInt()

            fun List<Int>.mean() = takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        }

        fun lap(start: Int, elapsed: Int, intensity: String? = null, trigger: String? = null): Lap =
            with(Span(start, elapsed)) {
                Lap(
                    start = start,
                    elapsed = elapsed,
                    moving = moving,
                    distance = distance.takeIf { it > 0 },
                    avgSpeed = speed,
                    maxSpeed = maxSpeed,
                    avgHeartRate = heartRate.mean(),
                    maxHeartRate = heartRate.maxOrNull(),
                    avgPower = power.mean(),
                    avgCadence = cadence.mean(),
                    ascent = ascent.takeIf { it > 0 },
                    descent = descent.takeIf { it > 0 },
                    intensity = intensity,
                    trigger = trigger,
                )
            }

        fun leg(start: Int, elapsed: Int, sport: String, subSport: String? = null, transition: Boolean? = null): Leg =
            with(Span(start, elapsed)) {
                Leg(
                    start = start,
                    elapsed = elapsed,
                    sport = sport,
                    subSport = subSport,
                    transition = transition,
                    moving = moving,
                    distance = distance.takeIf { it > 0 },
                    avgSpeed = speed,
                    maxSpeed = maxSpeed,
                    avgHeartRate = heartRate.mean(),
                    maxHeartRate = heartRate.maxOrNull(),
                    avgPower = power.mean(),
                    avgCadence = cadence.mean(),
                    ascent = ascent.takeIf { it > 0 },
                    descent = descent.takeIf { it > 0 },
                )
            }

        /** The record's power and cadence summary, from every point. */
        fun totals(): ExportTotals =
            with(Span(0, seconds)) {
                ExportTotals(
                    elevationLossMeters = descent.toLong(),
                    avgCadence = cadence.mean(),
                    maxCadence = cadence.maxOrNull(),
                    avgPower = power.mean(),
                    maxPower = power.maxOrNull(),
                )
            }

        /** The activity over the seconds of [window], with only the points in that time, which start it. */
        fun activity(
            name: String,
            sport: String,
            subSport: String?,
            tcxSport: TcxSport,
            window: IntRange = 0 until seconds,
        ): ExportActivity =
            with(Span(window.first, window.count())) {
                ExportActivity(
                    name = name,
                    sport = sport,
                    subSport = subSport,
                    tcxSport = tcxSport,
                    startedAt = START + window.first.seconds,
                    elapsed = window.count().seconds,
                    moving = moving.seconds,
                    distanceMeters = distance.toDouble(),
                    avgHeartRate = heartRate.mean(),
                    maxHeartRate = heartRate.maxOrNull(),
                    elevationGainMeters = ascent.toDouble().takeIf { it > 0 },
                    avgSpeedMps = speed?.div(MILLI),
                    maxSpeedMps = maxSpeed?.div(MILLI),
                    isDive = false,
                    points = at,
                )
            }
    }

    private companion object {
        const val DIR_PROPERTY = "workoutSamples"
        val START: Instant = Instant.parse("2026-09-20T07:00:00Z")
        const val POOL = 25
        const val MILLI = 1000.0
        const val SPEED_TOLERANCE = 0.01
        const val START_LATITUDE = 47.6
        const val START_LONGITUDE = -122.3
        const val START_ALTITUDE = 1500.0
        const val METERS_PER_DEGREE = 111_320.0
        val WOBBLE = listOf(-15, -5, 5, 15)
    }
}
