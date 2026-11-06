package io.github.solcott.activitytracker.location

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Terrain heights along a line, as segments/segment-elevation smooths and serves them: [smooth] before [ElevationGain] counts climbing, so
 * detail finer than the terrain can resolve doesn't add up, [even] for the evenly spaced profile a segment's chart draws, and [maxGrade]
 * for its steepest stretch.
 */
object ElevationProfile {
    /** How far either side of a sample, in meters, [smooth] averages over: a 50 m window. */
    const val SMOOTHING_HALF_WIDTH_M = 25.0

    /** How many heights a segment's profile holds, from its start to its end. */
    const val PROFILE_POINTS = 128

    /**
     * [heights], [spacingM] apart, each replaced by the mean of the heights within [halfWidthM] either side of it. Near either end the
     * window narrows evenly to what's there on both sides, so the first and last heights are kept and a straight climb comes back as it
     * was.
     */
    fun smooth(heights: List<Double>, spacingM: Double, halfWidthM: Double = SMOOTHING_HALF_WIDTH_M): List<Double> {
        val reach = (halfWidthM / spacingM).toInt()
        val last = heights.lastIndex
        return heights.indices.map { i ->
            val k = minOf(reach, i, last - i)
            var sum = 0.0
            for (j in i - k..i + k) sum += heights[j]
            sum / (2 * k + 1)
        }
    }

    /**
     * [count] heights in whole decimeters, evenly spaced from the first of [heights] to the last, each interpolated linearly between the
     * two heights either side of it. [atM] is how far along the line each of [heights] lies, in meters, rising from 0. Empty when there are
     * fewer than two heights.
     */
    fun even(heights: List<Double>, atM: List<Double>, count: Int = PROFILE_POINTS): List<Int> {
        require(heights.size == atM.size) { "one distance per height" }
        if (heights.size < 2 || count < 2) return emptyList()
        val length = atM.last()
        var below = 0
        return List(count) { j ->
            val at = length * j / (count - 1)
            while (below < heights.lastIndex - 1 && atM[below + 1] < at) below++
            val span = atM[below + 1] - atM[below]
            val f = if (span > 0) ((at - atM[below]) / span).coerceIn(0.0, 1.0) else 0.0
            ((heights[below] + (heights[below + 1] - heights[below]) * f) * DECIMETERS_PER_METER).roundToInt()
        }
    }

    /**
     * The steepest absolute grade along [heights], in percent, each measured between the farthest heights within [halfWidthM] behind and
     * ahead of a height, and at least its neighbours; near either end the window is cut short by the line. [atM] is how far along the line
     * each of [heights] lies, in meters, rising from 0, so a shorter last stretch counts as what it is. A descent counts as steep as the
     * same climb. Null when there are fewer than two heights.
     */
    fun maxGrade(heights: List<Double>, atM: List<Double>, halfWidthM: Double = SMOOTHING_HALF_WIDTH_M): Double? {
        require(heights.size == atM.size) { "one distance per height" }
        if (heights.size < 2) return null
        var behind = 0
        var ahead = 0
        var steepest = 0.0
        for (i in heights.indices) {
            while (atM[i] - atM[behind] > halfWidthM + WINDOW_SLACK_M) behind++
            ahead = maxOf(ahead, i)
            while (ahead < heights.lastIndex && atM[ahead + 1] - atM[i] <= halfWidthM + WINDOW_SLACK_M) ahead++
            // Heights farther apart than the window are measured against their neighbours.
            val from = minOf(behind, maxOf(i - 1, 0))
            val to = maxOf(ahead, minOf(i + 1, heights.lastIndex))
            val span = atM[to] - atM[from]
            if (span > 0) steepest = maxOf(steepest, abs(heights[to] - heights[from]) / span * PERCENT)
        }
        return steepest
    }

    private const val DECIMETERS_PER_METER = 10.0
    private const val PERCENT = 100.0

    /** Rounding in summed distances shouldn't drop a sample that is [SMOOTHING_HALF_WIDTH_M] away from the window. */
    private const val WINDOW_SLACK_M = 1e-6
}
