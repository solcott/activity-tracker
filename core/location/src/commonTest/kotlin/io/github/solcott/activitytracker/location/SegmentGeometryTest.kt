package io.github.solcott.activitytracker.location

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SegmentGeometryTest {
    private val origin = GeoPoint(47.0, 8.0)
    private val metersPerDegreeLat = 6_371_000.0 * PI / 180
    private val metersPerDegreeLng = metersPerDegreeLat * cos(origin.lat * PI / 180)

    /** A point [east] and [north] meters from [origin]. */
    private fun at(east: Double, north: Double) = GeoPoint(origin.lat + north / metersPerDegreeLat, origin.lng + east / metersPerDegreeLng)

    /** A 1 km segment running north from [origin]. */
    private val straight = listOf(at(0.0, 0.0), at(0.0, 1000.0))

    /** A 1 km square loop, east then north then west then south, starting and ending at [origin]. */
    private val loop = listOf(at(0.0, 0.0), at(250.0, 0.0), at(250.0, 250.0), at(0.0, 250.0), at(0.0, 0.0))

    /**
     * A track along [path] (east, north waypoints in meters) at 5 m/s, a sample every [spacing] meters, each moved east by [offset] of its
     * distance along the path.
     */
    private fun track(path: List<Pair<Double, Double>>, spacing: Double = 5.0, offset: (Double) -> Double = { 0.0 }): List<TimedPoint> {
        val out = mutableListOf<TimedPoint>()
        var along = 0.0
        for ((a, b) in path.zipWithNext()) {
            val length = kotlin.math.hypot(b.first - a.first, b.second - a.second)
            var d = 0.0
            while (d < length) {
                val f = d / length
                val p = at(a.first + (b.first - a.first) * f + offset(along + d), a.second + (b.second - a.second) * f)
                out += TimedPoint(p.lat, p.lng, (along + d) / 5.0)
                d += spacing
            }
            along += length
        }
        val last = path.last()
        val p = at(last.first + offset(along), last.second)
        out += TimedPoint(p.lat, p.lng, along / 5.0)
        return out
    }

    private fun passesOver(line: List<GeoPoint>, piece: List<TimedPoint>) = SegmentGeometry.passes(piece, SegmentGeometry.resample(line))

    private fun assertNear(expected: Double, actual: Double, tolerance: Double = 0.5) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, was $actual")

    @Test
    fun polylineRoundTripsAtPrecision6() {
        val encoded = SegmentGeometry.encodePolyline(straight)
        val decoded = SegmentGeometry.decodePolyline(encoded)!!
        assertEquals(2, decoded.size)
        decoded.zip(straight).forEach { (a, b) -> assertTrue(SegmentGeometry.distanceMeters(a, b) < 0.2) }
        assertEquals(TrackMath.decodePolyline(SegmentGeometry.toPrecision5(straight)).size, 2)
        assertEquals(null, SegmentGeometry.decodePolyline("_p~iF~ps|U_"), "cut off mid-value")
        assertEquals(null, SegmentGeometry.decodePolyline("_p~iF~ps|U _"), "a character outside the format")
    }

    @Test
    fun lengthAndResample() {
        assertNear(1000.0, SegmentGeometry.lengthMeters(straight), 1.0)
        val ref = SegmentGeometry.resample(loop)
        assertEquals(101, ref.size)
        ref.zipWithNext { a, b -> assertNear(10.0, SegmentGeometry.distanceMeters(a, b), 0.1) }
        assertEquals(loop.last(), ref.last())
    }

    @Test
    fun onePassWithInterpolatedTimes() {
        val passes = passesOver(straight, track(listOf(0.0 to -100.0, 0.0 to 1100.0)))
        assertEquals(1, passes.size)
        assertNear(20.0, passes[0].start)
        assertNear(220.0, passes[0].end)
    }

    @Test
    fun twoLapsOfALoop() {
        val square = listOf(-50.0 to 0.0, 250.0 to 0.0, 250.0 to 250.0, 0.0 to 250.0, 0.0 to 0.0)
        val lap = square.drop(1).drop(1)
        val path = square + listOf(250.0 to 0.0) + lap + listOf(100.0 to 0.0)
        val passes = passesOver(loop, track(path))
        assertEquals(2, passes.size)
        assertNear(10.0, passes[0].start)
        assertNear(210.0, passes[0].end)
        assertNear(210.0, passes[1].start)
        assertNear(410.0, passes[1].end)
    }

    @Test
    fun reversedPassIsNone() {
        assertEquals(emptyList(), passesOver(straight, track(listOf(0.0 to 1100.0, 0.0 to -100.0))))
    }

    @Test
    fun shortcutIsNone() {
        // Leaves the line by 40 m between 400 and 600 m along it.
        val piece = track(listOf(0.0 to -100.0, 0.0 to 1100.0)) { d -> if (d in 500.0..700.0) 40.0 else 0.0 }
        assertEquals(emptyList(), passesOver(straight, piece))
    }

    @Test
    fun cornerCutBetweenSparseSamplesIsNone() {
        // An L-shaped segment, and a track with a sample every 120 m that sits on it either side of the corner but jumps straight across.
        val ell = listOf(at(0.0, 0.0), at(300.0, 0.0), at(300.0, 300.0))
        val onLine = track(listOf(-10.0 to 0.0, 300.0 to 0.0, 300.0 to 310.0), spacing = 120.0)
        assertEquals(1, passesOver(ell, onLine).size)
        val piece = onLine.filterIndexed { i, _ -> i != 3 }
        assertEquals(emptyList(), passesOver(ell, piece))
    }

    @Test
    fun jitterWithinTheCorridorMatches() {
        var i = 0
        val piece = track(listOf(0.0 to -100.0, 0.0 to 1100.0)) { if (i++ % 2 == 0) 10.0 else -10.0 }
        assertEquals(1, passesOver(straight, piece).size)
    }

    @Test
    fun jitterBeyondTheCorridorMidSegmentIsNone() {
        var i = 0
        val piece = track(listOf(0.0 to -100.0, 0.0 to 1100.0)) { d -> if (d in 500.0..700.0) (if (i++ % 2 == 0) 35.0 else -35.0) else 0.0 }
        assertEquals(emptyList(), passesOver(straight, piece))
    }

    @Test
    fun trackStartingMidSegmentIsNone() {
        assertEquals(emptyList(), passesOver(straight, track(listOf(0.0 to 500.0, 0.0 to 1100.0))))
    }

    @Test
    fun sparseSamplesInterpolateTheEndpoints() {
        // A sample every 40 m from 20 m before the start: the start falls halfway between two samples.
        val passes = passesOver(straight, track(listOf(0.0 to -20.0, 0.0 to 1100.0), spacing = 40.0))
        assertEquals(1, passes.size)
        assertNear(4.0, passes[0].start)
        assertNear(204.0, passes[0].end)
    }

    @Test
    fun e7RoundTripLeavesResultsUnchanged() {
        val piece = track(listOf(0.0 to -100.0, 0.0 to 1100.0)) { d -> if ((d / 5).toInt() % 3 == 0) 7.0 else -4.0 }
        val e7 = piece.map { TimedPoint((it.lat * 1e7).roundToLong() / 1e7, (it.lng * 1e7).roundToLong() / 1e7, it.t) }
        val exact = passesOver(straight, piece).map { it.start.roundToLong() to it.end.roundToLong() }
        val rounded = passesOver(straight, e7).map { it.start.roundToLong() to it.end.roundToLong() }
        assertEquals(exact, rounded)
    }
}
