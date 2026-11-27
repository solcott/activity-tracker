package io.github.solcott.activitytracker.data.activity.trim

import io.github.solcott.activitytracker.location.ElevationGain
import io.github.solcott.activitytracker.location.GainLoss
import io.github.solcott.activitytracker.location.MovingTime
import io.github.solcott.activitytracker.location.TrackMath

/**
 * An activity as samples in time order, for measuring what a trim keeps and cuts: seconds since the start, meters from the start, meters of
 * altitude and meters per second. From its streams when they say how far or how fast it went, else from its route.
 */
internal class TrimSeries(
    val time: List<Int>,
    /** Meters from the start at each sample, a missing sample carrying the last; null when neither the streams nor the route have it. */
    val distance: List<Double>?,
    val altitude: List<Double?>,
    val speed: List<Double?>,
) {
    val size: Int
        get() = time.size

    /** The last sample at or before [t], or -1 when none is. */
    fun indexAtOrBefore(t: Int): Int {
        var lo = 0
        var hi = time.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (time[mid] <= t) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }

    /** The first sample at or after [t], or [size] when none is. */
    fun indexAtOrAfter(t: Int): Int {
        val before = indexAtOrBefore(t)
        return if (before >= 0 && time[before] == t) before else before + 1
    }

    /** Meters from the start at [t]: the last sample's at or before it, 0 before the first. */
    fun distanceAt(t: Int): Double? = distance?.let { d -> indexAtOrBefore(t).let { if (it < 0) 0.0 else d[it] } }

    /** Seconds spent moving between [from] and [to], each step judged by its speed (or distance over time); a long gap is a pause. */
    fun movingSeconds(from: Int, to: Int): Double? {
        if (distance == null && speed.all { it == null }) return null
        val first = indexAtOrAfter(from)
        val last = indexAtOrBefore(to)
        var moving = 0.0
        for (i in first until last) {
            val dt = time[i + 1] - time[i]
            if (dt <= 0 || dt > gapLimit) continue
            if (isMoving(i) == true) moving += dt
        }
        return moving
    }

    /**
     * Whether the step from sample [i] to the next was moving: by the distance covered when there's distance (an instant's speed at either
     * end would count a step half stopped), else by the speed at its end. Null when nothing says, or across a pause.
     */
    @Suppress("ReturnCount") // Moving, standing, or nothing says.
    fun isMoving(i: Int): Boolean? {
        val dt = time[i + 1] - time[i]
        if (dt <= 0 || dt > gapLimit) return null
        val v = distance?.let { (it[i + 1] - it[i]) / dt } ?: speed[i + 1] ?: return null
        return MovingTime.isMoving(v)
    }

    /** Meters per second over the step from sample [i] to the next, or null when nothing says. */
    fun speedOver(i: Int): Double? {
        val dt = time[i + 1] - time[i]
        if (dt <= 0) return null
        return distance?.let { (it[i + 1] - it[i]) / dt } ?: speed[i + 1]
    }

    /** Elevation gain and loss between [from] and [to], counted as the activity's are; null without two altitudes. */
    fun gainLoss(from: Int, to: Int): GainLoss? {
        val first = indexAtOrAfter(from)
        val last = indexAtOrBefore(to)
        if (last <= first) return null
        return ElevationGain.of(altitude.subList(first, last + 1))
    }

    /** Longer than this between two samples is a pause, not time spent moving or standing: twice the usual interval, at least 10 s. */
    private val gapLimit: Int by lazy {
        val gaps = time.zipWithNext { a, b -> b - a }.filter { it > 0 }.sorted()
        maxOf(MIN_GAP_SECONDS, 2 * (gaps.getOrNull(gaps.size / 2) ?: 0))
    }

    companion object {
        private const val MIN_GAP_SECONDS = 10
        private const val CM_PER_M = 100.0
        private const val MM_PER_M = 1000.0

        /** [input] as samples, or null when it has neither streams nor a route. */
        @Suppress("CyclomaticComplexMethod", "ReturnCount") // Streams, else the route, else time alone.
        fun of(input: TrimInput): TrimSeries? {
            val streams = input.streams
            val fromStreams = streams?.takeIf { it.time.isNotEmpty() && (it.distance != null || it.speed != null || it.altitude != null) }
            if (fromStreams != null && (fromStreams.distance != null || input.route.size < 2)) {
                val distance =
                    fromStreams.distance?.let { cm ->
                        var last = 0.0
                        cm.map { value -> (value?.div(CM_PER_M) ?: last).also { last = it } }
                    }
                return TrimSeries(
                    time = fromStreams.time,
                    distance = distance,
                    altitude = fromStreams.altitude?.map { it?.div(CM_PER_M) } ?: List(fromStreams.time.size) { null },
                    speed = fromStreams.speed?.map { it?.div(MM_PER_M) } ?: List(fromStreams.time.size) { null },
                )
            }
            val route = input.route
            if (route.size >= 2) {
                var total = 0.0
                val distance =
                    route.indices.map { i ->
                        if (i > 0) total += TrackMath.distanceMeters(route[i - 1], route[i])
                        total
                    }
                return TrimSeries(
                    time = route.map { (it.time - input.startedAt).inWholeSeconds.toInt() },
                    distance = distance,
                    altitude = route.map { it.altitude },
                    speed = route.map { it.speed },
                )
            }
            return streams
                ?.takeIf { it.time.isNotEmpty() }
                ?.let { s ->
                    TrimSeries(
                        s.time,
                        null,
                        s.altitude?.map { it?.div(CM_PER_M) } ?: List(s.time.size) { null },
                        List(s.time.size) { null },
                    )
                }
        }
    }
}
