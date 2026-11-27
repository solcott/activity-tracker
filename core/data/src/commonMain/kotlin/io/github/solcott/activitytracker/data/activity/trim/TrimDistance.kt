package io.github.solcott.activitytracker.data.activity.trim

/**
 * Where a trim by distance lands in time (feed/activity-trim). A start at a distance keeps from the last sample at or before it, and an end
 * from the first sample at or after it, so the cut also takes any standing time at that point: standing adds no distance.
 */
class TrimDistance private constructor(private val series: TrimSeries) {
    /** Meters from the start at the end of the recording. */
    val total: Double
        get() = series.distance?.lastOrNull() ?: 0.0

    /** Meters from the start at [seconds]. */
    fun at(seconds: Int): Double = series.distanceAt(seconds) ?: 0.0

    /** The second a kept span starting [meters] from the start begins at. */
    fun startAt(meters: Double): Int {
        val d = series.distance ?: return 0
        var index = 0
        while (index < d.size - 1 && d[index + 1] <= meters) index++
        return series.time[index].coerceAtLeast(0)
    }

    /** The second a kept span ending [meters] from the start ends at. */
    fun endAt(meters: Double): Int {
        val d = series.distance ?: return series.time.last()
        var index = d.size - 1
        while (index > 0 && d[index - 1] >= meters) index--
        return series.time[index]
    }

    companion object {
        /** [input]'s distance over time, or null when nothing says how far the athlete went. */
        fun of(input: TrimInput): TrimDistance? = TrimSeries.of(input)?.takeIf { it.distance != null && it.size >= 2 }?.let(::TrimDistance)
    }
}
