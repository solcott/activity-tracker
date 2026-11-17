package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.assignMissingRoutes
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.ktor.server.testing.testApplication
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** Routes (segments/segment-routes): segments of one kind following one path, in any sport group and either way. */
class SegmentRoutesTest : SegmentTestBase() {
    private val back = straight.reversed()

    private fun route(uri: String): String? = runBlocking {
        db.tx { Segments.selectAll().where { Segments.uri eq uri }.single()[Segments.routeUri] }
    }

    private fun status(uri: String): String = runBlocking {
        db.tx { Segments.selectAll().where { Segments.uri eq uri }.single()[Segments.status] }
    }

    @Test
    fun sportGroupsOfOneTrailShareARoute() {
        segment("3ka", sport = "ride")
        segment("3kb", sport = "run")
        segment("3kc", sport = "hike")
        listOf("3ka", "3kb", "3kc").forEach { assertEquals(segmentUri("3ka"), route(segmentUri(it)), it) }
    }

    @Test
    fun oppositeDirectionsShareARoute() {
        segment("3ka", sport = "ride")
        segment("3kb", path = back, sport = "ride")
        segment("3kc", path = back, sport = "run")
        assertEquals(segmentUri("3ka"), route(segmentUri("3kb")))
        assertEquals(segmentUri("3ka"), route(segmentUri("3kc")))
        assertEquals(SegmentStatus.PROPOSED, status(segmentUri("3kb")), "a reversed line still isn't a duplicate")
    }

    @Test
    fun aRunOverARideLineIsNoDuplicate() {
        segment("3ka", sport = "ride")
        segment("3kb", sport = "run")
        assertEquals(SegmentStatus.PROPOSED, status(segmentUri("3kb")))
        assertEquals(segmentUri("3ka"), route(segmentUri("3kb")))
    }

    @Test
    fun aLongerStretchIsItsOwnRoute() {
        segment("3ka")
        segment("3kb", path = listOf(0.0 to 0.0, 0.0 to 3000.0))
        assertEquals(segmentUri("3kb"), route(segmentUri("3kb")))
    }

    @Test
    fun aLiftAndARunAreApart() {
        val lift =
            """{"name":"Summit Quad","sport":"alpine","kind":"lift","line":"${lineOf(straight)}","source":"osm",""" +
                """"license":"ODbL-1.0","createdAt":"2026-10-01T00:00:00Z"}"""
        commit(importer, Nsid.FEED_SEGMENT, "lift", lift)
        segment("run", sport = "alpine", did = importer)
        assertEquals(segmentUri("run", importer), route(segmentUri("run", importer)))
    }

    @Test
    fun deletingTheNamerKeepsTheRestTogether() {
        segment("3ka", sport = "ride")
        segment("3kb", path = back, sport = "run")
        segment("3kc", sport = "hike")
        commit(alice, Nsid.FEED_SEGMENT, "3ka", null)
        assertEquals(segmentUri("3kb"), route(segmentUri("3kb")))
        assertEquals(segmentUri("3kb"), route(segmentUri("3kc")))
    }

    @Test
    fun aRelinedMemberMovesToItsNewTrail() {
        val elsewhere = listOf(2000.0 to 0.0, 2000.0 to 1000.0)
        segment("3ka", sport = "ride")
        segment("3kb", path = elsewhere, sport = "ride")
        segment("3kc", sport = "run")
        assertEquals(segmentUri("3ka"), route(segmentUri("3kc")))
        segment("3kc", path = elsewhere, sport = "run")
        assertEquals(segmentUri("3kb"), route(segmentUri("3kc")))
        assertEquals(segmentUri("3ka"), route(segmentUri("3ka")))
    }

    @Test
    fun aRelinedNamerHandsItsRouteOn() {
        segment("3ka", sport = "ride")
        segment("3kb", sport = "run")
        segment("3kc", sport = "hike")
        segment("3ka", path = listOf(2000.0 to 0.0, 2000.0 to 1000.0), sport = "ride")
        assertEquals(segmentUri("3ka"), route(segmentUri("3ka")))
        assertEquals(segmentUri("3kb"), route(segmentUri("3kb")))
        assertEquals(segmentUri("3kb"), route(segmentUri("3kc")))
    }

    @Test
    fun aDuplicateIsNoNamer() {
        segment("3ka", sport = "ride")
        segment("3kb", sport = "ride", did = bob)
        assertEquals(SegmentStatus.DUPLICATE, status(segmentUri("3kb", bob)))
        segment("3kc", sport = "run")
        assertEquals(segmentUri("3ka"), route(segmentUri("3kc")))
        commit(alice, Nsid.FEED_SEGMENT, "3ka", null)
        // Bob's was a duplicate of the deleted one and is live again: it came first, so it names the route.
        assertEquals(segmentUri("3kb", bob), route(segmentUri("3kb", bob)))
        assertEquals(segmentUri("3kb", bob), route(segmentUri("3kc")))
    }

    @Test
    fun segmentsFromBeforeRoutesAreGivenTheirs() {
        segment("3ka", sport = "ride")
        segment("3kb", path = back, sport = "ride")
        segment("3kc", path = listOf(2000.0 to 0.0, 2000.0 to 1000.0))
        runBlocking { db.tx { Segments.update { it[routeUri] = null } } }
        assertEquals(3, runBlocking { assignMissingRoutes(db, batch = 2) })
        assertEquals(segmentUri("3ka"), route(segmentUri("3ka")))
        assertEquals(segmentUri("3ka"), route(segmentUri("3kb")))
        assertEquals(segmentUri("3kc"), route(segmentUri("3kc")))
        assertNotEquals(route(segmentUri("3ka")), route(segmentUri("3kc")))
        assertEquals(0, runBlocking { assignMissingRoutes(db) })
    }

    private fun activate(vararg uris: String) = runBlocking {
        db.tx { uris.forEach { uri -> Segments.update({ Segments.uri eq uri }) { it[Segments.status] = SegmentStatus.ACTIVE } } }
    }

    @Test
    fun aSegmentListsItsRoutesOtherMembers() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ka", sport = "ride")
        segment("3kb", path = back, sport = "ride")
        segment("3kc", sport = "run")
        segment("3kd", path = back, sport = "run", did = bob)
        activate(segmentUri("3ka"), segmentUri("3kb"), segmentUri("3kc"))
        val output = client.xrpcOk(GetSegment.method, mapOf("uri" to segmentUri("3ka")), viewer = carol)
        assertEquals(segmentUri("3ka"), output.segment.route?.value)
        assertEquals(
            listOf(segmentUri("3kb"), segmentUri("3kc")),
            output.routeMembers.map { it.uri.value },
            "Bob's proposed segment isn't listed to Carol",
        )
        assertEquals(listOf("ride", "run"), output.routeMembers.map { it.sport })
        val bobs = client.xrpcOk(GetSegment.method, mapOf("uri" to segmentUri("3ka")), viewer = bob)
        assertEquals(segmentUri("3kd", bob), bobs.routeMembers.last().uri.value, "his own is")
        segment("3ke", path = listOf(2000.0 to 0.0, 2000.0 to 1000.0))
        val alone = client.xrpcOk(GetSegment.method, mapOf("uri" to segmentUri("3ke")), viewer = alice)
        assertEquals(emptyList(), alone.routeMembers)
    }

    @Test
    fun anAreaPageHoldsItsRoutesWhole() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ka", sport = "ride")
        segment("3kb", path = listOf(200.0 to 0.0, 200.0 to 1000.0), sport = "ride")
        segment("3kc", path = back, sport = "run")
        segment("3kd", path = back, sport = "hike", did = bob)
        activate(segmentUri("3ka"), segmentUri("3kb"), segmentUri("3kc"))
        val sw = at(-500.0, 400.0)
        val ne = at(500.0, 600.0)
        fun box(extra: Map<String, String> = emptyMap()) =
            mapOf(
                "minLat" to "${(sw.lat * 1e7).roundToInt()}",
                "minLng" to "${(sw.lng * 1e7).roundToInt()}",
                "maxLat" to "${(ne.lat * 1e7).roundToInt()}",
                "maxLng" to "${(ne.lng * 1e7).roundToInt()}",
                "limit" to "1",
            ) + extra
        val first = client.xrpcOk(SearchSegments.method, box(), viewer = carol)
        assertEquals(
            listOf(segmentUri("3ka"), segmentUri("3kc")),
            first.segments.map { it.uri.value },
            "the run the other way comes with the ride, and Bob's proposed hike isn't listed to Carol",
        )
        val runs = client.xrpcOk(SearchSegments.method, box(mapOf("sport" to "run")), viewer = carol)
        assertEquals(listOf(segmentUri("3kc")), runs.segments.map { it.uri.value }, "the sport still narrows the route")
        val second = client.xrpcOk(SearchSegments.method, box(mapOf("cursor" to first.cursor!!)), viewer = carol)
        assertEquals(listOf(segmentUri("3kb")), second.segments.map { it.uri.value })
    }
}
