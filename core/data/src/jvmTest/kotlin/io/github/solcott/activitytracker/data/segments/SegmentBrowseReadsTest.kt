package io.github.solcott.activitytracker.data.segments

import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SearchSegmentNames
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.dataresult.Origin
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.Page
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** The reads segment browse makes (segments/segment-browse): every page of an area, capped, and a page of name results. */
class SegmentBrowseReadsTest {
    private val did = Did("did:plc:alice")
    private val box = SearchSegments.Params(minLat = 443_000_000, minLng = -728_000_000, maxLat = 444_000_000, maxLng = -727_000_000)

    private fun view(rkey: String) =
        SegmentView(
            AtUri.of(Did("did:plc:carol"), Nsid.FEED_SEGMENT, rkey),
            Cid("bafy$rkey"),
            ProfileViewBasic(Did("did:plc:carol"), Handle("carol.test")),
            SegmentRecord("Hill $rkey", "run", "_p~iF~ps|U", "athlete", "CC0-1.0", Instant.parse("2026-10-01T00:00:00Z")),
            distanceMeters = 1000,
        )

    /** Area pages by cursor: none, "a" and "b" hold one segment each, "e" holds none, and "b"'s page ends the search. */
    private val pages =
        mapOf(
            null to SearchSegments.Output(listOf(view("1")), "a"),
            "a" to SearchSegments.Output(emptyList(), "b"),
            "b" to SearchSegments.Output(listOf(view("2"), view("1")), null),
        )
    private val requests = mutableListOf<Map<String, List<String>>>()

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                val path = request.url.encodedPath.removePrefix("/xrpc/")
                if (path == "com.atproto.server.getServiceAuth") return@MockEngine respond("""{"token":"service-jwt"}""", headers = json)
                val params = request.url.parameters.entries().associate { it.key to it.value }
                requests += params
                when (path) {
                    Nsid.FEED_SEARCH_SEGMENTS -> {
                        val page = pages.getValue(request.url.parameters["cursor"])
                        respond(AtJson.encodeToString(SearchSegments.Output.serializer(), page), headers = json)
                    }
                    Nsid.FEED_SEARCH_SEGMENT_NAMES ->
                        respond(
                            AtJson.encodeToString(
                                SearchSegmentNames.Output.serializer(),
                                SearchSegmentNames.Output(listOf(view("9")), "30"),
                            ),
                            headers = json,
                        )
                    else -> respondError(HttpStatusCode.NotFound)
                }
            },
        )

    @Test
    fun anAreaFollowsEveryCursorOnce() = runTest {
        val (_, _, clients) = signedInTestSession(http, did = did)
        val area = SegmentRepository(clients).areaSegments(box)

        assertEquals(Outcome.Data(AreaSegments(listOf(view("1"), view("2")), capped = false), Origin.Network), area)
        assertEquals(listOf(null, "a", "b"), requests.map { it["cursor"]?.single() })
        assertEquals(listOf("100"), requests.first()["limit"])
    }

    @Test
    fun anAreaStopsAtItsCap() = runTest {
        val (_, _, clients) = signedInTestSession(http, did = did)
        val segments = SegmentRepository(clients)

        assertEquals(Outcome.Data(AreaSegments(listOf(view("1")), capped = true), Origin.Network), segments.areaSegments(box, cap = 1))
        assertEquals(1, requests.size)
        requests.clear()
        val bounded = segments.areaSegments(box, maxRequests = 2)
        assertEquals(Outcome.Data(AreaSegments(listOf(view("1")), capped = true), Origin.Network), bounded, "pages with none count too")
        assertEquals(2, requests.size)
    }

    @Test
    fun aNamePageSendsItsParams() = runTest {
        val (_, _, clients) = signedInTestSession(http, did = did)
        val page =
            SegmentRepository(clients)
                .namesPage(SearchSegmentNames.Params(q = "hog", nearLat = 443_500_000, nearLng = -727_500_000, sport = "ride"))

        assertEquals(Outcome.Data(Page(listOf(view("9")), Unit, "30"), Origin.Network), page)
        assertEquals(
            mapOf("q" to listOf("hog"), "nearLat" to listOf("443500000"), "nearLng" to listOf("-727500000"), "sport" to listOf("ride")),
            requests.single(),
        )
    }
}
