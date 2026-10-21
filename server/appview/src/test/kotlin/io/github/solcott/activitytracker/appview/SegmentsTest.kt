package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.SegmentCellIndex
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.server.testing.testApplication
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.selectAll

/** `feed.segment` records (segments/segment-records): indexed from the repo only, validated, and served by URI and by area. */
class SegmentsTest : SegmentTestBase() {
    private fun segmentRows(): List<String> = runBlocking { db.tx { Segments.selectAll().map { it[Segments.uri] } } }

    private fun cellCount(): Long = runBlocking { db.tx { SegmentCellIndex.selectAll().count() } }

    @Test
    fun aSegmentIsIndexedFromTheRepo() {
        segment("3ks")
        assertEquals(listOf(segmentUri("3ks")), segmentRows())
        assertTrue(cellCount() > 0)
        assertEquals(listOf(SegmentJobs.SEGMENT to segmentUri("3ks")), jobs())
    }

    @Test
    fun aSegmentInASpaceIsIgnored() {
        spaceWrite(self(alice), Nsid.FEED_SEGMENT, "3ks", segmentJson())
        spaceWrite(space(Visibility.FOLLOWERS), Nsid.FEED_SEGMENT, "3kt", segmentJson())
        assertEquals(emptyList(), segmentRows())
    }

    @Test
    fun invalidSegmentsAreDropped() {
        segment("3ka", path = listOf(0.0 to 0.0, 0.0 to 150.0))
        segment("3kb", path = listOf(0.0 to 0.0, 0.0 to 30_000.0, 30_000.0 to 30_000.0))
        segment("3kc", sport = "skydive")
        segment("3kd", name = "   ")
        segment("3kh", name = "x".repeat(101))
        commit(alice, Nsid.FEED_SEGMENT, "3ke", segmentJson(line = "_p~iF~ps|U_"))
        commit(alice, Nsid.FEED_SEGMENT, "3kf", segmentJson(line = "_p~iF~ps|U"))
        commit(alice, Nsid.FEED_SEGMENT, "3kg", """{"name":"x"}""")
        assertEquals(emptyList(), segmentRows())
    }

    @Test
    fun replayingIsIdempotent() {
        segment("3ks")
        val cells = cellCount()
        match()
        segment("3ks")
        assertEquals(listOf(segmentUri("3ks")), segmentRows())
        assertEquals(cells, cellCount())
        assertEquals(emptyList(), jobs(), "an unchanged line isn't matched again")
    }

    @Test
    fun deletingASegmentRemovesItAndItsEfforts() {
        matching(true)
        activity("3ka")
        track("3ka")
        segment("3ks")
        match()
        assertEquals(1, efforts().size)

        commit(alice, Nsid.FEED_SEGMENT, "3ks", null)
        assertEquals(emptyList(), segmentRows())
        assertEquals(0L, cellCount())
        assertEquals(emptyList(), efforts())
    }

    @Test
    fun aChangedLineIsMatchedAgain() {
        matching(true)
        activity("3ka")
        track("3ka")
        segment("3ks")
        match()
        assertEquals(1, efforts().size)

        // The end moves past where the run turned back.
        segment("3ks", path = listOf(0.0 to 0.0, 0.0 to 1500.0))
        assertEquals(emptyList(), efforts(), "dropped at once")
        match()
        assertEquals(emptyList(), efforts())
    }

    @Test
    fun aDeletedAccountsSegmentsGo() {
        segment("3ks")
        runBlocking {
            indexer.handle(
                JetstreamEvent.Account(
                    did = alice,
                    time = "2026-10-15T12:00:00.000000Z",
                    account = JetstreamEvent.Account.Body(alice, active = false, status = "deleted"),
                )
            )
        }
        assertEquals(emptyList(), segmentRows())
        assertEquals(0L, cellCount())
    }

    @Test
    fun getSegmentServesTheSegmentAndTheViewersBest() = testApplication {
        application { appView(config, db, verifier) }
        matching(true)
        activity("3ka")
        track("3ka", speed = 5.0)
        activity("3kb")
        track("3kb", speed = 4.0)
        segment("3ks")
        match()

        val signedOut = client.xrpcOk(GetSegment.method, mapOf("uri" to segmentUri("3ks"))).segment
        assertEquals("Hill", signedOut.record.name)
        assertEquals("alice.test", signedOut.creator.handle.value)
        assertEquals(1000, (signedOut.distanceMeters / 10.0).roundToInt() * 10)
        assertNull(signedOut.viewer)

        val own = client.xrpcOk(GetSegment.method, mapOf("uri" to segmentUri("3ks")), viewer = alice).segment
        assertEquals(activityUri("3ka"), own.viewer?.best?.activity?.value)
        assertEquals(200, own.viewer?.best?.elapsed)
        assertEquals(true, own.viewer?.best?.best)

        assertNull(client.xrpcOk(GetSegment.method, mapOf("uri" to segmentUri("3ks")), viewer = bob).segment.viewer?.best)
        assertEquals("NotFound", client.xrpcError(GetSegment.method, mapOf("uri" to segmentUri("3kx"))))
    }

    @Test
    fun searchFindsSegmentsCrossingTheBox() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ka")
        segment("3kb", sport = "ride")
        // 20 km east, outside the box.
        segment("3kc", path = listOf(20_000.0 to 0.0, 20_000.0 to 1000.0))
        val sw = at(-500.0, 400.0)
        val ne = at(500.0, 600.0)
        fun box(extra: Map<String, String> = emptyMap()) =
            mapOf(
                "minLat" to "${(sw.lat * 1e7).roundToInt()}",
                "minLng" to "${(sw.lng * 1e7).roundToInt()}",
                "maxLat" to "${(ne.lat * 1e7).roundToInt()}",
                "maxLng" to "${(ne.lng * 1e7).roundToInt()}",
            ) + extra

        val all = client.xrpcOk(SearchSegments.method, box())
        assertEquals(listOf(segmentUri("3ka"), segmentUri("3kb")), all.segments.map { it.uri.value })
        assertNull(all.cursor)

        val runs = client.xrpcOk(SearchSegments.method, box(mapOf("sport" to "run")))
        assertEquals(listOf(segmentUri("3ka")), runs.segments.map { it.uri.value })

        val first = client.xrpcOk(SearchSegments.method, box(mapOf("limit" to "1")))
        assertEquals(listOf(segmentUri("3ka")), first.segments.map { it.uri.value })
        val second = client.xrpcOk(SearchSegments.method, box(mapOf("limit" to "1", "cursor" to first.cursor!!)))
        assertEquals(listOf(segmentUri("3kb")), second.segments.map { it.uri.value })

        val wide = mapOf("minLat" to "460000000", "minLng" to "70000000", "maxLat" to "480000000", "maxLng" to "100000000")
        assertEquals("InvalidRequest", client.xrpcError(SearchSegments.method, wide))
        val offGlobe = mapOf("minLat" to "899000000", "minLng" to "0", "maxLat" to "905000000", "maxLng" to "1000000")
        assertEquals("InvalidRequest", client.xrpcError(SearchSegments.method, offGlobe))
    }
}
