package io.github.solcott.activitytracker.activityfile

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Power, cadence, shifting and mountain bike dynamics for [DecodedActivityMapper]. As there, the device's summaries win and the samples
 * fill in what they leave out. Values that need the athlete's FTP (intensity factor, TSS) come only from the device.
 */
internal object RideMetrics {
    /** [records] are sorted and all have a timestamp. Null without a power meter. */
    fun power(sessions: List<DecodedSummary>, records: List<DecodedSample>): ImportedPower? {
        val seconds = records.powerBySecond()
        val power =
            ImportedPower(
                avgWatts =
                    sessions.weightedAverage { it.avgPowerWatts?.toDouble() }?.roundToInt()?.takeIf { it > 0 }
                        ?: seconds.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                maxWatts = sessions.mapNotNull { it.maxPowerWatts }.maxOrNull()?.takeIf { it > 0 } ?: seconds.maxOrNull(),
                // Normalized power doesn't add up across sessions, so several are worked out from the samples.
                normalizedWatts = sessions.singleOrNull()?.normalizedPowerWatts?.takeIf { it > 0 } ?: normalizedPower(seconds),
                workJoules =
                    sessions.total { it.totalWorkJoules }?.takeIf { it > 0 } ?: seconds.takeIf { it.isNotEmpty() }?.sum()?.toDouble(),
                ftpWatts = sessions.firstNotNullOfOrNull { it.thresholdPowerWatts }?.takeIf { it > 0 },
                intensityFactor = sessions.singleOrNull()?.intensityFactor?.takeIf { it > 0 },
                trainingStressScore = sessions.total { it.trainingStressScore }?.takeIf { it > 0 },
            )
        return power.takeIf { it.avgWatts != null || it.maxWatts != null }
    }

    /** Average and max; the average leaves out zeros (coasting), as Garmin's does. */
    fun cadence(sessions: List<DecodedSummary>, records: List<DecodedSample>): Pair<Int?, Int?> {
        val samples = records.mapNotNull { it.cadence }.filter { it > 0 }
        val avg = sessions.weightedAverage { it.avgCadence?.toDouble() }?.roundToInt()?.takeIf { it > 0 } ?: samples.averageOrNull()
        val max = sessions.mapNotNull { it.maxCadence }.maxOrNull()?.takeIf { it > 0 } ?: samples.maxOrNull()
        return avg to max
    }

    fun descentMeters(sessions: List<DecodedSummary>, records: List<DecodedSample>): Double? =
        sessions.total { it.totalDescentMeters } ?: records.mapNotNull { it.altitudeMeters?.unaryMinus() }.climb()

    /** Null without electronic shifting. [endMillis] is when the activity ended, which closes the last gear's time. */
    fun shifting(sessions: List<DecodedSummary>, changes: List<DecodedGearChange>, endMillis: Long?): ImportedShifting? {
        val front = sessions.count { it.frontShiftCount } ?: changes.count { !it.rear }.takeIf { it > 0 }
        val rear = sessions.count { it.rearShiftCount } ?: changes.count { it.rear }.takeIf { it > 0 }
        if (changes.isEmpty() && (front ?: 0) == 0 && (rear ?: 0) == 0) return null
        return ImportedShifting(
            frontShifts = front,
            rearShifts = rear,
            chainrings = changes.mapNotNull { it.frontTeeth }.filter { it > 0 }.distinct().sortedDescending(),
            cogs = changes.mapNotNull { it.rearTeeth }.filter { it > 0 }.distinct().sorted(),
            timeInGears = timeInGears(changes, endMillis),
        )
    }

    /** Null when the device recorded none of them. */
    fun mtb(sessions: List<DecodedSummary>, jumps: List<DecodedJump>): ImportedMtb? {
        val mtb =
            ImportedMtb(
                jumps = sessions.count { it.jumpCount } ?: jumps.size.takeIf { it > 0 },
                longestHangTimeSeconds = jumps.mapNotNull { it.hangTimeSeconds }.maxOrNull(),
                longestJumpMeters = jumps.mapNotNull { it.distanceMeters }.maxOrNull(),
                grit = sessions.total { it.totalGrit },
                flow = sessions.weightedAverage { it.avgFlow },
            )
        return mtb.takeUnless { it == ImportedMtb(null, null, null, null, null) }
    }

    /** Each rear gear holds from its change to the next; the last until [endMillis]. */
    private fun timeInGears(changes: List<DecodedGearChange>, endMillis: Long?): List<GearTime> {
        val timed =
            changes
                .filter { it.rear }
                .mapNotNull { change -> change.timestampMillis?.let { at -> change.rearGear?.let { Triple(at, it, change.rearTeeth) } } }
                .sortedBy { (at) -> at }
        val millis = mutableMapOf<Int, Long>()
        val teeth = mutableMapOf<Int, Int>()
        timed.forEachIndexed { i, (at, gear, rearTeeth) ->
            rearTeeth?.takeIf { it > 0 }?.let { teeth[gear] = it }
            val until = timed.getOrNull(i + 1)?.first ?: endMillis ?: return@forEachIndexed
            val held = until - at
            if (held > 0) millis[gear] = (millis[gear] ?: 0L) + held
        }
        return millis.entries.sortedBy { it.key }.map { (gear, ms) -> GearTime(gear, teeth[gear], (ms / MILLIS_PER_SECOND).roundToLong()) }
    }

    /**
     * Power for each second the athlete was recording: a sample holds until the next, but a gap longer than [MAX_HOLD_SECONDS] is a pause
     * (auto-pause or a dropout), so it counts once.
     */
    private fun List<DecodedSample>.powerBySecond(): List<Int> {
        val samples = mapNotNull { sample -> sample.powerWatts?.let { watts -> sample.timestampMillis?.let { it to watts } } }
        val seconds = mutableListOf<Int>()
        samples.forEachIndexed { i, (at, watts) ->
            val next = samples.getOrNull(i + 1)
            val gap = next?.let { (nextAt) -> ((nextAt - at) / MILLIS_PER_SECOND).roundToInt() } ?: 1
            repeat(if (gap in 1..MAX_HOLD_SECONDS) gap else 1) { seconds += watts }
        }
        return seconds
    }

    /** Coggan's normalized power: the 4th-power mean of 30 s rolling averages. Null for under 30 s of power. */
    private fun normalizedPower(seconds: List<Int>): Int? {
        if (seconds.size < NP_WINDOW_SECONDS) return null
        var window = seconds.take(NP_WINDOW_SECONDS).sum().toDouble()
        var sum = (window / NP_WINDOW_SECONDS).pow(NP_EXPONENT)
        for (i in NP_WINDOW_SECONDS until seconds.size) {
            window += seconds[i] - seconds[i - NP_WINDOW_SECONDS]
            sum += (window / NP_WINDOW_SECONDS).pow(NP_EXPONENT)
        }
        return (sum / (seconds.size - NP_WINDOW_SECONDS + 1)).pow(QUARTER).roundToInt().takeIf { it > 0 }
    }

    /** Null unless at least one session has the count. */
    private inline fun List<DecodedSummary>.count(value: (DecodedSummary) -> Int?): Int? =
        mapNotNull(value).takeIf { it.isNotEmpty() }?.sum()

    private fun List<Int>.averageOrNull(): Int? = takeIf { it.isNotEmpty() }?.average()?.roundToInt()

    private const val NP_WINDOW_SECONDS = 30
    private const val NP_EXPONENT = 4
    private const val QUARTER = 1.0 / NP_EXPONENT
    private const val MAX_HOLD_SECONDS = 5
    private const val MILLIS_PER_SECOND = 1000.0
}
