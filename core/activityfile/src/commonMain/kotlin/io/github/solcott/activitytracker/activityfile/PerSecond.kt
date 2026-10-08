package io.github.solcott.activitytracker.activityfile

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
}
