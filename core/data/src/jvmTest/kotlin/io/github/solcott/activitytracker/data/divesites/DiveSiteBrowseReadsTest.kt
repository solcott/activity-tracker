package io.github.solcott.activitytracker.data.divesites

import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRecord
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SearchDiveSiteNames
import io.github.solcott.activitytracker.lexicon.app.SearchDiveSites
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

/** The reads dive site browse makes (dive-sites/site-browse): every page of an area, capped, and a page of name results. */
class DiveSiteBrowseReadsTest {
    private val did = Did("did:plc:alice")
    private val box = SearchDiveSites.Params(minLat = 275_000_000, minLng = 338_000_000, maxLat = 276_000_000, maxLng = 339_000_000)

    private fun view(rkey: String) =
        DiveSiteView(
            AtUri.of(Did("did:plc:carol"), Nsid.FEED_DIVE_SITE, rkey),
            Cid("bafy$rkey"),
            ProfileViewBasic(Did("did:plc:carol"), Handle("carol.test")),
            DiveSiteRecord("Reef $rkey", 275_500_000, 338_500_000, 150, "osm", "ODbL-1.0", Instant.parse("2026-10-01T00:00:00Z")),
        )

    private val pages =
        mapOf(
            null to SearchDiveSites.Output(listOf(view("1")), "a"),
            "a" to SearchDiveSites.Output(listOf(view("2"), view("1")), null),
        )
    private val requests = mutableListOf<Map<String, List<String>>>()

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                val path = request.url.encodedPath.removePrefix("/xrpc/")
                if (path == "com.atproto.server.getServiceAuth") return@MockEngine respond("""{"token":"service-jwt"}""", headers = json)
                requests += request.url.parameters.entries().associate { it.key to it.value }
                when (path) {
                    Nsid.FEED_SEARCH_DIVE_SITES -> {
                        val page = pages.getValue(request.url.parameters["cursor"])
                        respond(AtJson.encodeToString(SearchDiveSites.Output.serializer(), page), headers = json)
                    }
                    Nsid.FEED_SEARCH_DIVE_SITE_NAMES ->
                        respond(
                            AtJson.encodeToString(
                                SearchDiveSiteNames.Output.serializer(),
                                SearchDiveSiteNames.Output(listOf(view("9")), "30"),
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
        val area = DiveSiteRepository(clients).areaSites(box)

        assertEquals(Outcome.Data(AreaSites(listOf(view("1"), view("2")), capped = false), Origin.Network), area)
        assertEquals(listOf(null, "a"), requests.map { it["cursor"]?.single() })
        assertEquals(listOf("100"), requests.first()["limit"])
    }

    @Test
    fun anAreaStopsAtItsCap() = runTest {
        val (_, _, clients) = signedInTestSession(http, did = did)
        val area = DiveSiteRepository(clients).areaSites(box, cap = 1)

        assertEquals(Outcome.Data(AreaSites(listOf(view("1")), capped = true), Origin.Network), area)
        assertEquals(1, requests.size)
    }

    @Test
    fun aNamePageSendsItsParams() = runTest {
        val (_, _, clients) = signedInTestSession(http, did = did)
        val page =
            DiveSiteRepository(clients).namesPage(SearchDiveSiteNames.Params(q = "thistle", nearLat = 275_500_000, nearLng = 338_500_000))

        assertEquals(Outcome.Data(Page(listOf(view("9")), Unit, "30"), Origin.Network), page)
        assertEquals(
            mapOf("q" to listOf("thistle"), "nearLat" to listOf("275500000"), "nearLng" to listOf("338500000")),
            requests.single(),
        )
    }
}
