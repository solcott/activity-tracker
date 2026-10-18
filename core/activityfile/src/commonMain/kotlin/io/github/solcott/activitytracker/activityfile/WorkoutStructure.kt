package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.LapIntensity
import io.github.solcott.activitytracker.lexicon.app.LapTrigger
import io.github.solcott.activitytracker.lexicon.app.Leg
import io.github.solcott.activitytracker.lexicon.app.Length
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SwimStroke
import io.github.solcott.activitytracker.lexicon.app.WorkoutSet
import kotlin.math.roundToInt

/**
 * A file's laps, lengths, sets and legs in the lexicon's shapes and units. Times are seconds since the activity's start, from `seconds`.
 * Enum values arrive in any SDK's spelling ("POSITION_LAP", "positionLap", TCX's "Location"): known ones become the lexicon's tokens, and
 * others are kept, as camelCase for lap and stroke tokens and snake case for exercises (the FIT profile's own spelling).
 */
internal object WorkoutStructure {
    fun lap(lap: DecodedSummary, seconds: (Long) -> Int): Lap? {
        val start = lap.startTimeMillis?.let(seconds)
        val elapsed = (lap.totalElapsedSeconds ?: lap.totalTimerSeconds)?.roundToInt()
        if (start == null || elapsed == null) return null
        return Lap(
            start = start,
            elapsed = elapsed,
            moving = lap.totalTimerSeconds?.roundToInt(),
            distance = lap.totalDistanceMeters?.roundToInt(),
            avgSpeed = lap.avgSpeedMps?.times(MILLI)?.roundToInt(),
            maxSpeed = lap.maxSpeedMps?.times(MILLI)?.roundToInt(),
            avgHeartRate = lap.avgHeartRate,
            maxHeartRate = lap.maxHeartRate,
            avgPower = lap.avgPowerWatts,
            avgCadence = lap.avgCadence,
            ascent = lap.totalAscentMeters?.roundToInt(),
            descent = lap.totalDescentMeters?.roundToInt(),
            calories = lap.totalCalories?.takeIf { it > 0 },
            intensity = lap.intensity?.let(::intensity),
            trigger = lap.lapTrigger?.let(::trigger),
        )
    }

    /** A session of a multisport file as a leg; FIT's transition sport makes a transition, under the multisport token. */
    fun leg(session: DecodedSummary, seconds: (Long) -> Int): Leg? {
        val lap = lap(session, seconds) ?: return null
        val type = DecodedActivityMapper.activityType(session.sport, session.subSport)
        val transition = session.sport?.fitKey() == TRANSITION
        return Leg(
            start = lap.start,
            elapsed = lap.elapsed,
            sport = if (transition) Sport.MULTISPORT else type.sport,
            subSport = type.subSport.takeUnless { transition },
            transition = true.takeIf { transition },
            moving = lap.moving,
            distance = lap.distance,
            avgSpeed = lap.avgSpeed,
            maxSpeed = lap.maxSpeed,
            avgHeartRate = lap.avgHeartRate,
            maxHeartRate = lap.maxHeartRate,
            avgPower = lap.avgPower,
            avgCadence = lap.avgCadence,
            ascent = lap.ascent,
            descent = lap.descent,
            calories = lap.calories,
        )
    }

    /** A length; an idle one is a rest, with no stroke. */
    fun length(length: DecodedLength, seconds: (Long) -> Int): Length? {
        val start = length.startTimeMillis?.let(seconds)
        val elapsed = length.totalElapsedSeconds?.roundToInt()
        if (start == null || elapsed == null) return null
        val rest = length.lengthType?.fitKey() == "idle"
        return Length(
            start = start,
            elapsed = elapsed,
            stroke = length.swimStroke.takeUnless { rest }?.let(::stroke),
            strokes = length.totalStrokes.takeUnless { rest },
            rest = true.takeIf { rest },
        )
    }

    /** A set, starting [DecodedSet.durationSeconds] before its timestamp when it has no start time. Weight in grams. */
    fun set(set: DecodedSet, seconds: (Long) -> Int): WorkoutSet? {
        val duration = set.durationSeconds
        val startMillis =
            set.startTimeMillis ?: set.timestampMillis?.let { end -> duration?.let { end - (it * MILLIS_PER_SECOND).toLong() } }
        val start = startMillis?.let(seconds)
        val elapsed = duration?.roundToInt() ?: set.timestampMillis?.let(seconds)?.let { end -> start?.let { end - it } }
        if (start == null || elapsed == null) return null
        return WorkoutSet(
            start = start,
            elapsed = elapsed.coerceAtLeast(0),
            rest = true.takeIf { set.setType?.fitKey() == "rest" },
            category = set.category?.let(::snakeCase)?.takeUnless { it in UNSET },
            exercise = set.exercise?.let(::snakeCase)?.takeUnless { it in UNSET },
            reps = set.repetitions,
            weight = set.weightKg?.takeIf { it > 0 }?.times(MILLI)?.roundToInt(),
        )
    }

    /** A ski run or lift split as a lap, a lift as a rest; null for any other split. */
    fun skiLap(split: DecodedSplit): DecodedSummary? {
        val lift =
            when (split.splitType?.fitKey()) {
                "skiliftsplit" -> true
                "skirunsplit" -> false
                else -> return null
            }
        return DecodedSummary(
            sport = null,
            subSport = null,
            startTimeMillis = split.startTimeMillis,
            totalElapsedSeconds = split.totalElapsedSeconds,
            totalTimerSeconds = split.totalTimerSeconds,
            totalDistanceMeters = split.totalDistanceMeters,
            totalAscentMeters = split.totalAscentMeters,
            avgSpeedMps = split.avgSpeedMps,
            maxSpeedMps = split.maxSpeedMps,
            avgHeartRate = split.avgHeartRate,
            maxHeartRate = split.maxHeartRate,
            avgPowerWatts = null,
            avgCadence = null,
            avgTemperatureCelsius = null,
            totalDescentMeters = split.totalDescentMeters,
            totalCalories = split.totalCalories,
            intensity = if (lift) LapIntensity.REST else LapIntensity.ACTIVE,
        )
    }

    fun intensity(raw: String): String =
        when (val key = raw.fitKey()) {
            "resting" -> LapIntensity.REST
            in INTENSITIES -> INTENSITIES.getValue(key)
            else -> camelCase(raw)
        }

    fun trigger(raw: String): String =
        when (val key = raw.fitKey()) {
            "location",
            "positionstart",
            "positionlap",
            "positionwaypoint",
            "positionmarked" -> LapTrigger.POSITION
            in TRIGGERS -> TRIGGERS.getValue(key)
            else -> camelCase(raw)
        }

    fun stroke(raw: String): String = STROKES[raw.fitKey()] ?: camelCase(raw)

    /**
     * "BARBELL_BENCH_PRESS", "barbellBenchPress" and "WEIGHTED_45_DEGREE_PLANK", "weighted45DegreePlank" alike as the FIT profile spells
     * them.
     */
    fun snakeCase(raw: String): String = words(raw).joinToString("_")

    internal fun camelCase(raw: String): String =
        words(raw).mapIndexed { i, w -> if (i == 0) w else w.replaceFirstChar { it.uppercase() } }.joinToString("")

    /** Lowercase words, split at underscores, spaces and hyphens, camelCase humps, and where letters meet digits. */
    private fun words(raw: String): List<String> =
        raw.split('_', ' ', '-').flatMap { part -> WORD.findAll(part).map { it.value } }.map { it.lowercase() }.filter { it.isNotEmpty() }

    private val WORD = Regex("[A-Z]+(?![a-z])|[A-Z]?[a-z]+|[0-9]+")

    private val INTENSITIES =
        listOf(
                LapIntensity.ACTIVE,
                LapIntensity.REST,
                LapIntensity.WARMUP,
                LapIntensity.COOLDOWN,
                LapIntensity.RECOVERY,
                LapIntensity.INTERVAL,
            )
            .associateBy { it.fitKey() }

    private val TRIGGERS =
        listOf(
                LapTrigger.MANUAL,
                LapTrigger.TIME,
                LapTrigger.DISTANCE,
                LapTrigger.POSITION,
                LapTrigger.HEART_RATE,
                LapTrigger.SESSION_END,
                LapTrigger.FITNESS_EQUIPMENT,
            )
            .associateBy { it.fitKey() }

    private val STROKES =
        listOf(
                SwimStroke.FREESTYLE,
                SwimStroke.BACKSTROKE,
                SwimStroke.BREASTSTROKE,
                SwimStroke.BUTTERFLY,
                SwimStroke.DRILL,
                SwimStroke.MIXED,
                SwimStroke.IM,
            )
            .associateBy { it.fitKey() }

    /** The FIT profile's placeholders for a category or exercise that wasn't set. */
    private val UNSET = setOf("unknown", "invalid")

    /** FIT's transition sport, the time between multisport legs. */
    private const val TRANSITION = "transition"

    private const val MILLI = 1000.0
    private const val MILLIS_PER_SECOND = 1000.0
}
