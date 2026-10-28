package io.github.solcott.activitytracker.location

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Drawing a line as a curve through its points (feed/route-line-detail). */
class RouteCurveTest {
    private val metersPerLng = 111_195.0 * cos(LAT * PI / 180)

    /** [x] meters east and [y] north of a point on the trail, as a (lat, lng) pair. */
    private fun at(x: Double, y: Double) = (LAT + y / 111_195.0) to (LNG + x / metersPerLng)

    private fun geo(p: Pair<Double, Double>) = GeoPoint(p.first, p.second)

    /** A switchback as a 1 m simplification keeps it: 40 m up, a hairpin 10 m across in a handful of points, 40 m back down. */
    private val hairpin =
        listOf(at(0.0, 0.0), at(0.0, 40.0)) +
            (1..4).map { k -> at(5 - 5 * cos(PI * k / 5), 40 + 5 * sin(PI * k / 5)) } +
            listOf(at(10.0, 40.0), at(10.0, 0.0))

    private val tomSawyer = TrackMath.simplify(TomSawyer.samples, 1.0).map { it.latitude to it.longitude }

    /** Whether [points] appear in [curve] in order. */
    private fun keepsInOrder(points: List<Pair<Double, Double>>, curve: List<Pair<Double, Double>>): Boolean {
        var next = 0
        for (p in curve) if (next < points.size && p == points[next]) next++
        return next == points.size
    }

    /** Each drawn point's distance from the straight stretch between the stored points either side of it. */
    private fun bulges(points: List<Pair<Double, Double>>, curve: List<Pair<Double, Double>>): List<Double> {
        val result = mutableListOf<Double>()
        var stretch = 0
        for (p in curve) {
            if (stretch < points.lastIndex && p == points[stretch + 1]) stretch++
            else if (p != points[stretch])
                result += SegmentGeometry.distanceToLineMeters(geo(p), listOf(geo(points[stretch]), geo(points[stretch + 1])))
        }
        return result
    }

    @Test
    fun everyStoredPointIsKeptInOrder() {
        for (line in listOf(hairpin, tomSawyer)) {
            val curve = RouteCurve.smooth(line)
            assertEquals(line.first(), curve.first())
            assertEquals(line.last(), curve.last())
            assertTrue(keepsInOrder(line, curve))
        }
    }

    @Test
    fun theCurveNeverStraysMoreThanTwoMetersFromAStretch() {
        for (line in listOf(hairpin, tomSawyer)) {
            val furthest = bulges(line, RouteCurve.smooth(line)).max()
            assertTrue(furthest <= RouteCurve.MAX_BULGE_M + 1e-6, "$furthest m")
        }
    }

    @Test
    fun aHairpinIsDrawnRound() {
        val curve = RouteCurve.smooth(hairpin)
        assertTrue(curve.size > hairpin.size + 8, "${curve.size} points")
        assertTrue(bulges(hairpin, curve).max() > 0.1, "the curve leaves the straight stretches")
        assertTrue(RouteCurve.smooth(tomSawyer).size > tomSawyer.size * 2)
    }

    @Test
    fun aStraightLineStaysStraight() {
        val road = (0..10).map { at(it * 50.0, it * 20.0) }
        assertEquals(road, RouteCurve.smooth(road))
    }

    @Test
    fun nothingLoopsBack() {
        // Each stretch is drawn no longer than its chord plus twice the most it may bulge.
        val curve = RouteCurve.smooth(tomSawyer)
        var stretch = 0
        var drawn = 0.0
        for ((a, b) in curve.zipWithNext()) {
            drawn += SegmentGeometry.distanceMeters(geo(a), geo(b))
            if (b == tomSawyer[stretch + 1]) {
                val chord = SegmentGeometry.distanceMeters(geo(tomSawyer[stretch]), geo(b))
                assertTrue(drawn <= chord + 2 * RouteCurve.MAX_BULGE_M, "stretch $stretch: $drawn m for a $chord m chord")
                stretch++
                drawn = 0.0
            }
        }
        assertEquals(tomSawyer.lastIndex, stretch)
    }

    @Test
    fun shortLinesAndRepeatedPointsAreLeftAlone() {
        val two = listOf(at(0.0, 0.0), at(10.0, 0.0))
        assertEquals(two, RouteCurve.smooth(two))
        val repeated = listOf(at(0.0, 0.0), at(0.0, 0.0), at(10.0, 10.0), at(20.0, 0.0))
        assertTrue(keepsInOrder(repeated, RouteCurve.smooth(repeated)))
    }

    private companion object {
        const val LAT = 40.49
        const val LNG = -111.82
    }
}
