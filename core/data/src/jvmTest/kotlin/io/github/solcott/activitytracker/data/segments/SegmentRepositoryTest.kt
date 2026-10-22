package io.github.solcott.activitytracker.data.segments

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.CheckSegment
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.GetActivitySegments
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.GetSegmentEfforts
import io.github.solcott.activitytracker.lexicon.app.GetSegmentLeaderboard
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.LeaderboardPeriod
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.SegmentViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentViewerState
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.Origin
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.Page
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class SegmentRepositoryTest {
    private val did = Did("did:plc:alice")
    private val segmentUri = AtUri.of(Did("did:plc:carol"), Nsid.FEED_SEGMENT, "3ks")
    private val activityUri = AtUri.of(did, Nsid.FEED_ACTIVITY, "3ka")
    private val requests = mutableListOf<Pair<String, Map<String, List<String>>>>()
    /** Bodies of the POSTs the client sent, by method. */
    private val posted = mutableMapOf<String, String>()

    private val effort =
        EffortView(
            SegmentViewBasic(segmentUri, "Hill", "run", 1000),
            activityUri,
            startOffset = 20,
            elapsed = 200,
            startedOn = "2026-10-06",
            best = true,
        )
    private val view =
        SegmentView(
            segmentUri,
            Cid("bafyseg"),
            ProfileViewBasic(Did("did:plc:carol"), Handle("carol.test")),
            SegmentRecord("Hill", "run", "_p~iF~ps|U", "athlete", "CC0-1.0", Instant.parse("2026-10-01T00:00:00Z")),
            distanceMeters = 1000,
            viewer = SegmentViewerState(effort),
        )

    private val entry = LeaderboardEntry(1, ProfileViewBasic(did, Handle("alice.test")), effort)
    private val goneUri = AtUri.of(Did("did:plc:carol"), Nsid.FEED_SEGMENT, "3kgone")
    private val brokenUri = AtUri.of(Did("did:plc:carol"), Nsid.FEED_SEGMENT, "3kbroken")

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                val path = request.url.encodedPath
                if (path == "/xrpc/com.atproto.server.getServiceAuth")
                    return@MockEngine respond("""{"token":"service-jwt"}""", headers = json)
                requests += path.removePrefix("/xrpc/") to request.url.parameters.entries().associate { it.key to it.value }
                if (request.method.value == "POST") posted[path.removePrefix("/xrpc/")] = request.body.toByteArray().decodeToString()
                val body =
                    when (path.removePrefix("/xrpc/")) {
                        Nsid.FEED_GET_SEGMENT ->
                            when (request.url.parameters["uri"]) {
                                goneUri.value ->
                                    return@MockEngine respond(
                                        """{"error":"NotFound","message":"Segment not found"}""",
                                        HttpStatusCode.BadRequest,
                                        json,
                                    )
                                brokenUri.value -> return@MockEngine respondError(HttpStatusCode.InternalServerError)
                                else -> AtJson.encodeToString(GetSegment.Output.serializer(), GetSegment.Output(view))
                            }
                        Nsid.FEED_GET_ACTIVITY_SEGMENTS ->
                            AtJson.encodeToString(GetActivitySegments.Output.serializer(), GetActivitySegments.Output(listOf(effort)))
                        Nsid.FEED_GET_SEGMENT_EFFORTS ->
                            AtJson.encodeToString(GetSegmentEfforts.Output.serializer(), GetSegmentEfforts.Output(listOf(effort), "next"))
                        Nsid.FEED_GET_SEGMENT_LEADERBOARD ->
                            AtJson.encodeToString(
                                GetSegmentLeaderboard.Output.serializer(),
                                GetSegmentLeaderboard.Output(listOf(entry), viewer = entry, cursor = "200::${did.value}"),
                            )
                        Nsid.FEED_CHECK_SEGMENT ->
                            AtJson.encodeToString(CheckSegment.Output.serializer(), CheckSegment.Output(listOf(view), remaining = 8))
                        Nsid.FEED_SEARCH_SEGMENTS ->
                            return@MockEngine respond(
                                """{"error":"InvalidRequest","message":"too wide"}""",
                                HttpStatusCode.BadRequest,
                                json,
                            )
                        else -> return@MockEngine respondError(HttpStatusCode.NotFound)
                    }
                respond(body, headers = json)
            }
        )

    @Test
    fun wrapsTheSegmentQueries() = runTest {
        val (_, _, clients) = signedInTestSession(http, did = did)
        val segments = SegmentRepository(clients)

        assertEquals(view, segments.segment(segmentUri))
        assertEquals(listOf(effort), segments.activitySegments(activityUri))
        assertEquals("next", segments.efforts(segmentUri).cursor)
        segments.efforts(segmentUri, actor = "bob.test", cursor = "c1")

        assertEquals(
            listOf(
                Nsid.FEED_GET_SEGMENT to mapOf("uri" to listOf(segmentUri.value)),
                Nsid.FEED_GET_ACTIVITY_SEGMENTS to mapOf("activity" to listOf(activityUri.value)),
                Nsid.FEED_GET_SEGMENT_EFFORTS to mapOf("segment" to listOf(segmentUri.value), "actor" to listOf(did.value)),
                Nsid.FEED_GET_SEGMENT_EFFORTS to
                    mapOf("segment" to listOf(segmentUri.value), "actor" to listOf("bob.test"), "cursor" to listOf("c1")),
            ),
            requests,
        )
    }

    @Test
    fun leaderboardSendsItsFilters() = runTest {
        val (_, _, clients) = signedInTestSession(http, did = did)
        val segments = SegmentRepository(clients)

        val board = segments.leaderboardPage(segmentUri, LeaderboardPeriod.MONTH, LeaderboardScope.FOLLOWING, eBike = true, cursor = "c1")
        assertEquals(Outcome.Data(Page(listOf(entry), entry, "200::${did.value}"), Origin.Network), board)
        segments.leaderboardPage(segmentUri, LeaderboardPeriod.ALL_TIME, LeaderboardScope.EVERYONE, eBike = false)

        assertEquals(
            listOf(
                Nsid.FEED_GET_SEGMENT_LEADERBOARD to
                    mapOf(
                        "segment" to listOf(segmentUri.value),
                        "period" to listOf("month"),
                        "scope" to listOf("following"),
                        "eBike" to listOf("true"),
                        "cursor" to listOf("c1"),
                    ),
                Nsid.FEED_GET_SEGMENT_LEADERBOARD to
                    mapOf("segment" to listOf(segmentUri.value), "period" to listOf("allTime"), "scope" to listOf("everyone")),
            ),
            requests,
        )
    }

    @Test
    fun aLeaderboardPageFailsWithWhy() = runTest {
        val failing =
            HttpClient(
                MockEngine { request ->
                    if (request.url.encodedPath == "/xrpc/com.atproto.server.getServiceAuth")
                        respond("""{"token":"service-jwt"}""", headers = headersOf("Content-Type", "application/json"))
                    else respondError(HttpStatusCode.ServiceUnavailable)
                }
            )
        val (_, _, clients) = signedInTestSession(failing, did = did)

        val page = SegmentRepository(clients).leaderboardPage(segmentUri, LeaderboardPeriod.ALL_TIME, LeaderboardScope.EVERYONE, false)

        assertEquals(DataError.Http(503), (page as Outcome.Error).cause)
    }

    @Test
    fun aLookupAnswersNullForASegmentTheAppViewNoLongerHas() = runTest {
        val (_, _, clients) = signedInTestSession(http, did = did)
        val segments = SegmentRepository(clients)

        assertEquals(Outcome.Data(view, Origin.Network), segments.segmentLookup(segmentUri))
        assertEquals(Outcome.Data(null, Origin.Network), segments.segmentLookup(goneUri))
        assertEquals(DataError.Http(500), (segments.segmentLookup(brokenUri) as Outcome.Error).cause)
    }

    @Test
    fun anAppViewErrorThrows() = runTest {
        val (_, _, clients) = signedInTestSession(http, did = did)
        assertFailsWith<Exception> { SegmentRepository(clients).search(SearchSegments.Params(0, 0, 1, 1)) }
    }

    @Test
    fun checkPostsTheLine() = runTest {
        val (_, _, clients) = signedInTestSession(http, did = did)
        val output = SegmentRepository(clients).check("ride", "_p~iF~ps|U")
        assertEquals(CheckSegment.Output(listOf(view), remaining = 8), output)
        assertEquals(
            CheckSegment.Input("ride", "_p~iF~ps|U"),
            AtJson.decodeFromString(CheckSegment.Input.serializer(), posted.getValue(Nsid.FEED_CHECK_SEGMENT)),
        )
    }

    @Test
    fun createQueuesAPublicSegmentInTheRepo() = runTest {
        val (db, _, clients) = signedInTestSession(http, did = did)
        val segments = SegmentRepository(clients)
        val publisher =
            SegmentPublisher(clients, OutboxWriter(db, clients, Clock.System), OutboxProcessor(db, clients, Logger), segments, Clock.System)
        val source = StrongRef(activityUri, Cid("bafyactivity"))

        val uri = publisher.create(activityUri, "  Mill Hill climb ", "ride", "_p~iF~ps|U", derivedFrom = source)
        val plain = publisher.create(activityUri, "Lane", "run", "_p~iF~ps|U")
        assertEquals(setOf(activityUri), segments.createdFrom.value)
        segments.dismissCreated(activityUri)
        assertEquals(emptySet(), segments.createdFrom.value)

        // The mock PDS refuses putRecord, so both stay queued, in order.
        val rows = db.outbox().pending(did.value)
        assertEquals(listOf(uri.rkey, plain.rkey), rows.map { it.rkey })
        assertEquals(listOf(Nsid.FEED_SEGMENT, Nsid.FEED_SEGMENT), rows.map { it.collection })
        assertEquals(listOf(null, null), rows.map { it.space }, "in the repo")
        val record = AtJson.decodeFromString(SegmentRecord.serializer(), rows.first().recordJson!!)
        assertEquals("Mill Hill climb", record.name)
        assertEquals("ride", record.sport)
        assertEquals("athlete", record.source)
        assertEquals(SegmentPublisher.LICENSE, record.license)
        assertEquals(source, record.derivedFrom)
        assertNull(AtJson.decodeFromString(SegmentRecord.serializer(), rows.last().recordJson!!).derivedFrom)
        assertEquals(Nsid.FEED_SEGMENT, uri.collection)
        assertEquals(did.value, uri.authority)
    }
}
