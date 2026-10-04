package io.github.solcott.activitytracker.location

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

object TrackMath {
    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val DEGREES_PER_HALF_TURN = 180.0

    // Google encoded polyline format: 5 decimal digits, 5-bit chunks offset by '?' (63), 0x20 = "more chunks follow".
    private const val POLYLINE_SCALE = 1e5
    private const val CHUNK_BITS = 5
    private const val CHUNK_MASK = 0x1f
    private const val CONTINUATION_BIT = 0x20
    private const val ASCII_OFFSET = 63

    fun distanceMeters(a: LocationSample, b: LocationSample): Double {
        val dLat = (b.latitude - a.latitude).toRadians()
        val dLon = (b.longitude - a.longitude).toRadians()
        val h = sin(dLat / 2).pow(2) + cos(a.latitude.toRadians()) * cos(b.latitude.toRadians()) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /**
     * Ramer–Douglas–Peucker simplification with a tolerance in meters (equirectangular projection, fine at activity scale). Keeps feed
     * polylines small enough for a record.
     */
    fun simplify(
        points: List<LocationSample>,
        toleranceMeters: Double = 5.0,
    ): List<LocationSample> {
        if (points.size <= 2) return points
        val keep =
            BooleanArray(points.size).also {
                it[0] = true
                it[points.lastIndex] = true
            }
        val stack = ArrayDeque<Pair<Int, Int>>().apply { add(0 to points.lastIndex) }
        while (stack.isNotEmpty()) {
            val (start, end) = stack.removeLast()
            var maxDist = 0.0
            var index = -1
            for (i in start + 1 until end) {
                val d = perpendicularMeters(points[i], points[start], points[end])
                if (d > maxDist) {
                    maxDist = d
                    index = i
                }
            }
            if (index >= 0 && maxDist > toleranceMeters) {
                keep[index] = true
                stack.add(start to index)
                stack.add(index to end)
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    private fun perpendicularMeters(
        p: LocationSample,
        a: LocationSample,
        b: LocationSample,
    ): Double {
        val lat0 = a.latitude.toRadians()

        fun x(s: LocationSample) = s.longitude.toRadians() * cos(lat0) * EARTH_RADIUS_M

        fun y(s: LocationSample) = s.latitude.toRadians() * EARTH_RADIUS_M
        val dx = x(b) - x(a)
        val dy = y(b) - y(a)
        val len = sqrt(dx * dx + dy * dy)
        if (len == 0.0) return sqrt((x(p) - x(a)).pow(2) + (y(p) - y(a)).pow(2))
        return abs(dy * x(p) - dx * y(p) + x(b) * y(a) - y(b) * x(a)) / len
    }

    /** Google encoded polyline, precision 5. */
    fun encodePolyline(points: List<LocationSample>): String = buildString {
        var lastLat = 0L
        var lastLon = 0L
        for (p in points) {
            val lat = (p.latitude * POLYLINE_SCALE).roundToLong()
            val lon = (p.longitude * POLYLINE_SCALE).roundToLong()
            encodeValue(lat - lastLat)
            encodeValue(lon - lastLon)
            lastLat = lat
            lastLon = lon
        }
    }

    fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
        val result = mutableListOf<Pair<Double, Double>>()
        var index = 0
        var lat = 0L
        var lon = 0L

        fun next(): Long {
            var shift = 0
            var value = 0L
            while (true) {
                val b = encoded[index++].code - ASCII_OFFSET
                value = value or ((b and CHUNK_MASK).toLong() shl shift)
                shift += CHUNK_BITS
                if (b < CONTINUATION_BIT) break
            }
            return if (value and 1L != 0L) (value shr 1).inv() else value shr 1
        }
        while (index < encoded.length) {
            lat += next()
            lon += next()
            result += lat / POLYLINE_SCALE to lon / POLYLINE_SCALE
        }
        return result
    }

    private fun StringBuilder.encodeValue(value: Long) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= CONTINUATION_BIT) {
            append(((CONTINUATION_BIT or (v and CHUNK_MASK.toLong()).toInt()) + ASCII_OFFSET).toChar())
            v = v shr CHUNK_BITS
        }
        append((v + ASCII_OFFSET).toInt().toChar())
    }

    private fun Double.toRadians() = this * kotlin.math.PI / DEGREES_PER_HALF_TURN
}
