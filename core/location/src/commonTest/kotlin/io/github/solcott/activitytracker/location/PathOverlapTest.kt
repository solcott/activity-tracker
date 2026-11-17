package io.github.solcott.activitytracker.location

import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Whether a segment lies on a path (segments/segment-paths). */
class PathOverlapTest {
    private val origin = GeoPoint(44.4, -72.9)
    private val metersPerDegreeLat = 111_320.0
    private val metersPerDegreeLng = metersPerDegreeLat * cos(origin.lat * PI / 180.0)

    private fun at(east: Double, north: Double) = GeoPoint(origin.lat + north / metersPerDegreeLat, origin.lng + east / metersPerDegreeLng)

    /** A straight line east from [from] to [to] meters, [north] meters north of the origin. */
    private fun east(from: Double, to: Double, north: Double = 0.0) = listOf(at(from, north), at(to, north))

    private val anns = listOf(east(0.0, 5_000.0))

    @Test
    fun aStretchOfTheTrailLiesOnIt() {
        assertTrue(PathOverlap.liesOn(east(1_000.0, 2_200.0), anns))
        assertEquals(1_200.0, PathOverlap.overlapMeters(east(1_000.0, 2_200.0), anns), 15.0)
    }

    @Test
    fun aSegmentCombiningTwoTrailsLiesOnBoth() {
        val hogback = listOf(listOf(at(5_000.0, 0.0), at(5_000.0, 2_500.0)))
        val combined = listOf(at(3_500.0, 0.0), at(5_000.0, 0.0), at(5_000.0, 2_500.0))
        assertTrue(PathOverlap.liesOn(combined, anns))
        assertTrue(PathOverlap.liesOn(combined, hogback))
    }

    @Test
    fun aClipAtAJunctionDoesNot() {
        val road = listOf(at(2_000.0, -1_500.0), at(2_000.0, 1_500.0))
        assertFalse(PathOverlap.liesOn(road, anns), "it crosses Ann's for some 60 m of 3 km")
    }

    @Test
    fun eitherDirectionCounts() {
        assertTrue(PathOverlap.liesOn(east(2_200.0, 1_000.0), anns))
    }

    @Test
    fun aKilometerOnALongSegmentIsEnough() {
        val long = listOf(at(4_000.0, 0.0), at(5_000.0, 0.0), at(5_000.0, 9_000.0))
        assertTrue(PathOverlap.liesOn(long, anns), "1 km of 10 km, under a quarter but a whole kilometer")
    }

    @Test
    fun aParallelLineOutsideTheCorridorDoesNot() {
        assertFalse(PathOverlap.liesOn(east(1_000.0, 2_200.0, north = 60.0), anns))
    }

    @Test
    fun anyPieceOfAPathCounts() {
        val pieces = listOf(east(0.0, 1_000.0), east(20_000.0, 30_000.0))
        assertTrue(PathOverlap.liesOn(east(21_000.0, 22_000.0), pieces))
        assertFalse(PathOverlap.liesOn(east(5_000.0, 6_000.0), pieces), "in the gap between pieces")
    }

    @Test
    fun aParallelTrailLosesToTheOneTheSegmentIsDrawnOn() {
        val hogHollow = PathCandidate("hog", listOf(east(0.0, 5_000.0, north = 20.0)))
        val achtung = PathCandidate("achtung", anns)
        val alongHog = east(1_000.0, 2_000.0, north = 20.0)
        assertEquals(setOf("hog"), PathOverlap.pathsUnder(alongHog, "ride", listOf(hogHollow, achtung)))
        assertTrue(PathOverlap.liesOn(alongHog, anns), "alone, Achtung Baby would have taken it")
    }

    @Test
    fun trailsSharingTreadBothKeepIt() {
        val section = PathCandidate("2112", listOf(east(0.0, 3_000.0)))
        val family = PathCandidate("rush", listOf(east(-1_000.0, 4_000.0)))
        assertEquals(setOf("2112", "rush"), PathOverlap.pathsUnder(east(500.0, 1_500.0), "ride", listOf(section, family)))
    }

    @Test
    fun aPathClosedToTheSportNeitherHoldsNorCompetes() {
        val footPath = PathCandidate("hoof", listOf(east(0.0, 5_000.0)), sports = setOf("run", "hike"))
        val rideTrail = PathCandidate("ride", listOf(east(0.0, 5_000.0, north = 20.0)))
        val ride = east(1_000.0, 2_000.0, north = 5.0)
        assertEquals(setOf("ride"), PathOverlap.pathsUnder(ride, "ride", listOf(footPath, rideTrail)), "nearer the foot path, but ride")
        assertEquals(setOf("hoof"), PathOverlap.pathsUnder(ride, "run", listOf(footPath, rideTrail)))
    }

    @Test
    fun anImportedSegmentLiesOnlyOnAPathItMostlyFollows() {
        // 5 km east, the middle 2 km along Ann's: as Bonneville Shoreline's segment runs along Clarks.
        val long = east(0.0, 5_000.0)
        val middle = listOf(east(1_500.0, 3_500.0))
        assertTrue(PathOverlap.liesOn(long, middle), "an athlete's segment lies on it")
        assertFalse(PathOverlap.liesOn(long, middle, imported = true), "40% of an imported one doesn't")
        assertTrue(PathOverlap.liesOn(east(1_600.0, 3_400.0), middle, imported = true), "a stretch of the trail itself does")
    }
}
