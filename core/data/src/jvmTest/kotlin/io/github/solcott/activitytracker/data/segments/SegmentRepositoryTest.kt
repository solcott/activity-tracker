package io.github.solcott.activitytracker.data.segments

import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.GetActivitySegments
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.GetSegmentEfforts
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.SegmentViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentViewerState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class SegmentRepositoryTest {
    private val did = Did("did:plc:alice")
    private val segmentUri = AtUri.of(Did("did:plc:carol"), Nsid.FEED_SEGMENT, "3ks")
    private val activityUri = AtUri.of(did, Nsid.FEED_ACTIVITY, "3ka")
    private val requests = mutableListOf<Pair<String, Map<String, List<String>>>>()

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

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                val path = request.url.encodedPath
                if (path == "/xrpc/com.atproto.server.getServiceAuth")
                    return@MockEngine respond("""{"token":"service-jwt"}""", headers = json)
                requests += path.removePrefix("/xrpc/") to request.url.parameters.entries().associate { it.key to it.value }
                val body =
                    when (path.removePrefix("/xrpc/")) {
                        Nsid.FEED_GET_SEGMENT -> AtJson.encodeToString(GetSegment.Output.serializer(), GetSegment.Output(view))
                        Nsid.FEED_GET_ACTIVITY_SEGMENTS ->
                            AtJson.encodeToString(GetActivitySegments.Output.serializer(), GetActivitySegments.Output(listOf(effort)))
                        Nsid.FEED_GET_SEGMENT_EFFORTS ->
                            AtJson.encodeToString(GetSegmentEfforts.Output.serializer(), GetSegmentEfforts.Output(listOf(effort), "next"))
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
    fun anAppViewErrorThrows() = runTest {
        val (_, _, clients) = signedInTestSession(http, did = did)
        assertFailsWith<Exception> { SegmentRepository(clients).search(SearchSegments.Params(0, 0, 1, 1)) }
    }
}
