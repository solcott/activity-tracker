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

    /**
     * Where a nudge of [meters] [later] or earlier moves the [start] or the end at [seconds], snapped as [startAt] and [endAt] snap. When
     * that leaves it where it was (samples further apart than the nudge), it goes to the next distance a sample reaches that way, so a
     * nudge always moves while there is somewhere to go.
     */
    @Suppress("ReturnCount") // Snapped there, else on to the next sample, else nowhere to go.
    fun nudge(seconds: Int, start: Boolean, later: Boolean, meters: Double): Int {
        val here = at(seconds)
        val target = (if (later) here + meters else here - meters).coerceIn(0.0, total)
        val snapped = if (start) startAt(target) else endAt(target)
        if (snapped != seconds) return snapped
        val d = series.distance ?: return seconds
        val next = if (later) d.firstOrNull { it > here } else d.lastOrNull { it < here }
        return next?.let { if (start) startAt(it) else endAt(it) } ?: seconds
    }

    companion object {
        /** [input]'s distance over time, or null when nothing says how far the athlete went. */
        fun of(input: TrimInput): TrimDistance? = input.series?.takeIf { it.distance != null && it.size >= 2 }?.let(::TrimDistance)
    }
}
