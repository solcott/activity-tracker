package io.github.solcott.activitytracker.location

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

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

    private fun sample(east: Double, north: Double, t: Double) = at(east, north).let { TimedPoint(it.lat, it.lng, t) }

    /** [count] samples a second apart heading north at [speed] along [east], from [north] at [t]. */
    private fun rideNorth(east: Double, north: Double, t: Double, count: Int, speed: Double = 5.0) =
        (0 until count).map { sample(east, north + it * speed, t + it) }

    @Test
    fun stopShortOfTheStartDoesNotCount() {
        // Waits 1 m short of the start, 7 m to the side, for 144 s; its closest sample is early in the wait.
        val piece =
            rideNorth(7.0, -100.0, 0.0, 20) +
                sample(6.5, -0.5, 20.0) +
                (21..163).map { sample(if (it % 2 == 0) 7.3 else 6.7, -1.0, it.toDouble()) } +
                rideNorth(7.0, -1.0, 164.0, 210)
        val passes = passesOver(straight, piece)
        assertEquals(1, passes.size)
        assertNear(164.2, passes[0].start)
        assertNear(364.2, passes[0].end)
    }

    @Test
    fun recordingGapAcrossTheStartLineDoesNotCount() {
        // The last sample before a 144 s gap is 8 m short of the start, the first after it 7 m past.
        val piece = rideNorth(7.0, -100.0, 0.0, 19) + sample(7.0, -8.0, 19.0) + rideNorth(7.0, 7.0, 163.0, 210)
        val passes = passesOver(straight, piece)
        assertEquals(1, passes.size)
        assertNear(163.0, passes[0].start)
        assertNear(361.6, passes[0].end)
    }

    @Test
    fun stopJustPastTheStartCounts() {
        val piece = rideNorth(0.0, -100.0, 0.0, 22) + (22..81).map { sample(0.0, 8.0, it.toDouble()) } + rideNorth(0.0, 8.0, 82.0, 210)
        val passes = passesOver(straight, piece)
        assertEquals(1, passes.size)
        assertNear(20.0, passes[0].start)
        assertNear(280.4, passes[0].end)
    }

    @Test
    fun stopAfterTheEndLineDoesNotCount() {
        // Crosses the end line between 997 m and 1002 m, then drifts back toward the end while standing.
        val piece =
            rideNorth(0.0, -103.0, 0.0, 222) +
                listOf(1001.5, 1001.0, 1000.5).mapIndexed { i, north -> sample(0.0, north, 222.0 + i) } +
                (225..390).map { sample(0.0, 1000.3, it.toDouble()) }
        val passes = passesOver(straight, piece)
        assertEquals(1, passes.size)
        assertNear(20.6, passes[0].start)
        assertNear(220.6, passes[0].end)
    }

    @Test
    fun slowCrossingTakesTheLaterSample() {
        // Walks across the start line at 0.8 m/s, too slow to say when within the second.
        val piece = rideNorth(0.0, -100.0, 0.0, 20) + sample(0.0, -0.4, 20.0) + rideNorth(0.0, 0.4, 21.0, 210)
        val passes = passesOver(straight, piece)
        assertEquals(1, passes.size)
        assertNear(21.0, passes[0].start, tolerance = 0.1)
        assertNear(220.92, passes[0].end, tolerance = 0.1)
    }

    @Test
    fun switchbacksRecrossingTheStartLineDontMoveTheStart() {
        // Three stacked legs 8 m apart right after the start; the third crosses the start line again, going forward, 16 m from the start.
        val path = listOf(0.0 to 0.0, 20.0 to 0.0, 20.0 to 8.0, -10.0 to 8.0, -10.0 to 16.0, 20.0 to 16.0, 20.0 to 300.0)
        val line = path.map { (east, north) -> at(east, north) }
        val passes = passesOver(line, track(listOf(-100.0 to 0.0) + path))
        assertEquals(1, passes.size)
        assertNear(20.0, passes[0].start)
        assertNear(96.0, passes[0].end)
    }

    @Test
    fun switchbacksCrossingTheEndLineEarlyDontEndThePass() {
        // Three stacked legs 8 m apart at the finish; the first crosses the end line, going forward, 16 m from the end.
        val path = listOf(0.0 to 0.0, 30.0 to 0.0, 30.0 to 8.0, 0.0 to 8.0, 0.0 to 16.0, 20.0 to 16.0)
        val line = path.map { (east, north) -> at(east, north) }
        val passes = passesOver(line, track(listOf(-100.0 to 0.0) + path))
        assertEquals(1, passes.size)
        assertNear(20.0, passes[0].start)
        assertNear(39.2, passes[0].end)
    }

    @Test
    fun e7RoundTripLeavesResultsUnchanged() {
        val piece = track(listOf(0.0 to -100.0, 0.0 to 1100.0)) { d -> if ((d / 5).toInt() % 3 == 0) 7.0 else -4.0 }
        val e7 = piece.map { TimedPoint((it.lat * 1e7).roundToLong() / 1e7, (it.lng * 1e7).roundToLong() / 1e7, it.t) }
        val exact = passesOver(straight, piece).map { it.start.roundToLong() to it.end.roundToLong() }
        val rounded = passesOver(straight, e7).map { it.start.roundToLong() to it.end.roundToLong() }
        assertEquals(exact, rounded)
    }

    /** A line along [path] (east, north waypoints in meters), a point every [spacing] meters, each moved east by [offset] of its index. */
    private fun line(path: List<Pair<Double, Double>>, spacing: Double = 20.0, offset: (Int) -> Double = { 0.0 }): List<GeoPoint> =
        track(path, spacing).mapIndexed { i, p -> GeoPoint(p.lat, p.lng + offset(i) / metersPerDegreeLng) }

    private val climb = listOf(0.0 to 0.0, 400.0 to 800.0, 300.0 to 1600.0, 900.0 to 2400.0)

    @Test
    fun jitteredCopyIsADuplicate() {
        val original = line(climb)
        // Another athlete's trace of the same climb: other points, drifting up to 12 m either side, endpoints 10 m off.
        val copy = line(climb, spacing = 7.0) { i -> 10.0 + 2.0 * sin(i * 7.0 / 40.0) - 12.0 * sin(i * 7.0 / 300.0) }
        assertTrue(SegmentGeometry.duplicates(original, copy))
        assertTrue(SegmentGeometry.duplicates(copy, original))
    }

    @Test
    fun reversedLineIsNotADuplicate() {
        val original = line(climb)
        assertFalse(SegmentGeometry.duplicates(original, original.reversed()))
    }

    @Test
    fun longerSupersetIsNotADuplicate() {
        val original = line(climb)
        val longer = line(climb + (900.0 to 4400.0))
        assertFalse(SegmentGeometry.duplicates(original, longer))
    }

    @Test
    fun copyBeyondTheDistanceIsNotADuplicate() {
        assertFalse(SegmentGeometry.duplicates(line(climb), line(climb) { i -> if (i in 40..60) 45.0 else 0.0 }))
    }

    @Test
    fun detourLoopIsNotADuplicate() {
        // The same 3 km straight, but with a 300 m out-and-back spur 150 m off the line halfway along: 10% longer, same ends.
        val straight3k = listOf(0.0 to 0.0, 0.0 to 3000.0)
        val detour = listOf(0.0 to 0.0, 0.0 to 1500.0, 150.0 to 1500.0, 0.0 to 1500.0, 0.0 to 3000.0)
        assertFalse(SegmentGeometry.duplicates(line(straight3k), line(detour)))
    }

    @Test
    fun longPairIsCheckedQuickly() {
        // A 50 km zigzag and a jittered copy of it.
        val path = (0..25).map { k -> (if (k % 2 == 0) 0.0 else 600.0) to k * 1_990.0 }
        val original = line(path, spacing = 25.0)
        val copy = line(path, spacing = 9.0) { i -> 15.0 * sin(i * 9.0 / 250.0) }
        val took = measureTime { assertTrue(SegmentGeometry.duplicates(original, copy)) }
        assertTrue(took < 2.seconds, "took $took")
    }

    @Test
    fun aSampleBesideTheEndThenOneWithinItFinishes() {
        val approach = track(listOf(0.0 to 0.0, 0.0 to 990.0))
        val t = approach.last().t
        // 28 m beside the end (closer to it than to the point before, but outside the end circle), then within it.
        val beside = at(28.0, 1003.0)
        val within = at(10.0, 1002.0)
        val piece = approach + TimedPoint(beside.lat, beside.lng, t + 6) + TimedPoint(within.lat, within.lng, t + 9)
        assertEquals(1, passesOver(straight, piece).size)
    }

    // Comparing two drawings of one trail (segments/segment-import's refine)

    private fun simplified(tolerance: Double) = TrackMath.simplify(TomSawyer.samples, tolerance).map { GeoPoint(it.latitude, it.longitude) }

    @Test
    fun identicalLinesAreZeroApart() {
        assertEquals(0.0, SegmentGeometry.hausdorffMeters(TomSawyer.points, TomSawyer.points, 2.0), absoluteTolerance = 1e-6)
    }

    @Test
    fun fiveAndOneMeterDrawingsOfOneTrailAreWithinSixMeters() {
        val apart = SegmentGeometry.hausdorffMeters(simplified(5.0), simplified(1.0), 2.0)
        assertTrue(apart <= 6.0, "$apart m")
        assertTrue(apart > 1.0, "the 5 m drawing really is coarser")
    }

    @Test
    fun aReroutedStretchIsFurtherThanSixMeters() {
        // Half the trail moved 20 m north.
        val moved =
            TomSawyer.points.mapIndexed { i, p -> if (i < TomSawyer.points.size / 2) p else GeoPoint(p.lat + 20.0 / 111_195.0, p.lng) }
        assertTrue(SegmentGeometry.hausdorffMeters(simplified(5.0), moved, 2.0) > 6.0)
    }

    @Test
    fun theDistanceToALineIsToItsNearestStretch() {
        val line = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001))
        assertEquals(10.0, SegmentGeometry.distanceToLineMeters(GeoPoint(10.0 / 111_195.0, 0.0005), line), absoluteTolerance = 0.01)
    }
}
