package io.github.solcott.activitytracker.app

import co.touchlab.kermit.Logger
import com.slack.circuit.test.CircuitReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.browse.BrowseStart
import io.github.solcott.activitytracker.app.browse.MapBrowseEvent
import io.github.solcott.activitytracker.app.browse.NameScope
import io.github.solcott.activitytracker.app.divesite.DiveSiteBrowseEvent
import io.github.solcott.activitytracker.app.divesite.DiveSiteBrowsePresenter
import io.github.solcott.activitytracker.app.divesite.DiveSiteBrowseScreen
import io.github.solcott.activitytracker.app.divesite.DiveSiteBrowseState
import io.github.solcott.activitytracker.app.divesite.DiveSiteScreen
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.divesites.DiveSiteRepository
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRecord
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SearchDiveSiteNames
import io.github.solcott.activitytracker.lexicon.app.SearchDiveSites
import io.github.solcott.activitytracker.location.FakeLocationTracker
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.activitytracker.location.UnsupportedLocationTracker
import io.github.solcott.activitytracker.location.pointE7
import io.github.solcott.uistate.LoadStatus
import io.github.solcott.uistate.hasAnswer
import io.github.solcott.uistate.hasLoaded
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** Dive site browse (dive-sites/site-browse): where it opens, the area's sites nearest first, and name search with its scope. */
class DiveSiteBrowsePresenterTest {
    private val alice = Did("did:plc:alice")
    private val carol = Did("did:plc:carol")

    /** The Strait of Gubal: where the map's centre sits. */
    private val centre = GeoPoint(27.8136, 33.9206)

    /** A site [east] metres east of the centre. */
    private fun site(rkey: String, name: String, east: Double): DiveSiteView {
        val lng = centre.lng + east / (111_320.0 * kotlin.math.cos(Math.toRadians(centre.lat)))
        return DiveSiteView(
            AtUri.of(carol, Nsid.FEED_DIVE_SITE, rkey),
            Cid("bafy$rkey"),
            ProfileViewBasic(carol, Handle("carol.test")),
            DiveSiteRecord(name, e7(centre.lat), e7(lng), 150, "osm", "ODbL-1.0", Instant.parse("2026-10-01T00:00:00Z")),
        )
    }

    private val far = site("3kf", "Shag Rock", 3000.0)
    private val near = site("3kn", "SS Thistlegorm", 100.0)
    private val elsewhere = site("3ke", "Blue Hole", 200_000.0)

    private val requests = mutableListOf<Pair<String, Map<String, List<String>>>>()

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                val path = request.url.encodedPath.removePrefix("/xrpc/")
                val params = request.url.parameters.entries().associate { it.key to it.value }
                when (path) {
                    "com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    Nsid.FEED_SEARCH_DIVE_SITES -> {
                        requests += path to params
                        val output = SearchDiveSites.Output(listOf(far, near))
                        respond(AtJson.encodeToString(SearchDiveSites.Output.serializer(), output), headers = json)
                    }
                    Nsid.FEED_SEARCH_DIVE_SITE_NAMES -> {
                        requests += path to params
                        val q = params.getValue("q").single().lowercase()
                        val pool = if (params["minLat"] == null) listOf(near, far, elsewhere) else listOf(near, far)
                        val output = SearchDiveSiteNames.Output(pool.filter { q in it.record.name.lowercase() })
                        respond(AtJson.encodeToString(SearchDiveSiteNames.Output.serializer(), output), headers = json)
                    }
                    Nsid.FEED_GET_AUTHOR_ACTIVITIES ->
                        respond(
                            AtJson.encodeToString(GetAuthorActivities.Output.serializer(), GetAuthorActivities.Output(emptyList())),
                            headers = json,
                        )
                    else -> respond("", HttpStatusCode.NotFound)
                }
            },
        ) {
            expectSuccess = false
        }

    private val navigator = FakeNavigator(DiveSiteBrowseScreen)

    /** A box of [halfSpan] degrees either side of the centre, as the map reports it. */
    private fun viewport(halfSpan: Double = 0.05) =
        MapViewport(
            minLat = e7(centre.lat - halfSpan),
            minLng = e7(centre.lng - halfSpan),
            maxLat = e7(centre.lat + halfSpan),
            maxLng = e7(centre.lng + halfSpan),
            centerLat = e7(centre.lat),
            centerLng = e7(centre.lng),
        )

    private fun e7(degrees: Double) = (degrees * 1e7).roundToInt()

    private fun names(state: DiveSiteBrowseState) = state.area.data.sites.map { it.site.record.name }

    private val DiveSiteBrowseState.areaLoaded
        get() = area.hasLoaded && area.status is LoadStatus.Idle

    private val DiveSiteBrowseState.resultsAnswered
        get() = results.hasAnswer && results.refresh is LoadStatus.Idle

    private suspend fun test(
        tracker: LocationTracker = FakeLocationTracker(listOf(sample())),
        block: suspend CircuitReceiveTurbine<DiveSiteBrowseState>.() -> Unit,
    ) {
        val (db, _, clients) = signedInTestSession(http, did = alice)
        val sites = DiveSiteRepository(clients)
        val feed = FeedRepository(db, clients, LocalLikes(), Logger)
        presenterTestOf({ DiveSiteBrowsePresenter(navigator, sites, feed, tracker) }, block = block)
    }

    private fun sample() =
        LocationSample(centre.lat, centre.lng, altitude = null, speed = null, accuracy = null, time = Instant.parse("2026-11-04T12:00:00Z"))

    @Test
    fun opensAtTheLocationAndListsTheAreasSitesNearestFirst() = runTest {
        test {
            val placed = awaitUntil { it.browse.start == BrowseStart.Placed }
            assertEquals(centre.lat, placed.browse.camera?.lat)

            placed.browse.eventSink(MapBrowseEvent.ViewportChanged(viewport()))
            val loaded = awaitUntil { it.areaLoaded }
            assertEquals(listOf("SS Thistlegorm", "Shag Rock"), names(loaded), "nearest first")
            assertEquals(100, loaded.area.data.sites.first().metersAway)
            assertEquals(setOf(near.uri.value, far.uri.value), loaded.browse.mapLines.map { it.uri }.toSet(), "both outlines drawn")

            loaded.browse.eventSink(MapBrowseEvent.Select(near.uri))
            assertTrue(awaitUntil { it.browse.selected != null }.isSelected(near))
            loaded.eventSink(DiveSiteBrowseEvent.Open(near.uri))
            assertEquals(DiveSiteScreen(near.uri.value), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun tooWideAnAreaAsksNothing() = runTest {
        test {
            awaitUntil { it.browse.start == BrowseStart.Placed }.browse.eventSink(MapBrowseEvent.ViewportChanged(viewport(halfSpan = 1.0)))
            val wide = awaitUntil { it.browse.tooWide }
            testScheduler.advanceUntilIdle()
            assertTrue(requests.none { it.first == Nsid.FEED_SEARCH_DIVE_SITES })
            assertFalse(wide.area.hasLoaded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aNameSearchLooksInTheAreaThenEverywhere() = runTest {
        test {
            val placed = awaitUntil { it.browse.start == BrowseStart.Placed }
            placed.browse.eventSink(MapBrowseEvent.ViewportChanged(viewport()))
            awaitUntil { it.areaLoaded }
            placed.browse.query.edit { append("blue") }
            val none = awaitUntil { it.resultsAnswered && it.browse.query.text.toString() == "blue" }
            assertEquals(emptyList(), none.results.items)
            assertTrue(none.browse.offersSearchEverywhere)
            val sent = requests.last { it.first == Nsid.FEED_SEARCH_DIVE_SITE_NAMES }.second
            assertEquals(listOf("${e7(centre.lat - 0.05)}"), sent["minLat"], "this area")

            none.browse.eventSink(MapBrowseEvent.SearchEverywhere)
            val found = awaitUntil { it.browse.scope == NameScope.Everywhere && it.resultsAnswered && it.results.items.isNotEmpty() }
            assertEquals(listOf("Blue Hole"), found.results.items.map { it.record.name })
            assertEquals(listOf(elsewhere.uri.value), found.browse.mapLines.map { it.uri }, "the map draws the results")
            val everywhere = requests.last { it.first == Nsid.FEED_SEARCH_DIVE_SITE_NAMES }.second
            assertNull(everywhere["minLat"], "no box")
            assertEquals(listOf("${e7(centre.lat)}"), everywhere["nearLat"], "still nearest first")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRowTapSelectsThenOpensAndFramesANameResultOutOfView() = runTest {
        test {
            val placed = awaitUntil { it.browse.start == BrowseStart.Placed }
            val opening = placed.browse.camera
            placed.browse.eventSink(MapBrowseEvent.ViewportChanged(viewport()))
            val loaded = awaitUntil { it.areaLoaded }

            // Every site an area lists is in the map's own view, so selecting one never moves the map.
            loaded.eventSink(loaded.tapRowEvent(far))
            assertEquals(opening, awaitUntil { it.browse.selected == far.uri }.browse.camera, "the map stays")
            navigator.expectNoGoToEvents()

            loaded.browse.eventSink(MapBrowseEvent.ScopeChanged(NameScope.Everywhere))
            loaded.browse.query.edit { append("blue") }
            val found = awaitUntil { it.resultsAnswered && it.results.items.isNotEmpty() }
            found.eventSink(found.tapRowEvent(elsewhere))
            val framed = awaitUntil { it.browse.selected == elsewhere.uri && it.browse.camera != opening }
            val at = pointE7(elsewhere.record.lat, elsewhere.record.lng)
            assertEquals(MapBounds.of(listOf(at)), framed.browse.camera?.bounds, "Blue Hole lies far east of the view")
            navigator.expectNoGoToEvents()

            framed.eventSink(framed.tapRowEvent(elsewhere))
            assertEquals(DiveSiteScreen(elsewhere.uri.value), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun withoutAMapANameSearchLooksEverywhereByMatchAlone() = runTest {
        test(UnsupportedLocationTracker()) {
            val state = awaitUntil { it.browse.start != BrowseStart.Locating }
            assertFalse(state.browse.hasArea)
            state.browse.query.edit { append("thistle") }
            assertEquals(
                listOf("SS Thistlegorm"),
                awaitUntil { it.resultsAnswered && it.results.items.isNotEmpty() }.results.items.map { it.record.name },
            )
            assertEquals(setOf("q"), requests.single { it.first == Nsid.FEED_SEARCH_DIVE_SITE_NAMES }.second.keys)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
