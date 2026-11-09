package io.github.solcott.activitytracker.app

import co.touchlab.kermit.Logger
import com.slack.circuit.test.CircuitReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.segment.BrowseStart
import io.github.solcott.activitytracker.app.segment.NameScope
import io.github.solcott.activitytracker.app.segment.SegmentBrowseEvent
import io.github.solcott.activitytracker.app.segment.SegmentBrowsePresenter
import io.github.solcott.activitytracker.app.segment.SegmentBrowseScreen
import io.github.solcott.activitytracker.app.segment.SegmentBrowseState
import io.github.solcott.activitytracker.app.segment.SegmentScreen
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SearchSegmentNames
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.location.FakeLocationTracker
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.location.TrackMath
import io.github.solcott.activitytracker.location.UnsupportedLocationTracker
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest

/** Segment browse (segments/segment-browse): where it opens, the area's runs nearest first, and name search with its scope. */
class SegmentBrowsePresenterTest {
    private val alice = Did("did:plc:alice")
    private val carol = Did("did:plc:carol")

    /** Burlington, Vermont: where the map's centre sits. */
    private val centre = GeoPoint(44.4759, -73.2121)

    /** A 1 km run north from a point [east] metres east of the centre, or a lift. */
    private fun segment(rkey: String, name: String, east: Double, sport: String = "run", kind: String? = null): SegmentView {
        val lng = centre.lng + east / (111_320.0 * kotlin.math.cos(Math.toRadians(centre.lat)))
        val line = SegmentGeometry.encodePolyline(listOf(GeoPoint(centre.lat, lng), GeoPoint(centre.lat + 0.009, lng)))
        return SegmentView(
            AtUri.of(carol, Nsid.FEED_SEGMENT, rkey),
            Cid("bafy$rkey"),
            ProfileViewBasic(carol, Handle("carol.test")),
            SegmentRecord(name, sport, line, "athlete", "CC0-1.0", Instant.parse("2026-10-01T00:00:00Z"), kind = kind),
            distanceMeters = 1000,
        )
    }

    private val far = segment("3kf", "Far Climb", 3000.0)
    private val near = segment("3kn", "Near Loop", 100.0)
    private val lift = segment("3kl", "Summit Chair", 500.0, sport = "alpine", kind = SegmentKind.LIFT)
    private val hogback = segment("3kh", "Hogback Climb", 200.0)

    /** The area the AppView serves; the search requests, by method. */
    private val area = listOf(far, near, lift)
    private val requests = mutableListOf<Pair<String, Map<String, List<String>>>>()
    /** Holds the area's answers until completed, while set. */
    private var areaGate: CompletableDeferred<Unit>? = null
    private var latest = emptyList<ActivityView>()

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                val path = request.url.encodedPath.removePrefix("/xrpc/")
                val params = request.url.parameters.entries().associate { it.key to it.value }
                when (path) {
                    "com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    Nsid.FEED_SEARCH_SEGMENTS -> {
                        requests += path to params
                        areaGate?.await()
                        val sport = params["sport"]?.single()
                        val shown = area.filter { sport == null || it.record.sport == sport }
                        respond(AtJson.encodeToString(SearchSegments.Output.serializer(), SearchSegments.Output(shown)), headers = json)
                    }
                    Nsid.FEED_SEARCH_SEGMENT_NAMES -> {
                        requests += path to params
                        val q = params.getValue("q").single()
                        val found = listOf(hogback, near, far).filter { q.lowercase() in it.record.name.lowercase() }
                        respond(
                            AtJson.encodeToString(SearchSegmentNames.Output.serializer(), SearchSegmentNames.Output(found)),
                            headers = json,
                        )
                    }
                    Nsid.FEED_GET_AUTHOR_ACTIVITIES ->
                        respond(
                            AtJson.encodeToString(GetAuthorActivities.Output.serializer(), GetAuthorActivities.Output(latest)),
                            headers = json,
                        )
                    else -> respond("", HttpStatusCode.NotFound)
                }
            },
        ) {
            expectSuccess = false
        }

    private val navigator = FakeNavigator(SegmentBrowseScreen)

    /** A box of [halfSpan] degrees either side of the centre, as the map reports it. */
    private fun viewport(halfSpan: Double = 0.02) =
        MapViewport(
            minLat = e7(centre.lat - halfSpan),
            minLng = e7(centre.lng - halfSpan),
            maxLat = e7(centre.lat + halfSpan),
            maxLng = e7(centre.lng + halfSpan),
            centerLat = e7(centre.lat),
            centerLng = e7(centre.lng),
        )

    private fun e7(degrees: Double) = (degrees * 1e7).roundToInt()

    private fun names(state: SegmentBrowseState) = state.area.data.runs.map { it.segment.record.name }

    private val SegmentBrowseState.areaLoaded
        get() = area.hasLoaded && area.status is LoadStatus.Idle

    private val SegmentBrowseState.resultsAnswered
        get() = results.hasAnswer && results.refresh is LoadStatus.Idle

    private suspend fun test(
        tracker: LocationTracker = FakeLocationTracker(listOf(sample())),
        block: suspend CircuitReceiveTurbine<SegmentBrowseState>.() -> Unit,
    ) {
        val (db, _, clients) = signedInTestSession(http, did = alice)
        val segments = SegmentRepository(clients)
        val feed = FeedRepository(db, clients, LocalLikes(), Logger)
        presenterTestOf({ SegmentBrowsePresenter(navigator, segments, feed, tracker) }, block = block)
    }

    private fun sample(lat: Double = centre.lat, lng: Double = centre.lng) =
        LocationSample(lat, lng, altitude = null, speed = null, accuracy = null, time = Instant.parse("2026-11-04T12:00:00Z"))

    @Test
    fun opensAtTheLocationAndListsTheAreasRunsNearestFirst() = runTest {
        test {
            val placed = awaitUntil { it.start == BrowseStart.Placed }
            assertEquals(centre.lat, placed.camera?.lat)
            assertFalse(placed.askLocation, "already granted")

            placed.eventSink(SegmentBrowseEvent.ViewportChanged(viewport()))
            val loaded = awaitUntil { it.areaLoaded }
            assertEquals(listOf("Near Loop", "Far Climb"), names(loaded), "nearest first, no lift")
            assertEquals(listOf(true), loaded.mapLines.filter { it.uri == lift.uri.value }.map { it.lift }, "the lift is on the map")
            assertEquals(3, loaded.mapLines.size)

            loaded.eventSink(SegmentBrowseEvent.Select(near.uri))
            assertEquals(near.uri, awaitUntil { it.selected != null }.selected)
            loaded.eventSink(SegmentBrowseEvent.Open(near.uri))
            assertEquals(SegmentScreen(near.uri.value), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun tooWideAnAreaAsksNothing() = runTest {
        test {
            awaitUntil { it.start == BrowseStart.Placed }.eventSink(SegmentBrowseEvent.ViewportChanged(viewport(halfSpan = 1.0)))
            val wide = awaitUntil { it.tooWide }
            testScheduler.advanceUntilIdle()
            assertTrue(requests.none { it.first == Nsid.FEED_SEARCH_SEGMENTS })
            assertFalse(wide.area.hasLoaded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aNewSportGroupDropsTheLastOnesRuns() = runTest {
        test {
            awaitUntil { it.start == BrowseStart.Placed }.eventSink(SegmentBrowseEvent.ViewportChanged(viewport()))
            val all = awaitUntil { it.areaLoaded }
            val gate = CompletableDeferred<Unit>()
            areaGate = gate
            all.eventSink(SegmentBrowseEvent.SportChanged("ride"))
            val changing = awaitUntil { it.sport == "ride" }
            assertEquals(emptyList(), names(changing), "no run segments stay listed")
            assertFalse(changing.area.hasLoaded, "loading, not empty")
            gate.complete(Unit)
            assertEquals(emptyList(), names(awaitUntil { it.areaLoaded }))
            assertEquals(listOf("ride"), requests.last().second["sport"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aNameSearchLooksInTheAreaThenEverywhere() = runTest {
        test {
            val placed = awaitUntil { it.start == BrowseStart.Placed }
            placed.eventSink(SegmentBrowseEvent.ViewportChanged(viewport()))
            awaitUntil { it.areaLoaded }
            placed.query.edit { append("hog") }
            val found = awaitUntil { it.resultsAnswered && it.results.items.isNotEmpty() }
            assertEquals(listOf("Hogback Climb"), found.results.items.map { it.record.name })
            val sent = requests.last { it.first == Nsid.FEED_SEARCH_SEGMENT_NAMES }.second
            assertEquals(listOf("${e7(centre.lat - 0.02)}"), sent["minLat"], "this area")
            assertEquals(listOf("${e7(centre.lat)}"), sent["nearLat"])

            found.query.edit { append("b") }
            val refining = awaitUntil { it.query.text.toString() == "hogb" }
            assertEquals(listOf("Hogback Climb"), refining.results.items.map { it.record.name }, "kept until the new query answers")

            refining.eventSink(SegmentBrowseEvent.SearchEverywhere)
            awaitUntil { it.scope == NameScope.Everywhere && it.resultsAnswered }
            val everywhere = requests.last { it.first == Nsid.FEED_SEARCH_SEGMENT_NAMES }.second
            assertNull(everywhere["minLat"], "no box")
            assertEquals(listOf("${e7(centre.lat)}"), everywhere["nearLat"], "still nearest first")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun withoutAMapANameSearchLooksEverywhereByMatchAlone() = runTest {
        test(UnsupportedLocationTracker()) {
            val state = awaitUntil { it.start != BrowseStart.Locating }
            assertFalse(state.hasArea)
            state.query.edit { append("loop") }
            assertEquals(
                listOf("Near Loop"),
                awaitUntil { it.resultsAnswered && it.results.items.isNotEmpty() }.results.items.map { it.record.name },
            )
            val sent = requests.single { it.first == Nsid.FEED_SEARCH_SEGMENT_NAMES }.second
            assertEquals(setOf("q"), sent.keys)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRefusedPermissionOpensAtTheLatestActivity() = runTest {
        val start = GeoPoint(44.4654, -72.6874)
        latest =
            listOf(
                ActivityView(
                    uri = AtUri.of(alice, Nsid.FEED_ACTIVITY, "3ka"),
                    cid = Cid("bafy3ka"),
                    author = ProfileViewBasic(alice, Handle("alice.test")),
                    record =
                        ActivityRecord(
                            Sport.RUN,
                            Instant.parse("2026-11-01T08:00:00Z"),
                            1800,
                            Instant.parse("2026-11-01T08:00:00Z"),
                            polyline = TrackMath.encodePolyline(listOf(sample(start.lat, start.lng), sample(start.lat + 0.01, start.lng))),
                        ),
                    likeCount = 0,
                    commentCount = 0,
                    indexedAt = Instant.parse("2026-11-01T09:00:00Z"),
                ),
            )
        test(AskingLocationTracker()) {
            val asking = awaitUntil { it.askLocation }
            assertEquals(BrowseStart.Locating, asking.start)
            asking.eventSink(SegmentBrowseEvent.PermissionResult(granted = false))
            val placed = awaitUntil { it.start == BrowseStart.Placed }
            assertEquals(start.lat, placed.camera?.lat)
            assertEquals(start.lng, placed.camera?.lng)
            assertFalse(placed.askLocation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun withNowhereToOpenItWaitsForTheAthlete() = runTest {
        test(UnsupportedLocationTracker()) {
            val nowhere = awaitUntil { it.start == BrowseStart.Nowhere }
            assertNull(nowhere.camera)
            nowhere.eventSink(SegmentBrowseEvent.ViewportChanged(viewport()))
            val loaded = awaitUntil { it.areaLoaded }
            assertEquals(listOf("Near Loop", "Far Climb"), names(loaded))
            assertEquals(BrowseStart.Placed, loaded.start, "the athlete found somewhere")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A phone whose location permission hasn't been asked for yet. */
    private class AskingLocationTracker : LocationTracker {
        override fun hasPermission(): Boolean = false

        override fun updates(): Flow<LocationSample> = emptyFlow()
    }
}
