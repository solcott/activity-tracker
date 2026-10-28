package io.github.solcott.activitytracker.location

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How maps draw a line between its points (feed/route-line-detail): a centripetal Catmull-Rom curve through every stored point, so a
 * switchback reads as a turn rather than straight lines meeting at angles. Drawing only: nothing here is ever stored.
 */
object RouteCurve {
    /**
     * The furthest the curve may stray from the straight line between two points, in meters: as far as simplification let the track stray
     * from it, so the curve is never drawn where nobody rode.
     */
    const val MAX_BULGE_M = 2.0

    /** About how far apart the points added along a bending stretch are, in meters. */
    const val STEP_M = 2.0

    /** The most stretches a single stretch is drawn as, however long it is. */
    const val MAX_STEPS = 16

    /** A turn smaller than this, in radians, at both ends of a stretch leaves it straight. */
    private const val STRAIGHT = 3 * PI / 180

    private const val METERS_PER_DEGREE = 111_195.0

    /**
     * [points] (lat, lng pairs, one unbroken piece of a line) with points added along each stretch that bends, on a centripetal Catmull-Rom
     * curve through them, each stretch's offsets from its chord scaled down until none exceeds [MAX_BULGE_M]. Every point of [points]
     * stays, in order; straight stretches gain nothing.
     */
    fun smooth(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (points.size < MIN_POINTS) return points
        val (lat0, lng0) = points.first()
        val east = cos(lat0 * PI / 180) * METERS_PER_DEGREE
        val xy = points.map { (lat, lng) -> Xy((lng - lng0) * east, (lat - lat0) * METERS_PER_DEGREE) }
        val out = ArrayList<Pair<Double, Double>>(points.size * 2)
        out += points.first()
        for (i in 0 until xy.lastIndex) {
            val p1 = xy[i]
            val p2 = xy[i + 1]
            // The line's ends carry on straight, so its first and last stretches curve only into the turn at their inner end.
            val p0 = xy.getOrNull(i - 1) ?: (p1 * 2.0 - p2)
            val p3 = xy.getOrNull(i + 2) ?: (p2 * 2.0 - p1)
            for (q in between(p0, p1, p2, p3)) out += (lat0 + q.y / METERS_PER_DEGREE) to (lng0 + q.x / east)
            out += points[i + 1]
        }
        return out
    }

    /** The points drawn strictly between [p1] and [p2], with [p0] before and [p3] after; none when the stretch doesn't bend. */
    private fun between(p0: Xy, p1: Xy, p2: Xy, p3: Xy): List<Xy> {
        val length = (p2 - p1).length
        val steps = ceil(length / STEP_M).toInt().coerceIn(1, MAX_STEPS)
        val knots = listOf((p1 - p0).length, length, (p3 - p2).length).map(::sqrt)
        // A repeated point leaves the curve undefined; a stretch that doesn't bend needs no curve.
        val bends = turn(p0, p1, p2) >= STRAIGHT || turn(p1, p2, p3) >= STRAIGHT
        if (steps < 2 || knots.any { it < MIN_KNOT } || !bends) return emptyList()
        val fractions = (1 until steps).map { it.toDouble() / steps }
        val offsets = fractions.map { u -> catmullRom(p0, p1, p2, p3, knots, u) - (p1 + (p2 - p1) * u) }
        val bulge = offsets.maxOf { it.length }
        val scale = if (bulge > MAX_BULGE_M) MAX_BULGE_M / bulge else 1.0
        return fractions.zip(offsets) { u, offset -> p1 + (p2 - p1) * u + offset * scale }
    }

    /** How far the line turns at [b], from a→b to b→c, in radians. */
    private fun turn(a: Xy, b: Xy, c: Xy): Double {
        val d = atan2((c - b).y, (c - b).x) - atan2((b - a).y, (b - a).x)
        return abs(atan2(sin(d), cos(d)))
    }

    /** The centripetal Catmull-Rom curve between [p1] and [p2] at [u] (0 to 1), by Barry and Goldman's recurrence. */
    @Suppress("LongParameterList") // The four control points, their knot spacings and where along the stretch.
    private fun catmullRom(p0: Xy, p1: Xy, p2: Xy, p3: Xy, knots: List<Double>, u: Double): Xy {
        val t0 = 0.0
        val t1 = t0 + knots[0]
        val t2 = t1 + knots[1]
        val t3 = t2 + knots[2]
        val t = t1 + (t2 - t1) * u

        fun mix(a: Xy, b: Xy, ta: Double, tb: Double) = a * ((tb - t) / (tb - ta)) + b * ((t - ta) / (tb - ta))
        val a1 = mix(p0, p1, t0, t1)
        val a2 = mix(p1, p2, t1, t2)
        val a3 = mix(p2, p3, t2, t3)
        val b1 = mix(a1, a2, t0, t2)
        val b2 = mix(a2, a3, t1, t3)
        return mix(b1, b2, t1, t2)
    }

    /** A line needs a turn, so at least three points, to be drawn as anything but itself. */
    private const val MIN_POINTS = 3

    /** Below this square root of a stretch's length, in meters, its points count as one. */
    private const val MIN_KNOT = 1e-3

    /** A point in meters east ([x]) and north ([y]) of the line's first point. */
    private data class Xy(val x: Double, val y: Double) {
        operator fun plus(o: Xy) = Xy(x + o.x, y + o.y)

        operator fun minus(o: Xy) = Xy(x - o.x, y - o.y)

        operator fun times(k: Double) = Xy(x * k, y * k)

        val length: Double
            get() = hypot(x, y)
    }
}
