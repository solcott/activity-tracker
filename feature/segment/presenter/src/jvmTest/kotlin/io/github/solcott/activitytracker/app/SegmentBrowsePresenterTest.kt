package io.github.solcott.activitytracker.app

import co.touchlab.kermit.Logger
import com.slack.circuit.test.CircuitReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.browse.BrowseStart
import io.github.solcott.activitytracker.app.browse.MapBrowseEvent
import io.github.solcott.activitytracker.app.browse.NameScope
import io.github.solcott.activitytracker.app.segment.BrowseRow
import io.github.solcott.activitytracker.app.segment.PathScreen
import io.github.solcott.activitytracker.app.segment.SegmentBrowseEvent
import io.github.solcott.activitytracker.app.segment.SegmentBrowsePresenter
import io.github.solcott.activitytracker.app.segment.SegmentBrowseScreen
import io.github.solcott.activitytracker.app.segment.SegmentBrowseState
import io.github.solcott.activitytracker.app.segment.SegmentGroupRow
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
import io.github.solcott.activitytracker.lexicon.app.GetPaths
import io.github.solcott.activitytracker.lexicon.app.PathKind
import io.github.solcott.activitytracker.lexicon.app.PathView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SearchPathNames
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

    /** Near Loop the other way as a ride: in Near Loop's segment group (segments/segment-groups). */
    private val nearRide =
        segment("3kr", "Near Loop", 100.0, sport = "ride").let { it.copy(group = near.uri, record = it.record.copy(line = reversed(it))) }

    private fun reversed(segment: SegmentView) =
        SegmentGeometry.encodePolyline(SegmentGeometry.decodePolyline(segment.record.line)!!.reversed())

    /** The trails and roads the AppView serves (segments/segment-paths). */
    private var trails = emptyList<PathView>()

    /** The area the AppView serves; the search requests, by method. */
    private var area = listOf(far, near, lift)
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
                    Nsid.FEED_SEARCH_PATH_NAMES -> {
                        requests += path to params
                        val q = params.getValue("q").single()
                        val sport = params["sport"]?.single()
                        val found =
                            // As the area serves them, so a segment on a path carries it.
                            (listOf(hogback, near) + area.filter { it.group != null } + far)
                                .map { s -> area.find { it.uri == s.uri } ?: s }
                                .filter { q.lowercase() in it.record.name.lowercase() && (sport == null || it.record.sport == sport) }
                        val pathed = trails.filter { trail ->
                            found.any { trail.uri in it.paths } || q.lowercase() in trail.name.lowercase()
                        }
                        val results =
                            pathed.map { SearchPathNames.Result(path = it) } +
                                found
                                    .filter { it.paths.isEmpty() }
                                    .groupBy { it.groupKey }
                                    .values
                                    .map { SearchPathNames.Result(group = it) }
                        respond(
                            AtJson.encodeToString(SearchPathNames.Output.serializer(), SearchPathNames.Output(results)),
                            headers = json,
                        )
                    }
                    Nsid.FEED_GET_PATHS -> {
                        val asked = params["uris"].orEmpty()
                        val output = GetPaths.Output(trails.filter { it.uri.value in asked })
                        respond(AtJson.encodeToString(GetPaths.Output.serializer(), output), headers = json)
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

    private fun names(state: SegmentBrowseState) =
        state.area.data.rows.map {
            when (val row = it.row) {
                is BrowseRow.Path -> row.path.name
                is BrowseRow.Group -> row.group.shown.record.name
            }
        }

    /** A row that is a segment group on no path, as its group. */
    private val BrowseRow.group: SegmentGroupRow
        get() = (this as BrowseRow.Group).group

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
            val placed = awaitUntil { it.browse.start == BrowseStart.Placed }
            assertEquals(centre.lat, placed.browse.camera?.lat)
            assertFalse(placed.browse.askLocation, "already granted")

            placed.browse.eventSink(MapBrowseEvent.ViewportChanged(viewport()))
            val loaded = awaitUntil { it.areaLoaded }
            assertEquals(listOf("Near Loop", "Far Climb"), names(loaded), "nearest first, no lift")
            assertEquals(listOf(true), loaded.browse.mapLines.filter { it.uri == lift.uri.value }.map { it.lift }, "the lift is on the map")
            assertEquals(3, loaded.browse.mapLines.size)

            loaded.browse.eventSink(MapBrowseEvent.Select(near.uri))
            assertEquals(near.uri, awaitUntil { it.browse.selected != null }.browse.selected)
            loaded.eventSink(SegmentBrowseEvent.Open(near.uri))
            assertEquals(SegmentScreen(near.uri.value), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRowTapSelectsThenOpensAndBrowsingAnAreaNeverMovesTheMap() = runTest {
        test {
            val placed = awaitUntil { it.browse.start == BrowseStart.Placed }
            val opening = placed.browse.camera
            placed.browse.eventSink(MapBrowseEvent.ViewportChanged(viewport()))
            val loaded = awaitUntil { it.areaLoaded }

            // Far Climb's line lies east of this view, but every run an area lists crosses the map's own view.
            loaded.eventSink(SegmentBrowseEvent.TapRow(far.uri))
            val selected = awaitUntil { it.browse.selected == far.uri }
            assertEquals(opening, selected.browse.camera, "the map stays")
            navigator.expectNoGoToEvents()

            selected.eventSink(SegmentBrowseEvent.TapRow(far.uri))
            assertEquals(SegmentScreen(far.uri.value), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun selectingANameResultOutOfViewFramesItsLineWithoutSearchingAgain() = runTest {
        test {
            val placed = awaitUntil { it.browse.start == BrowseStart.Placed }
            placed.browse.eventSink(MapBrowseEvent.ViewportChanged(viewport()))
            awaitUntil { it.areaLoaded }
            placed.browse.eventSink(MapBrowseEvent.ScopeChanged(NameScope.Everywhere))
            placed.browse.query.edit { append("climb") }
            val found = awaitUntil { it.resultsAnswered && it.results.items.size == 2 }
            val searches = requests.count { it.first == Nsid.FEED_SEARCH_PATH_NAMES }
            val opening = found.browse.camera

            // Hogback Climb is 200 m from the centre, in view: selecting it leaves the map where it is.
            found.eventSink(SegmentBrowseEvent.TapRow(hogback.uri))
            assertEquals(opening, awaitUntil { it.browse.selected == hogback.uri }.browse.camera)

            // Far Climb is 3 km east, past the view's edge: the map frames its whole line.
            found.eventSink(SegmentBrowseEvent.TapRow(far.uri))
            val framed = awaitUntil { it.browse.selected == far.uri && it.browse.camera != opening }
            val line = SegmentGeometry.decodePolyline(far.record.line)!!
            assertEquals(MapBounds.of(line), framed.browse.camera?.bounds)
            assertEquals((opening?.id ?: 0) + 1, framed.browse.camera?.id)

            framed.browse.eventSink(MapBrowseEvent.ViewportChanged(viewport().copy(centerLng = e7(line.first().lng))))
            testScheduler.advanceUntilIdle()
            assertEquals(searches, requests.count { it.first == Nsid.FEED_SEARCH_PATH_NAMES }, "the search isn't run again")
            assertEquals(listOf("Hogback Climb", "Far Climb"), expectMostRecentItem().results.items.map { it.group.shown.record.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun tooWideAnAreaAsksNothing() = runTest {
        test {
            awaitUntil { it.browse.start == BrowseStart.Placed }.browse.eventSink(MapBrowseEvent.ViewportChanged(viewport(halfSpan = 1.0)))
            val wide = awaitUntil { it.browse.tooWide }
            testScheduler.advanceUntilIdle()
            assertTrue(requests.none { it.first == Nsid.FEED_SEARCH_SEGMENTS })
            assertFalse(wide.area.hasLoaded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aNewSportGroupDropsTheLastOnesRuns() = runTest {
        test {
            awaitUntil { it.browse.start == BrowseStart.Placed }.browse.eventSink(MapBrowseEvent.ViewportChanged(viewport()))
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
            val placed = awaitUntil { it.browse.start == BrowseStart.Placed }
            placed.browse.eventSink(MapBrowseEvent.ViewportChanged(viewport()))
            awaitUntil { it.areaLoaded }
            placed.browse.query.edit { append("hog") }
            val found = awaitUntil { it.resultsAnswered && it.results.items.isNotEmpty() }
            assertEquals(listOf("Hogback Climb"), found.results.items.map { it.group.shown.record.name })
            val sent = requests.last { it.first == Nsid.FEED_SEARCH_PATH_NAMES }.second
            assertEquals(listOf("${e7(centre.lat - 0.02)}"), sent["minLat"], "this area")
            assertEquals(listOf("${e7(centre.lat)}"), sent["nearLat"])

            found.browse.query.edit { append("b") }
            val refining = awaitUntil { it.browse.query.text.toString() == "hogb" }
            assertEquals(
                listOf("Hogback Climb"),
                refining.results.items.map { it.group.shown.record.name },
                "kept until the new query answers",
            )

            refining.browse.eventSink(MapBrowseEvent.SearchEverywhere)
            awaitUntil { it.browse.scope == NameScope.Everywhere && it.resultsAnswered }
            val everywhere = requests.last { it.first == Nsid.FEED_SEARCH_PATH_NAMES }.second
            assertNull(everywhere["minLat"], "no box")
            assertEquals(listOf("${e7(centre.lat)}"), everywhere["nearLat"], "still nearest first")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun withoutAMapANameSearchLooksEverywhereByMatchAlone() = runTest {
        test(UnsupportedLocationTracker()) {
            val state = awaitUntil { it.browse.start != BrowseStart.Locating }
            assertFalse(state.browse.hasArea)
            state.browse.query.edit { append("loop") }
            assertEquals(
                listOf("Near Loop"),
                awaitUntil { it.resultsAnswered && it.results.items.isNotEmpty() }.results.items.map { it.group.shown.record.name },
            )
            val sent = requests.single { it.first == Nsid.FEED_SEARCH_PATH_NAMES }.second
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
            val asking = awaitUntil { it.browse.askLocation }
            assertEquals(BrowseStart.Locating, asking.browse.start)
            asking.browse.eventSink(MapBrowseEvent.PermissionResult(granted = false))
            val placed = awaitUntil { it.browse.start == BrowseStart.Placed }
            assertEquals(start.lat, placed.browse.camera?.lat)
            assertEquals(start.lng, placed.browse.camera?.lng)
            assertFalse(placed.browse.askLocation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun withNowhereToOpenItWaitsForTheAthlete() = runTest {
        test(UnsupportedLocationTracker()) {
            val nowhere = awaitUntil { it.browse.start == BrowseStart.Nowhere }
            assertNull(nowhere.browse.camera)
            nowhere.browse.eventSink(MapBrowseEvent.ViewportChanged(viewport()))
            val loaded = awaitUntil { it.areaLoaded }
            assertEquals(listOf("Near Loop", "Far Climb"), names(loaded))
            assertEquals(BrowseStart.Placed, loaded.browse.start, "the athlete found somewhere")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A phone whose location permission hasn't been asked for yet. */
    private class AskingLocationTracker : LocationTracker {
        override fun hasPermission(): Boolean = false

        override fun updates(): Flow<LocationSample> = emptyFlow()
    }

    @Test
    fun aRoutesSegmentsAreOneRowShownAsTheChosenSport() = runTest {
        area = listOf(far, near, nearRide, lift)
        test {
            awaitUntil { it.browse.start == BrowseStart.Placed }.browse.eventSink(MapBrowseEvent.ViewportChanged(viewport()))
            val loaded = awaitUntil { it.areaLoaded }
            assertEquals(listOf("Near Loop", "Far Climb"), names(loaded), "one row for both of Near Loop's runs")
            val row = loaded.area.data.rows.first().row.group
            assertEquals(listOf("ride", "run"), row.sports, "in app order")
            assertTrue(row.bothWays)
            assertEquals(nearRide.uri, row.shown.uri, "the first group's run when every group is shown")
            assertEquals(3, loaded.browse.mapLines.size, "one line for the group")

            loaded.eventSink(SegmentBrowseEvent.SportChanged("run"))
            val runs = awaitUntil { it.sport == "run" && it.areaLoaded }
            assertEquals(near.uri, runs.area.data.rows.first().row.group.shown.uri)
            assertFalse(runs.area.data.rows.first().row.group.bothWays, "only the run, one way")

            runs.eventSink(SegmentBrowseEvent.SportChanged(null))
            val all = awaitUntil { it.sport == null && it.areaLoaded }
            all.eventSink(all.tapRowEvent(all.area.data.rows.first().row))
            val selected = awaitUntil { it.browse.selected != null }
            assertEquals(nearRide.uri, selected.browse.selected)
            selected.eventSink(selected.tapRowEvent(selected.area.data.rows.first().row))
            assertEquals(SegmentScreen(nearRide.uri.value), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRoutesNameResultsAreOneRow() = runTest {
        area = listOf(far, near, nearRide, lift)
        test {
            val placed = awaitUntil { it.browse.start == BrowseStart.Placed }
            placed.browse.eventSink(MapBrowseEvent.ScopeChanged(NameScope.Everywhere))
            placed.browse.query.edit { append("loop") }
            val found = awaitUntil { it.resultsAnswered && it.results.items.isNotEmpty() }
            assertEquals(1, found.results.items.size)
            assertEquals(setOf(near.uri, nearRide.uri), found.results.items.single().group.members.map { it.uri }.toSet())
            assertEquals(1, found.browse.mapLines.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private val loop =
        PathView(
            AtUri.of(Did("did:plc:importer"), Nsid.FEED_PATH, "loop"),
            Cid("bafyloop"),
            PathKind.TRAIL,
            "Loop Trail",
            listOf(near.record.line),
            1000,
            "osm",
            "ODbL-1.0",
            sports = listOf("ride", "run"),
            groupCount = 1,
        )

    @Test
    fun segmentsOnATrailAreItsRowAndOpenItsPage() = runTest {
        trails = listOf(loop)
        area = listOf(far, near.copy(paths = listOf(loop.uri)), nearRide.copy(paths = listOf(loop.uri)), lift)
        test {
            awaitUntil { it.browse.start == BrowseStart.Placed }.browse.eventSink(MapBrowseEvent.ViewportChanged(viewport()))
            val loaded = awaitUntil { it.areaLoaded }
            assertEquals(listOf("Loop Trail", "Far Climb"), names(loaded), "the trail, then a segment on no path")
            val trail = loaded.area.data.rows.first().row as BrowseRow.Path
            assertEquals(1, trail.groups.size, "both of Near Loop's runs as one group on it")
            assertTrue(loaded.browse.mapLines.any { it.uri == loop.uri.value }, "the map draws the trail's line")
            loaded.eventSink(loaded.tapRowEvent(trail))
            val selected = awaitUntil { it.browse.selected == loop.uri }
            selected.eventSink(selected.tapRowEvent(trail))
            assertEquals(PathScreen(loop.uri.value), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aTrailFoundByNameOpensWithTheChosenSport() = runTest {
        trails = listOf(loop)
        area = listOf(far, near.copy(paths = listOf(loop.uri)), lift)
        test {
            val placed = awaitUntil { it.browse.start == BrowseStart.Placed }
            placed.browse.eventSink(MapBrowseEvent.ScopeChanged(NameScope.Everywhere))
            placed.eventSink(SegmentBrowseEvent.SportChanged("run"))
            placed.browse.query.edit { append("loop") }
            val found = awaitUntil { it.resultsAnswered && it.results.items.isNotEmpty() }
            val row = found.results.items.single() as BrowseRow.Path
            assertEquals(loop.uri, row.key)
            found.eventSink(found.openEvent(row))
            assertEquals(PathScreen(loop.uri.value, "run"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
