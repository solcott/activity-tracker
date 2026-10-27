package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.HIKE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.RIDE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.RUN
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import kotlin.test.Test
import kotlin.test.assertEquals

/** Candidates that duplicate one another keep one (segments/trail-candidates). */
class CandidateDedupTest {
    private val line = (0..10).map { GeoPoint(north(44.4, it * 100.0), -72.7) }
    private val nudged = line.map { GeoPoint(it.lat, it.lng + 0.0001) } // about 8 m east

    @Suppress("LongParameterList") // Each way a test candidate differs.
    private fun candidate(
        id: String,
        points: List<GeoPoint> = line,
        group: String = RIDE,
        kind: TrailKind = TrailKind.NAMED,
        whole: Boolean = true,
        source: String = SegmentSource.OSM,
    ) = Candidate(id, group, id, SegmentGeometry.encodePolyline(points), points, 1000, emptyList(), kind, whole, source = source)

    @Test
    fun anAgencyTrailWinsOverTheSameOsmTrail() {
        val kept =
            CandidateDedup.dedup(
                listOf(candidate("aaa", kind = TrailKind.RELATION), candidate("zzz", nudged, group = RIDE, source = "agency"))
            )
        assertEquals(listOf("zzz"), kept.map { it.id })
    }

    @Test
    fun differentGroupsAreKeptApartAcrossSources() {
        // The agency trail is for hikes only; the OSM path along it is for rides and hikes.
        val kept =
            CandidateDedup.dedup(
                listOf(
                    candidate("agency-hike", nudged, group = HIKE, source = "agency"),
                    candidate("osm-hike", group = HIKE),
                    candidate("osm-ride"),
                )
            )
        assertEquals(setOf("agency-hike", "osm-ride"), kept.map { it.id }.toSet())
    }

    @Test
    fun aRouteRelationsCandidateWinsOverANamedTrails() {
        val kept = CandidateDedup.dedup(listOf(candidate("aaa"), candidate("zzz", nudged, kind = TrailKind.RELATION)))
        assertEquals(listOf("zzz"), kept.map { it.id })
    }

    @Test
    fun aWholeTrailWinsOverAStretch() {
        val kept = CandidateDedup.dedup(listOf(candidate("aaa", whole = false), candidate("zzz", nudged)))
        assertEquals(listOf("zzz"), kept.map { it.id })
    }

    @Test
    fun aSectionsWholeTrailWinsOverAParentTrailThatDuplicatesIt() {
        val kept = CandidateDedup.dedup(listOf(candidate("aaa", kind = TrailKind.PARENT), candidate("zzz", nudged)))
        assertEquals(listOf("zzz"), kept.map { it.id })
    }

    @Test
    fun aParentTrailWinsOverAStretch() {
        val kept = CandidateDedup.dedup(listOf(candidate("aaa", whole = false), candidate("zzz", nudged, kind = TrailKind.PARENT)))
        assertEquals(listOf("zzz"), kept.map { it.id })
    }

    @Test
    fun aRouteRelationOverAParentTrailsWaysLeavesOneCandidateOfThatId() {
        // The same ways between the same ends give both the same id.
        val kept = CandidateDedup.dedup(listOf(candidate("same", kind = TrailKind.PARENT), candidate("same", kind = TrailKind.RELATION)))
        assertEquals(listOf(TrailKind.RELATION), kept.map { it.kind })
    }

    @Test
    fun otherwiseTheLowerIdWins() {
        assertEquals(listOf("bbb"), CandidateDedup.dedup(listOf(candidate("ccc", nudged), candidate("bbb"))).map { it.id })
    }

    @Test
    fun differentGroupsAndDirectionsAreKept() {
        val kept = CandidateDedup.dedup(listOf(candidate("aaa"), candidate("bbb", group = RUN), candidate("ccc", line.reversed())))
        assertEquals(setOf("aaa", "bbb", "ccc"), kept.map { it.id }.toSet())
    }

    @Test
    fun aRouteRelationOverANamedTrailKeepsOnlyTheRelations() {
        val data = osm {
            for (i in 0 until 5) node(i + 1L, north(44.4, i * 250.0), -72.7)
            way(100, 1, 2, 3, 4, 5, tags = path("Oak Trail"))
            relation(200, 100, tags = mapOf("type" to "route", "route" to "mtb", "name" to "Oak MTB Route"))
        }
        val cutter = CandidateCutter(data)
        val all = TrailBuilder.build(data).filter { it.group == RIDE }.flatMap(cutter::cut)
        val kept = CandidateDedup.dedup(all)
        assertEquals(2, kept.size, "one each way")
        assertEquals(true, kept.all { it.kind == TrailKind.RELATION })
    }
}
