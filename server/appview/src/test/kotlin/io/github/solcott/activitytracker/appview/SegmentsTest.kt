package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.SegmentCellIndex
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.atproto.segmentStarRkey
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.CheckSegment
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.SegmentStarRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** `feed.segment` records (segments/segment-records): indexed from the repo only, validated, and served by URI and by area. */
class SegmentsTest : SegmentTestBase() {
    private fun segmentRows(): List<String> = runBlocking { db.tx { Segments.selectAll().map { it[Segments.uri] } } }

    private fun cellCount(): Long = runBlocking { db.tx { SegmentCellIndex.selectAll().count() } }

    private data class Row(val status: String, val duplicateOf: String?, val firstIndexedAt: Long)

    private fun row(uri: String): Row = runBlocking {
        db.tx {
            Segments.selectAll()
                .where { Segments.uri eq uri }
                .single()
                .let { Row(it[Segments.status], it[Segments.duplicateOf], it[Segments.firstIndexedAt]) }
        }
    }

    private fun setStatus(status: String, vararg uris: String) = runBlocking {
        db.tx { uris.forEach { uri -> Segments.update({ Segments.uri eq uri }) { it[Segments.status] = status } } }
    }

    private suspend fun HttpClient.check(viewer: String?, sport: String, line: String): HttpResponse =
        post("/xrpc/${Nsid.FEED_CHECK_SEGMENT}") {
            viewer?.let {
                header(HttpHeaders.Authorization, "Bearer ${keys.getValue(it).serviceJwt(it, service, Nsid.FEED_CHECK_SEGMENT)}")
            }
            contentType(ContentType.Application.Json)
            setBody(AtJson.encodeToString(CheckSegment.Input.serializer(), CheckSegment.Input(sport, line)))
        }

    private suspend fun HttpClient.checkOk(viewer: String, sport: String, line: String): CheckSegment.Output {
        val response = check(viewer, sport, line)
        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status, body)
        return AtJson.decodeFromString(CheckSegment.Output.serializer(), body)
    }

    /** A climb of about 2.6 km, with a turn so a reversed copy can't line up. */
    private val climb = listOf(0.0 to 0.0, 400.0 to 800.0, 300.0 to 1600.0, 900.0 to 2400.0)

    /** [climb] traced again by someone else: its points 10 m east, and ends 10 m off. */
    private val climbAgain = climb.map { (e, n) -> e + 10.0 to n }

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

    /** Each table's row count, to show a write stored nothing anywhere. */
    private fun rowCounts(): Map<String, Long> = runBlocking {
        db.tx {
            val tables = mutableListOf<String>()
            exec("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'") { rs ->
                while (rs.next()) tables += rs.getString(1)
            }
            tables.associateWith { table ->
                exec("SELECT COUNT(*) FROM \"$table\"") { rs ->
                    rs.next()
                    rs.getLong(1)
                } ?: 0L
            }
        }
    }

    @Test
    fun aSegmentStarIsNeverStoredOrServed() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks")
        val segment = AtUri(segmentUri("3ks"))
        val before = rowCounts()

        val star = SegmentStarRecord(subject = StrongRef(segment, Cid("bafyseg")), createdAt = Instant.parse("2026-10-21T12:00:00Z"))
        val rkey = runBlocking { segmentStarRkey(segment) }
        spaceWrite(self(bob), Nsid.FEED_SEGMENT_STAR, rkey, AtJson.encodeToString(SegmentStarRecord.serializer(), star))
        runBlocking { indexer.spaceDelete(self(bob), Nsid.FEED_SEGMENT_STAR, rkey) }
        spaceWrite(self(bob), Nsid.FEED_SEGMENT_STAR, rkey, AtJson.encodeToString(SegmentStarRecord.serializer(), star))

        assertFalse(Nsid.FEED_SEGMENT_STAR in Nsid.indexedCollections)
        for (skey in listOf(Visibility.FOLLOWERS, Visibility.MUTUALS, AthleteSpace.SELF, AthleteSpace.INTERACTIONS)) {
            assertFalse(Nsid.FEED_SEGMENT_STAR in Indexer.spaceCollections(skey), skey)
        }
        assertEquals(before, rowCounts())
        val body = client.xrpc(GetSegment.method, mapOf("uri" to segment.value), viewer = bob).bodyAsText()
        val view = AtJson.parseToJsonElement(body).jsonObject.getValue("segment").jsonObject
        assertFalse("star" in body.lowercase(), body)
        assertFalse("viewer" in view && "star" in view.getValue("viewer").toString(), body)
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
        setStatus(SegmentStatus.ACTIVE, segmentUri("3ka"), segmentUri("3kb"), segmentUri("3kc"))
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

    @Test
    fun minimumLengthDependsOnTheSportGroup() {
        val short = listOf(0.0 to 0.0, 0.0 to 350.0)
        segment("3ka", path = short, sport = "ride")
        segment("3kb", path = short, sport = "run")
        assertEquals(listOf(segmentUri("3kb")), segmentRows())
    }

    @Test
    fun aNewSegmentIsProposedAndKeepsWhenItWasFirstIndexed() {
        segment("3ks")
        val first = row(segmentUri("3ks"))
        assertEquals(SegmentStatus.PROPOSED, first.status)
        Thread.sleep(5)
        segment("3ks", name = "Renamed")
        segment("3ks", path = listOf(0.0 to 0.0, 0.0 to 1200.0))
        assertEquals(first.firstIndexedAt, row(segmentUri("3ks")).firstIndexedAt)
    }

    @Test
    fun aCreatorMayIndexTenNewSegmentsADay() {
        (0 until 11).forEach { k -> segment("3k$k", path = listOf(k * 2000.0 to 0.0, k * 2000.0 to 1000.0)) }
        assertEquals(10, segmentRows().size)
        assertTrue(segmentUri("3k10") !in segmentRows())

        segment("3k0", path = listOf(0.0 to 0.0, 0.0 to 1000.0), name = "Renamed")
        assertEquals(
            "Renamed",
            runBlocking { db.tx { Segments.selectAll().where { Segments.uri eq segmentUri("3k0") }.single()[Segments.name] } },
        )
        segment("3kx", path = straight, did = bob)
        assertTrue(segmentUri("3kx", bob) in segmentRows(), "the limit is per creator")
    }

    @Test
    fun aDuplicateOfAnEarlierSegmentIsMarkedAndNeverMatched() {
        segment("3ka", path = climb)
        segment("3kb", path = climbAgain, did = bob)
        assertEquals(
            Row(SegmentStatus.DUPLICATE, segmentUri("3ka"), row(segmentUri("3kb", bob)).firstIndexedAt),
            row(segmentUri("3kb", bob)),
        )
        assertEquals(listOf(SegmentJobs.SEGMENT to segmentUri("3ka")), jobs(), "only the original is queued")

        segment("3kc", path = climb.reversed(), did = bob)
        assertEquals(SegmentStatus.PROPOSED, row(segmentUri("3kc", bob)).status, "the other direction")
        segment("3kd", path = climbAgain, sport = "ride", did = bob)
        assertEquals(SegmentStatus.PROPOSED, row(segmentUri("3kd", bob)).status, "another sport group")
        segment("3ke", path = climb + (900.0 to 4400.0), did = bob)
        assertEquals(SegmentStatus.PROPOSED, row(segmentUri("3ke", bob)).status, "a longer stretch")
        assertEquals(SegmentStatus.PROPOSED, row(segmentUri("3ka")).status)
    }

    @Test
    fun aDuplicateIsCheckedAgainWhenItsOriginalGoes() {
        matching(true, did = bob)
        activity("3kr", did = bob)
        track("3kr", pieces = listOf(listOf(0.0 to -100.0) + climb + listOf(900.0 to 2500.0)), did = bob)
        segment("3ka", path = climb)
        match()
        segment("3kb", path = climbAgain, did = carol)
        segment("3kc", path = climb.map { (e, n) -> e - 10.0 to n }, did = carol)
        match()
        assertEquals(listOf(segmentUri("3ka")), efforts().map { it.segment })

        commit(alice, Nsid.FEED_SEGMENT, "3ka", null)
        assertEquals(Row(SegmentStatus.PROPOSED, null, row(segmentUri("3kb", carol)).firstIndexedAt), row(segmentUri("3kb", carol)))
        assertEquals(SegmentStatus.DUPLICATE, row(segmentUri("3kc", carol)).status)
        assertEquals(segmentUri("3kb", carol), row(segmentUri("3kc", carol)).duplicateOf, "the earliest left")
        match()
        assertEquals(listOf(segmentUri("3kb", carol)), efforts().map { it.segment }, "past tracks matched")
    }

    @Test
    fun aDuplicateIsCheckedAgainWhenItsOriginalMoves() {
        segment("3ka", path = climb)
        segment("3kb", path = climbAgain, did = bob)
        segment("3ka", path = listOf(5000.0 to 0.0, 5000.0 to 1000.0))
        assertEquals(SegmentStatus.PROPOSED, row(segmentUri("3kb", bob)).status)
        assertNull(row(segmentUri("3kb", bob)).duplicateOf)
    }

    @Test
    fun viewsCarryTheStatus() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ka", path = climb)
        segment("3kb", path = climbAgain, did = bob)
        val original = client.xrpcOk(GetSegment.method, mapOf("uri" to segmentUri("3ka"))).segment
        assertEquals(SegmentStatus.PROPOSED, original.status)
        assertNull(original.duplicateOf)
        val duplicate = client.xrpcOk(GetSegment.method, mapOf("uri" to segmentUri("3kb", bob))).segment
        assertEquals(SegmentStatus.DUPLICATE, duplicate.status)
        assertEquals(segmentUri("3ka"), duplicate.duplicateOf?.value)
    }

    @Test
    fun searchListsProposedSegmentsOnlyToTheirCreatorAndRiders() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks")
        matching(true, did = bob)
        activity("3kr", did = bob)
        track("3kr", did = bob)
        match()
        segment("3kd", path = straight.map { (e, n) -> e + 8.0 to n }, did = carol)
        segment("3kz", path = listOf(200.0 to 0.0, 200.0 to 1000.0), did = carol)
        setStatus(SegmentStatus.ARCHIVED, segmentUri("3kz", carol))
        val sw = at(-500.0, 400.0)
        val ne = at(500.0, 600.0)
        val box =
            mapOf(
                "minLat" to "${(sw.lat * 1e7).roundToInt()}",
                "minLng" to "${(sw.lng * 1e7).roundToInt()}",
                "maxLat" to "${(ne.lat * 1e7).roundToInt()}",
                "maxLng" to "${(ne.lng * 1e7).roundToInt()}",
            )
        suspend fun listed(viewer: String?) = client.xrpcOk(SearchSegments.method, box, viewer).segments.map { it.uri.value }

        assertEquals(listOf(segmentUri("3ks")), listed(alice), "the creator")
        assertEquals(listOf(segmentUri("3ks")), listed(bob), "a rider")
        assertEquals(emptyList(), listed(carol), "a stranger; her own duplicate and archived segments never")
        assertEquals(emptyList(), listed(null))

        setStatus(SegmentStatus.ACTIVE, segmentUri("3ks"))
        assertEquals(listOf(segmentUri("3ks")), listed(carol))
        assertEquals(listOf(segmentUri("3ks")), listed(null))
    }

    @Test
    fun checkSegmentFindsDuplicatesAndTheAllowance() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ka", path = climb)
        segment("3kb", path = listOf(5000.0 to 0.0, 5000.0 to 1000.0), did = bob)
        segment("3kc", path = listOf(7000.0 to 0.0, 7000.0 to 1000.0), did = bob)

        val found = client.checkOk(bob, "run", lineOf(climbAgain))
        assertEquals(listOf(segmentUri("3ka")), found.duplicates.map { it.uri.value })
        assertEquals(8, found.remaining)
        assertEquals(emptyList(), client.checkOk(bob, "ride", lineOf(climbAgain)).duplicates, "another sport group")

        val none = client.checkOk(alice, "run", lineOf(listOf(9000.0 to 0.0, 9000.0 to 1000.0)))
        assertEquals(emptyList(), none.duplicates)
        assertEquals(9, none.remaining)

        for ((sport, line) in
            listOf("run" to "_p~iF~ps|U_", "skydive" to lineOf(climb), "ride" to lineOf(listOf(0.0 to 0.0, 0.0 to 350.0)))) {
            val response = client.check(alice, sport, line)
            assertEquals(HttpStatusCode.BadRequest, response.status, sport)
            assertTrue("InvalidRequest" in response.bodyAsText())
        }
        assertEquals(HttpStatusCode.Unauthorized, client.check(null, "run", lineOf(climb)).status)
        assertEquals(1, segmentRows().count { it == segmentUri("3ka") }, "checking writes nothing")
    }
}
