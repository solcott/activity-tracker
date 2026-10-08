package io.github.solcott.activitytracker.activityfile

import kotlin.math.pow
import kotlin.math.roundToInt

/** Resampling of irregular samples (smart recording, dropouts) to one value per second. */
object PerSecond {
    /** The longest gap a sample holds across; a longer one is a pause (auto-pause or a dropout) and counts once. */
    const val MAX_HOLD_SECONDS = 5

    /**
     * One value for each second the athlete was recording, from (seconds, value) pairs in time order: each holds until the next, but a gap
     * longer than [MAX_HOLD_SECONDS] counts once.
     */
    fun hold(samples: List<Pair<Double, Int>>): List<Int> {
        val seconds = mutableListOf<Int>()
        samples.forEachIndexed { i, (at, value) ->
            val gap = samples.getOrNull(i + 1)?.let { (nextAt) -> (nextAt - at).roundToInt() } ?: 1
            repeat(if (gap in 1..MAX_HOLD_SECONDS) gap else 1) { seconds += value }
        }
        return seconds
    }

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
