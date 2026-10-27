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

    /** The most seconds [hold] and [clock] give: a week, past any real activity, so untrusted times can't exhaust memory. */
    const val MAX_SECONDS = 7 * 24 * 60 * 60

    /** Fewer gaps than this say nothing about the recording interval (two samples 10 minutes apart are a pause), so 5 s holds. */
    private const val MIN_GAPS_FOR_INTERVAL = 3

    /**
     * One value for each second the athlete was recording, from (seconds, value) pairs in time order: each holds until the next, but a gap
     * longer than [MAX_HOLD_SECONDS] or [HOLD_INTERVALS] median gaps, whichever is longer, counts once. One-second and smart recording
     * (median 1–2 s) keep the 5 s limit; a file logged every 30 s holds across 60 s.
     */
    fun hold(samples: List<Pair<Double, Int>>): List<Int> = resample(samples, pauseValue = null)

    /**
     * One value for each second of clock time from the first sample to the last, as [hold] but with a pause counting its real length at 0:
     * what a power curve reads, so no window joins efforts from either side of a stop.
     */
    fun clock(samples: List<Pair<Double, Int>>): List<Int> = resample(samples, pauseValue = 0)

    /** [hold] when [pauseValue] is null; otherwise a pause's seconds after its first take [pauseValue]. */
    private fun resample(samples: List<Pair<Double, Int>>, pauseValue: Int?): List<Int> {
        val gaps = samples.zipWithNext { (at), (nextAt) -> (nextAt - at).roundToInt() }
        val positive = gaps.filter { it > 0 }
        val interval = if (positive.size >= MIN_GAPS_FOR_INTERVAL) median(positive) else 0
        val limit = maxOf(MAX_HOLD_SECONDS, HOLD_INTERVALS * interval)
        val seconds = mutableListOf<Int>()
        // Times come from files and other athletes' records, so a gap can be years: never more than MAX_SECONDS in all.
        fun add(count: Int, value: Int) = repeat(minOf(count, MAX_SECONDS - seconds.size)) { seconds += value }
        for ((i, sample) in samples.withIndex()) {
            if (seconds.size >= MAX_SECONDS) break
            val value = sample.second
            val gap = gaps.getOrNull(i) ?: 1
            when {
                gap in 1..limit -> add(gap, value)
                pauseValue != null && gap > limit -> {
                    add(1, value)
                    add(gap - 1, pauseValue)
                }
                else -> add(1, value)
            }
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
