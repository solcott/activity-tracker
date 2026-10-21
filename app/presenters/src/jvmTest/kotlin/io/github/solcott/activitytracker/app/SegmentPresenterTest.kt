package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.segment.SegmentEvent
import io.github.solcott.activitytracker.app.segment.SegmentPresenter
import io.github.solcott.activitytracker.app.segment.SegmentScreen
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.location.TrackMath
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/** The segment screen (segments/segment-efforts): the segment, its line for the map, and the viewer's own efforts fastest first. */
class SegmentPresenterTest {
    private val alice = Did("did:plc:alice")
    private val uri = "at://did:plc:carol/${Nsid.FEED_SEGMENT}/3kshill"
    private val screen = SegmentScreen(uri)
    private val line = SegmentGeometry.encodePolyline(listOf(GeoPoint(47.0, 8.0), GeoPoint(47.009, 8.0)))
    private var failSegment = false
    private val effortsRequested = mutableListOf<String?>()

    private fun effort(rkey: String, elapsed: Int, best: Boolean = false) =
        """{"segment":{"uri":"$uri","name":"Hill","sport":"run","distanceMeters":1000},""" +
            """"activity":"at://$alice/${Nsid.FEED_ACTIVITY}/$rkey",""" +
            """"startOffset":20,"elapsed":$elapsed,"startedOn":"2026-10-0${rkey.last()}"${if (best) ""","best":true""" else ""}}"""

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/${Nsid.FEED_GET_SEGMENT}" ->
                        if (failSegment) respond("""{"error":"NotFound","message":"Segment not found"}""", HttpStatusCode.BadRequest, json)
                        else
                            respond(
                                """{"segment":{"uri":"$uri","cid":"bafyseg","creator":{"did":"did:plc:carol","handle":"carol.test"},""" +
                                    """"record":{"name":"Hill","sport":"run","line":"$line","source":"athlete","license":"CC0-1.0",""" +
                                    """"createdAt":"2026-10-01T00:00:00Z"},"distanceMeters":1000}}""",
                                headers = json,
                            )
                    "/xrpc/${Nsid.FEED_GET_SEGMENT_EFFORTS}" -> {
                        effortsRequested += request.url.parameters["actor"]
                        // Out of order, as a client must not rely on.
                        respond(
                            """{"efforts":[${effort("3ka2", 250)},${effort("3ka1", 200, best = true)},${effort("3ka3", 300)}]}""",
                            headers = json,
                        )
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        ) {
            expectSuccess = false
        }

    private suspend fun segments() = SegmentRepository(signedInTestSession(http, did = alice).let { (_, _, clients) -> clients })

    @Test
    fun loadsTheSegmentItsLineAndTheViewersEffortsFastestFirst() = runTest {
        val segments = segments()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments) }) {
            assertEquals(true, awaitItem().isLoading)
            val state = awaitUntil { it.segment != null && it.efforts.isNotEmpty() }
            assertEquals("Hill", state.segment?.record?.name)
            assertEquals(2, TrackMath.decodePolyline(assertNotNull<String>(state.polyline)).size)
            assertEquals(listOf(200L, 250L, 300L), state.efforts.map { it.elapsed })
            assertEquals<List<String?>>(listOf(alice.value), effortsRequested, "the viewer's own")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedLoadIsAnErrorThatRetries() = runTest {
        failSegment = true
        val segments = segments()
        presenterTestOf({ SegmentPresenter(screen, FakeNavigator(screen), segments) }) {
            val failed = awaitUntil { it.error != null }
            assertEquals(false, failed.isLoading)
            assertNull(failed.segment)

            failSegment = false
            failed.eventSink(SegmentEvent.Retry)
            assertEquals("Hill", awaitUntil { it.segment != null }.segment?.record?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anEffortOpensItsActivity() = runTest {
        val navigator = FakeNavigator(screen)
        val segments = segments()
        presenterTestOf({ SegmentPresenter(screen, navigator, segments) }) {
            val state = awaitUntil { it.efforts.isNotEmpty() }
            state.eventSink(SegmentEvent.OpenActivity(state.efforts.first().activity))
            assertEquals(ActivityDetailScreen("at://$alice/${Nsid.FEED_ACTIVITY}/3ka1"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
