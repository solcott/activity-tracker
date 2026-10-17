package io.github.solcott.activitytracker.activityfile

import kotlin.math.pow
import kotlin.math.roundToInt

/** Resampling of irregular samples (smart recording, dropouts) to one value per second. */
object PerSecond {
    /**
     * The shortest gap limit: a sample holds across a gap up to this or [HOLD_INTERVALS] of the stream's median gap, whichever is longer; a
     * longer gap is a pause (auto-pause or a dropout) and counts once.
     */
    const val MAX_HOLD_SECONDS = 5

    /** How many of the stream's median gaps a sample holds across, so steady 10–30 s recording counts in full. */
    const val HOLD_INTERVALS = 2

    /** Fewer gaps than this say nothing about the recording interval (two samples 10 minutes apart are a pause), so 5 s holds. */
    private const val MIN_GAPS_FOR_INTERVAL = 3

    /**
     * One value for each second the athlete was recording, from (seconds, value) pairs in time order: each holds until the next, but a gap
     * longer than [MAX_HOLD_SECONDS] or [HOLD_INTERVALS] median gaps, whichever is longer, counts once. One-second and smart recording
     * (median 1–2 s) keep the 5 s limit; a file logged every 30 s holds across 60 s.
     */
    fun hold(samples: List<Pair<Double, Int>>): List<Int> {
        val gaps = samples.zipWithNext { (at), (nextAt) -> (nextAt - at).roundToInt() }
        val positive = gaps.filter { it > 0 }
        val interval = if (positive.size >= MIN_GAPS_FOR_INTERVAL) median(positive) else 0
        val limit = maxOf(MAX_HOLD_SECONDS, HOLD_INTERVALS * interval)
        val seconds = mutableListOf<Int>()
        samples.forEachIndexed { i, (_, value) ->
            val gap = gaps.getOrNull(i) ?: 1
            repeat(if (gap in 1..limit) gap else 1) { seconds += value }
        }
        return seconds
    }

    /** The middle of [values] (the upper one of an even count), or 0 when empty. */
    private fun median(values: List<Int>): Int = values.sorted().getOrElse(values.size / 2) { 0 }

    /** Coggan's normalized power from [seconds] of watts (see [hold]): the 4th-power mean of 30 s rolling averages. Null under 30 s. */
    fun normalizedPower(seconds: List<Int>): Int? {
        if (seconds.size < NP_WINDOW_SECONDS) return null
        var window = seconds.take(NP_WINDOW_SECONDS).sum().toDouble()
        var sum = (window / NP_WINDOW_SECONDS).pow(NP_EXPONENT)
        for (i in NP_WINDOW_SECONDS until seconds.size) {
            window += seconds[i] - seconds[i - NP_WINDOW_SECONDS]
            sum += (window / NP_WINDOW_SECONDS).pow(NP_EXPONENT)
        }
        return (sum / (seconds.size - NP_WINDOW_SECONDS + 1)).pow(QUARTER).roundToInt().takeIf { it > 0 }
    }

    private const val NP_WINDOW_SECONDS = 30
    private const val NP_EXPONENT = 4
    private const val QUARTER = 1.0 / NP_EXPONENT
}
