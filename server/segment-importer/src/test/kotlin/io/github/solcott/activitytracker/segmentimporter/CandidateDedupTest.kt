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
    fun aNamedWholeTrailWinsOverAJunctionSpanThatDuplicatesIt() {
        val kept = CandidateDedup.dedup(listOf(candidate("aaa", kind = TrailKind.PARENT, whole = false), candidate("zzz", nudged)))
        assertEquals(listOf("zzz"), kept.map { it.id })
    }

    @Test
    fun aSpanEveryBranchSharesIsKeptOnceUnderItsFamilysName() {
        val data = osm {
            for (i in 0..1) node(i + 1L, north(44.4, i * 500.0), -72.7)
            node(3, north(44.4, 1000.0), -72.69)
            node(4, north(44.4, 1000.0), -72.71)
            for (i in 0..2) node(i + 5L, north(44.4, 1500.0 + i * 500.0), -72.7)
            way(10, 1, 2, tags = path("Rush Trail - Tom Sawyer Section", "oneway" to "yes"))
            way(11, 2, 3, 5, tags = path("Rush Trail - Exit Stage Left Section", "oneway" to "yes"))
            way(12, 2, 4, 5, tags = path("Rush Trail - Through the Trees Trail Section", "oneway" to "yes"))
            way(13, 5, 6, tags = path("Rush Trail", "oneway" to "yes"))
            way(14, 6, 7, tags = path("Rush Trail - 2112 Section", "oneway" to "yes"))
            node(30, 0.0, 0.0)
            way(30, 30, 5, tags = path("Canyon Hollow Trail"))
        }
        val cutter = CandidateCutter(data)
        val kept = CandidateDedup.dedup(TrailBuilder.build(data).filter { it.group == RIDE }.flatMap(cutter::cut))
        val spans = kept.filter { it.kind == TrailKind.PARENT && !it.whole }.map { it.name }.sorted()
        assertEquals(
            listOf(
                "Rush Trail from Canyon Hollow Trail",
                "Rush Trail via Exit Stage Left Section to Canyon Hollow Trail",
                "Rush Trail via Through the Trees Trail Section to Canyon Hollow Trail",
            ),
            spans,
        )
        assertEquals(kept.size, kept.map { it.id }.toSet().size, "no duplicate ids")
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
