package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.HIKE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.RIDE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.RUN
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** OSM candidates along an agency closure are left out (segments/trail-candidates). */
class ClosureFilterTest {
    /** A line due north from [fromM] to [toM] meters north of 44.4, [eastM] meters east of -72.7, with a point every 100 m. */
    private fun northward(fromM: Double, toM: Double, eastM: Double = 0.0): List<GeoPoint> =
        generateSequence(fromM) { it + 100.0 }.takeWhile { it < toM }.plus(toM).map { GeoPoint(north(44.4, it), east(eastM)) }.toList()

    private fun east(meters: Double) = -72.7 + meters / (METERS_PER_DEGREE * cos(44.4 * PI / 180.0))

    private fun candidate(points: List<GeoPoint>, group: String = RIDE, id: String = "c", source: String = SegmentSource.OSM) =
        Candidate(
            id,
            group,
            id,
            SegmentGeometry.encodePolyline(points),
            points,
            SegmentGeometry.lengthMeters(points).toLong(),
            osmWays = if (source == SegmentSource.OSM) listOf(100L) else emptyList(),
            kind = TrailKind.NAMED,
            whole = true,
            source = source,
        )

    private val wilderness = AgencyLine(open = setOf(RUN, HIKE), closed = setOf(RIDE), points = northward(0.0, 2000.0, eastM = 5.0))

    @Test
    fun anOsmPathThroughAWildernessKeepsOnlyItsFootCandidates() {
        val filter = ClosureFilter(listOf(wilderness))
        val path = northward(0.0, 2000.0)
        assertFalse(filter.keep(candidate(path)))
        assertFalse(filter.keep(candidate(path.reversed())))
        assertTrue(filter.keep(candidate(path, RUN)))
        assertTrue(filter.keep(candidate(path, HIKE)))
        assertEquals(mapOf(RIDE to 2), filter.leftOut)
    }

    @Test
    fun crossingAClosedTrailKeepsTheCandidate() {
        val crossing = (0..10).map { i -> GeoPoint(north(44.4, 1000.0), east(-500.0 + i * 100.0)) }
        val filter = ClosureFilter(listOf(wilderness))
        assertTrue(filter.keep(candidate(crossing)))
        assertEquals(emptyMap(), filter.leftOut)
    }

    @Test
    fun aParallelTrail40mAwayIsKept() {
        assertTrue(ClosureFilter(listOf(wilderness)).keep(candidate(northward(0.0, 2000.0, eastM = 45.0))))
    }

    @Test
    fun aShortOverlapIsKept() {
        // 60 m along the closed trail, then off to the east.
        val points = northward(1940.0, 2000.0) + (1..10).map { i -> GeoPoint(north(44.4, 2000.0 + i * 70.0), east(i * 70.0)) }
        assertTrue(ClosureFilter(listOf(wilderness)).keep(candidate(points)))
    }

    @Test
    fun aFeatureOpenAlongTheSamePathKeepsTheCandidate() {
        val winter = AgencyLine(open = setOf(RIDE), closed = emptySet(), points = northward(0.0, 2000.0, eastM = -6.0))
        assertTrue(ClosureFilter(listOf(wilderness, winter)).keep(candidate(northward(0.0, 2000.0))))
    }

    @Test
    fun onlyTheStretchesAlongTheClosureAreLeftOut() {
        val middle = AgencyLine(open = emptySet(), closed = setOf(RIDE), points = northward(1000.0, 2000.0, eastM = 8.0))
        val filter = ClosureFilter(listOf(middle))
        assertFalse(filter.keep(candidate(northward(0.0, 3000.0), id = "whole")))
        assertTrue(filter.keep(candidate(northward(0.0, 1000.0), id = "part 1")))
        assertFalse(filter.keep(candidate(northward(1000.0, 2000.0), id = "part 2")))
        assertTrue(filter.keep(candidate(northward(2000.0, 3000.0), id = "part 3")))
        assertEquals(mapOf(RIDE to 2), filter.leftOut)
    }

    @Test
    fun agencyCandidatesAreNeverLeftOut() {
        val filter = ClosureFilter(listOf(wilderness))
        assertTrue(filter.keep(candidate(northward(0.0, 2000.0), source = SegmentSource.AGENCY)))
        assertTrue(ClosureFilter(emptyList()).keep(candidate(northward(0.0, 2000.0))))
    }

    @Test
    fun overlapsAddUp() {
        // Two 60 m stretches of a candidate run along closed lines, far enough apart that neither alone is enough.
        val south = AgencyLine(open = emptySet(), closed = setOf(RIDE), points = northward(0.0, 60.0))
        val north = AgencyLine(open = emptySet(), closed = setOf(RIDE), points = northward(500.0, 560.0))
        assertFalse(ClosureFilter(listOf(south, north)).keep(candidate(northward(0.0, 1000.0))))
        assertTrue(ClosureFilter(listOf(south)).keep(candidate(northward(0.0, 1000.0))))
    }

    private companion object {
        const val METERS_PER_DEGREE = 111_195.0
    }
}
