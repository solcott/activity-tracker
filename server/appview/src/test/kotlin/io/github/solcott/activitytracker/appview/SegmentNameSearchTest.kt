package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.LabelSources
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.labels.LabelStore
import io.github.solcott.activitytracker.lexicon.app.SearchSegmentNames
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.ktor.server.testing.testApplication
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.update

/** `feed.searchSegmentNames` (segments/segment-records): segments found by their current name, ranked, then nearest first. */
class SegmentNameSearchTest : SegmentTestBase() {
    private val labeler = "did:plc:labeler"

    @AfterTest fun forgetLabelers() = run { LabelSources.trusted = emptyList() }

    /** A 1 km segment running north, [east] metres east of the origin, so segments this far apart never duplicate one another. */
    private fun named(rkey: String, name: String, east: Double, sport: String = "run", did: String = alice) {
        segment(rkey, path = listOf(east to 0.0, east to 1000.0), sport = sport, did = did, name = name)
    }

    private fun update(uri: String, change: Segments.(UpdateStatement) -> Unit) = runBlocking {
        db.tx { Segments.update({ Segments.uri eq uri }, body = change) }
    }

    private fun activate(vararg rkeys: String) = rkeys.forEach { rkey ->
        update(segmentUri(rkey)) { it[Segments.status] = SegmentStatus.ACTIVE }
    }

    private fun e7(value: Double) = "${(value * 1e7).roundToInt()}"

    private fun near(east: Double): Map<String, String> = at(east, 500.0).let { mapOf("nearLat" to e7(it.lat), "nearLng" to e7(it.lng)) }

    private fun box(fromEast: Double, toEast: Double): Map<String, String> {
        val sw = at(fromEast, 400.0)
        val ne = at(toEast, 600.0)
        return mapOf("minLat" to e7(sw.lat), "minLng" to e7(sw.lng), "maxLat" to e7(ne.lat), "maxLng" to e7(ne.lng))
    }

    private fun label(uri: String, value: String) = runBlocking {
        LabelSources.trusted = listOf(labeler)
        LabelStore(db, labeler, null).apply(labeler, 1, listOf(testLabel(labeler, uri, value)))
    }

    @Test
    fun namesRankByMatchThenPage() = testApplication {
        application { appView(config, db, verifier) }
        named("3ka", "Hogback Climb", 0.0)
        named("3kb", "Big Hog Loop", 300.0)
        named("3kc", "Shoghill", 600.0)
        named("3kd", "Hog", 900.0)
        named("3ke", "Ridge", 1200.0)
        activate("3ka", "3kb", "3kc", "3kd", "3ke")
        suspend fun found(query: Map<String, String>) = client.xrpcOk(SearchSegmentNames.method, query)

        val all = found(mapOf("q" to "HOG"))
        assertEquals(
            listOf("3kd", "3ka", "3kb", "3kc").map { segmentUri(it) },
            all.segments.map { it.uri.value },
            "whole, prefix, word, anywhere",
        )
        assertNull(all.cursor)

        val first = found(mapOf("q" to "hog", "limit" to "2"))
        assertEquals(listOf(segmentUri("3kd"), segmentUri("3ka")), first.segments.map { it.uri.value })
        val second = found(mapOf("q" to "hog", "limit" to "2", "cursor" to first.cursor!!))
        assertEquals(listOf(segmentUri("3kb"), segmentUri("3kc")), second.segments.map { it.uri.value })

        val blank = found(mapOf("q" to "   "))
        assertEquals(emptyList(), blank.segments)
        assertNull(blank.cursor)
        assertEquals(emptyList(), found(mapOf("q" to "50%")).segments, "a % is matched literally")
    }

    @Test
    fun theCurrentNameIsSearchedNotTheOriginal() = testApplication {
        application { appView(config, db, verifier) }
        named("3ka", "Trail 214", 0.0)
        activate("3ka")
        update(segmentUri("3ka")) { it[Segments.currentName] = "Wiggly Woods" }

        val found = client.xrpcOk(SearchSegmentNames.method, mapOf("q" to "wiggly")).segments
        assertEquals(listOf(segmentUri("3ka")), found.map { it.uri.value })
        assertEquals("Wiggly Woods", found.single().name)
        assertEquals(emptyList(), client.xrpcOk(SearchSegmentNames.method, mapOf("q" to "214")).segments)
    }

    @Test
    fun nearestComesFirstWithinARank() = testApplication {
        application { appView(config, db, verifier) }
        named("3ka", "Summit Trail", 0.0)
        named("3kb", "Summit Trail", 5000.0)
        activate("3ka", "3kb")
        suspend fun order(extra: Map<String, String>) =
            client.xrpcOk(SearchSegmentNames.method, mapOf("q" to "summit") + extra).segments.map { it.uri.value }

        assertEquals(listOf(segmentUri("3kb"), segmentUri("3ka")), order(near(5000.0)))
        assertEquals(listOf(segmentUri("3ka"), segmentUri("3kb")), order(near(0.0)))
        assertEquals(listOf(segmentUri("3ka"), segmentUri("3kb")), order(emptyMap()), "by name, then URI, without a centre")
        assertEquals("InvalidRequest", client.xrpcError(SearchSegmentNames.method, mapOf("q" to "summit", "nearLat" to "443000000")))
    }

    @Test
    fun aBoxAndASportGroupNarrowTheSearch() = testApplication {
        application { appView(config, db, verifier) }
        named("3ka", "Summit Trail", 0.0)
        named("3kb", "Summit Trail", 5000.0)
        named("3kc", "Summit Ride", 300.0, sport = "ride")
        activate("3ka", "3kb", "3kc")
        suspend fun found(extra: Map<String, String>) =
            client.xrpcOk(SearchSegmentNames.method, mapOf("q" to "summit") + extra).segments.map { it.uri.value }

        assertEquals(listOf(segmentUri("3ka")), found(box(-100.0, 100.0)), "only the line crossing the box")
        assertEquals(listOf(segmentUri("3kc")), found(mapOf("sport" to "ride")))
        assertEquals(listOf(segmentUri("3ka"), segmentUri("3kb")), found(mapOf("sport" to "run")))

        val wide = mapOf("q" to "summit", "minLat" to "460000000", "minLng" to "70000000", "maxLat" to "480000000", "maxLng" to "100000000")
        assertEquals("InvalidRequest", client.xrpcError(SearchSegmentNames.method, wide))
        val partial = mapOf("q" to "summit") + box(-100.0, 100.0) - "maxLng"
        assertEquals("InvalidRequest", client.xrpcError(SearchSegmentNames.method, partial))
    }

    @Test
    fun liftsAreNeverFound() = testApplication {
        application { appView(config, db, verifier) }
        named("3ka", "Summit", 0.0, sport = "alpine")
        named("3kb", "Summit", 300.0, sport = "alpine")
        activate("3ka", "3kb")
        update(segmentUri("3kb")) { it[Segments.kind] = SegmentKind.LIFT }

        val found = client.xrpcOk(SearchSegmentNames.method, mapOf("q" to "summit")).segments
        assertEquals(listOf(segmentUri("3ka")), found.map { it.uri.value })
    }

    @Test
    fun nameSearchListsWhatAreaSearchWould() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks", name = "Proposed Climb")
        matching(true, did = bob)
        activity("3kr", did = bob)
        track("3kr", did = bob)
        match()
        named("3kz", "Archived Climb", 600.0, did = carol)
        update(segmentUri("3kz", carol)) { it[Segments.status] = SegmentStatus.ARCHIVED }
        named("3kh", "Hidden Climb", 900.0, did = carol)
        update(segmentUri("3kh", carol)) { it[Segments.status] = SegmentStatus.ACTIVE }
        suspend fun listed(viewer: String?) =
            client.xrpcOk(SearchSegmentNames.method, mapOf("q" to "climb"), viewer).segments.map { it.uri.value }
        // The app sets its trusted labelers when it starts, on the first request, so the label goes in after one.
        assertEquals(listOf(segmentUri("3kh", carol)), listed(null))
        label(segmentUri("3kh", carol), "!hide")

        assertEquals(listOf(segmentUri("3ks")), listed(alice), "the creator sees her proposed segment")
        assertEquals(listOf(segmentUri("3ks")), listed(bob), "a rider sees it")
        assertEquals(listOf(segmentUri("3kh", carol)), listed(carol), "a stranger doesn't; her own hidden segment she does, archived never")
        assertEquals(emptyList(), listed(null))
    }
}
