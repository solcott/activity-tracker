package io.github.solcott.activitytracker.app

import com.slack.circuit.test.CircuitReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.segment.PathEvent
import io.github.solcott.activitytracker.app.segment.PathPresenter
import io.github.solcott.activitytracker.app.segment.PathScreen
import io.github.solcott.activitytracker.app.segment.PathState
import io.github.solcott.activitytracker.app.segment.SegmentScreen
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetPath
import io.github.solcott.activitytracker.lexicon.app.PathKind
import io.github.solcott.activitytracker.lexicon.app.PathView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** The Trail page (segments/path-page): its segment groups longest first, the sport filter, and a selection shared with the map. */
class PathPresenterTest {
    private val carol = Did("did:plc:carol")
    private val start = GeoPoint(44.4759, -73.2121)

    private fun line(meters: Double, back: Boolean = false): String {
        val points = listOf(start, GeoPoint(start.lat + meters / 111_320.0, start.lng))
        return SegmentGeometry.encodePolyline(if (back) points.reversed() else points)
    }

    @Suppress("LongParameterList") // A segment's every field a test sets.
    private fun segment(rkey: String, name: String, sport: String, meters: Double, back: Boolean = false, group: String? = null) =
        SegmentView(
            AtUri.of(carol, Nsid.FEED_SEGMENT, rkey),
            Cid("bafy$rkey"),
            ProfileViewBasic(carol, Handle("carol.test")),
            SegmentRecord(name, sport, line(meters, back), "osm", "ODbL-1.0", Instant.parse("2026-10-01T00:00:00Z")),
            distanceMeters = meters.toLong(),
            group = group?.let { AtUri.of(carol, Nsid.FEED_SEGMENT, it) },
        )

    private val wholeRide = segment("3kwr", "Ann's (northbound)", "ride", 5_000.0, group = "3kwr")
    private val wholeRun = segment("3kwn", "Ann's (southbound)", "run", 5_000.0, back = true, group = "3kwr")
    private val stretch = segment("3ks", "Ann's: Lot → Ridge", "run", 1_200.0)

    private val anns =
        PathView(
            AtUri.of(Did("did:plc:importer"), Nsid.FEED_PATH, "anns"),
            Cid("bafyanns"),
            PathKind.TRAIL,
            "Ann's",
            listOf(line(5_000.0)),
            5_000,
            "osm",
            "ODbL-1.0",
            sports = listOf("ride", "run"),
            groupCount = 2,
        )

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath.removePrefix("/xrpc/")) {
                    "com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    Nsid.FEED_GET_PATH ->
                        respond(
                            AtJson.encodeToString(GetPath.Output.serializer(), GetPath.Output(anns, listOf(stretch, wholeRide, wholeRun))),
                            headers = json,
                        )
                    else -> respond("", HttpStatusCode.NotFound)
                }
            },
        ) {
            expectSuccess = false
        }

    private suspend fun test(screen: PathScreen, navigator: FakeNavigator, block: suspend CircuitReceiveTurbine<PathState>.() -> Unit) {
        val (_, _, clients) = signedInTestSession(http, did = Did("did:plc:alice"))
        presenterTestOf({ PathPresenter(screen, navigator, SegmentRepository(clients)) }, block = block)
    }

    @Test
    fun listsTheTrailsGroupsLongestFirstAndFramesIt() = runTest {
        val screen = PathScreen(anns.uri.value)
        test(screen, FakeNavigator(screen)) {
            val loaded = awaitUntil { it.path != null && it.camera != null }
            assertEquals(listOf(wholeRide.uri, stretch.uri), loaded.groups.map { it.shown.uri }, "the whole line, then the stretch")
            assertTrue(loaded.groups.first().bothWays)
            assertEquals("OpenStreetMap", loaded.credit?.let { it::class.simpleName })
            assertEquals(3, loaded.mapLines.size, "the trail's line and each group's")
            assertTrue(loaded.mapLines.first().lift, "the trail's line drawn muted under them")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aTapSelectsThenOpensAndAClickOpens() = runTest {
        val screen = PathScreen(anns.uri.value)
        val navigator = FakeNavigator(screen)
        test(screen, navigator) {
            val loaded = awaitUntil { it.path != null }
            val stretchRow = loaded.groups.last()
            loaded.eventSink(loaded.tapRowEvent(stretchRow))
            val selected = awaitUntil { it.selected == stretch.uri }
            assertTrue(selected.isSelected(stretchRow))
            selected.eventSink(selected.tapRowEvent(stretchRow))
            assertEquals(SegmentScreen(stretch.uri.value), navigator.awaitNextScreen())
            selected.eventSink(selected.openEvent(selected.groups.first()))
            assertEquals(SegmentScreen(wholeRide.uri.value), navigator.awaitNextScreen())
            selected.eventSink(selected.selectEvent(null))
            awaitUntil { it.selected == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun opensNarrowedToTheBrowseSportUntilShowAll() = runTest {
        val screen = PathScreen(anns.uri.value, sport = "ride")
        test(screen, FakeNavigator(screen)) {
            val ride = awaitUntil { it.path != null }
            assertTrue(ride.offersAllSports)
            assertEquals(listOf(wholeRide.uri), ride.groups.map { it.shown.uri }, "only the ride group")
            ride.eventSink(PathEvent.ShowAllSports)
            val all = awaitUntil { it.sport == null && it.groups.size == 2 }
            assertEquals(listOf(wholeRide.uri, stretch.uri), all.groups.map { it.shown.uri })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
