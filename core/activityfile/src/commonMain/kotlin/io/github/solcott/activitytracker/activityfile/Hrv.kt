package io.github.solcott.activitytracker.activityfile

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** An activity's heart rate variability from its beat-to-beat (RR) intervals. */
object Hrv {
    /** Intervals outside 300–2000 ms (200 to 30 bpm) are a strap's dropouts or doubled beats. */
    private const val MIN_RR_MS = 300.0
    private const val MAX_RR_MS = 2000.0

    /** An interval more than this far from the last one kept is an artifact. */
    private const val MAX_CHANGE = 0.2

    /** Fewer intervals than this left give no summary. */
    const val MIN_INTERVALS = 30

    private const val MILLIS_PER_SECOND = 1000.0

    /**
     * The RMSSD of [rrSeconds], in whole milliseconds: the root mean square of successive differences, over the plausible intervals that
     * are within 20% of the last one kept. Null with fewer than [MIN_INTERVALS] left.
     */
    fun rmssd(rrSeconds: List<Double>): Int? {
        val kept = mutableListOf<Double>()
        for (rr in rrSeconds.map { it * MILLIS_PER_SECOND }) {
            if (rr < MIN_RR_MS || rr > MAX_RR_MS) continue
            val last = kept.lastOrNull()
            if (last == null || abs(rr - last) <= last * MAX_CHANGE) kept += rr
        }
        if (kept.size < MIN_INTERVALS) return null
        val squares = kept.zipWithNext { a, b -> (b - a) * (b - a) }
        return sqrt(squares.average()).roundToInt()
    }
}
