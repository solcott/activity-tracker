package io.github.solcott.activitytracker.data.streams

import io.github.solcott.activitytracker.activityfile.PerSecond
import io.github.solcott.activitytracker.data.activity.CombinedRoute
import kotlin.math.roundToInt

/** What the activity detail charts show, worked out from an activity's streams (`ActivityStreamData`). */
object StreamAnalytics {
    /** Durations a power curve is read at, in seconds: Garmin Connect's, then 3 h and 5 h for longer days. */
    val CURVE_SECONDS = listOf(1, 2, 5, 10, 20, 30, 60, 120, 300, 600, 1200, 1800, 3600, 7200, 10800, 18000)

    /** One value per second recorded, from samples at [time] seconds (see [PerSecond.hold]); null samples are skipped. */
    fun perSecond(time: List<Int>, values: List<Int?>): List<Int> = PerSecond.hold(samples(time, values))

    /** One value per second of clock time from the first sample to the last (see [PerSecond.clock]); a pause counts its length at 0. */
    fun clockSeconds(time: List<Int>, values: List<Int?>): List<Int> = PerSecond.clock(samples(time, values))

    private fun samples(time: List<Int>, values: List<Int?>) =
        time.indices.mapNotNull { i -> values.getOrNull(i)?.let { time[i].toDouble() to it } }

    /**
     * Seconds spent in each zone, lowest first, for zones starting at [lows] (ascending; each ends where the next begins). A value below
     * the first zone counts in it.
     */
    fun timeInZones(time: List<Int>, values: List<Int?>, lows: List<Int>): List<Int> {
        val seconds = IntArray(lows.size)
        if (lows.isEmpty()) return emptyList()
        for (value in perSecond(time, values)) seconds[(lows.indexOfLast { value >= it }).coerceAtLeast(0)]++
        return seconds.toList()
    }

    /**
     * The best average of [perSecond] over each of [durations] it's long enough for, as (seconds, average). A power curve passes
     * [clockSeconds], so no window joins efforts from either side of a pause.
     */
    fun meanMax(perSecond: List<Int>, durations: List<Int> = CURVE_SECONDS): List<Pair<Int, Int>> =
        durations
            .filter { it in 1..perSecond.size }
            .map { d ->
                var window = 0L
                for (i in 0 until d) window += perSecond[i]
                var best = window
                for (i in d until perSecond.size) {
                    window += perSecond[i] - perSecond[i - d]
                    if (window > best) best = window
                }
                d to (best / d).toInt()
            }

    /**
     * Grade in percent at each sample, from [distance] and [altitude] in centimeters: the climb over [GRADE_WINDOW_CM] centered on the
     * sample, which steadies GPS and barometer noise. Null where either is missing or the athlete hardly moved.
     */
    fun grade(distance: List<Int?>, altitude: List<Int?>): List<Double?> {
        val points =
            distance.indices.mapNotNull { i ->
                val d = distance[i] ?: return@mapNotNull null
                altitude.getOrNull(i)?.let { Triple(i, d, it) }
            }
        val grades = arrayOfNulls<Double>(distance.size)
        var from = 0
        var to = 0
        for ((index, at, _) in points) {
            while (from < points.size && points[from].second < at - GRADE_WINDOW_CM / 2) from++
            while (to + 1 < points.size && points[to + 1].second <= at + GRADE_WINDOW_CM / 2) to++
            val run = points[to].second - points[from].second
            if (run >= GRADE_WINDOW_CM / 2) {
                val rise = points[to].third - points[from].third
                grades[index] = (rise * PERCENT / run).coerceIn(-MAX_GRADE, MAX_GRADE)
            }
        }
        return grades.toList()
    }

    /**
     * At most about [max] points for a line chart: the mean of each run of samples, at the mean of their times so the line doesn't lead the
     * time axis, and a short spike still moves the line where every-nth thinning would drop it. Null samples are skipped; a run of only
     * nulls gives no point. A run never spans a gap longer than [gapLimit]: the first point after one is [ChartPoint.gapBefore], so the
     * chart breaks its line there and no point lands inside the gap.
     */
    fun averaged(time: List<Int>, values: List<Double?>, max: Int = MAX_CHART_POINTS): List<ChartPoint> {
        val step = ((time.size + max - 1) / max).coerceAtLeast(1)
        val limit = gapLimit(time.indices.mapNotNull { i -> values.getOrNull(i)?.let { time[i] } })
        val points = mutableListOf<ChartPoint>()
        val run = mutableListOf<Pair<Int, Double>>()
        var runLength = 0
        var gapBefore = false
        var lastAt: Int? = null
        fun flush() {
            if (run.isNotEmpty()) {
                points += ChartPoint(run.map { it.first }.average().roundToInt(), run.map { it.second }.average(), gapBefore)
                gapBefore = false
            }
            run.clear()
            runLength = 0
        }
        for (i in time.indices) {
            val value = values.getOrNull(i)
            val at = time[i]
            if (value != null && lastAt != null && at - lastAt > limit) {
                flush()
                gapBefore = true
            }
            if (value != null) {
                run += at to value
                lastAt = at
            }
            if (++runLength == step) flush()
        }
        flush()
        return points
    }

    /**
     * The longest stretch between samples at [times] (seconds, in order) that a chart joins with a line: the map's
     * [CombinedRoute.BREAK_SECONDS], or [GAP_INTERVALS] of the usual gap between samples when that's longer, so a stream logged every 30 s
     * or 60 s still draws as one line.
     */
    internal fun gapLimit(times: List<Int>): Int {
        val gaps = times.zipWithNext { a, b -> b - a }.filter { it > 0 }.sorted()
        return maxOf(CombinedRoute.BREAK_SECONDS, GAP_INTERVALS * (gaps.getOrNull(gaps.size / 2) ?: 0))
    }

    /** Every nth item so about [max] remain, keeping the last so a chart ends where the activity did. */
    fun <T> everyNth(items: List<T>, max: Int = MAX_CHART_POINTS): List<T> {
        if (items.size <= max) return items
        val step = (items.size + max - 1) / max
        val kept = items.filterIndexed { i, _ -> i % step == 0 }
        return if (kept.last() == items.last()) kept else kept + items.last()
    }

    /** About this many points draw a smooth line; a four-hour ride at one sample a second logs 14,400. */
    const val MAX_CHART_POINTS = 300

    private const val GAP_INTERVALS = 4
    private const val GRADE_WINDOW_CM = 5_000
    private const val PERCENT = 100.0
    private const val MAX_GRADE = 40.0
}

/** A point of a line chart: the mean [value] of a run of samples at the mean of their [seconds]; [gapBefore] when a gap precedes it. */
data class ChartPoint(val seconds: Int, val value: Double, val gapBefore: Boolean = false)
