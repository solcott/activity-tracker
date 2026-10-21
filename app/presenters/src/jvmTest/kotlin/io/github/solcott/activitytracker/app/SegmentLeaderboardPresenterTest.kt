package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.segment.SegmentLeaderboardEvent
import io.github.solcott.activitytracker.app.segment.SegmentLeaderboardPresenter
import io.github.solcott.activitytracker.app.segment.SegmentLeaderboardScreen
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.LeaderboardPeriod
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** The leaderboard screen (segments/segment-leaderboards): filters, paging, the viewer's own place and errors. */
class SegmentLeaderboardPresenterTest {
    private val alice = Did("did:plc:alice")
    private val uri = "at://did:plc:carol/${Nsid.FEED_SEGMENT}/3kshill"
    private val screen = SegmentLeaderboardScreen(uri, "Hill", "ride")
    private var failing = false
    /** Each request's query parameters, in order. */
    private val requests = mutableListOf<Map<String, String>>()

    private fun entry(rank: Int, athlete: String, elapsed: Int) =
        """{"rank":$rank,"athlete":{"did":"did:plc:$athlete","handle":"$athlete.test"},"effort":{""" +
            """"segment":{"uri":"$uri","name":"Hill","sport":"ride","distanceMeters":1000},""" +
            """"activity":"at://did:plc:$athlete/${Nsid.FEED_ACTIVITY}/3ka","startOffset":20,"elapsed":$elapsed,"startedOn":"2026-10-06"}}"""

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/${Nsid.FEED_GET_SEGMENT_LEADERBOARD}" -> {
                        val params = request.url.parameters.entries().associate { it.key to it.value.single() }
                        requests += params
                        when {
                            failing -> respond("""{"error":"InternalError"}""", HttpStatusCode.InternalServerError, json)
                            params["cursor"] == null ->
                                respond(
                                    """{"entries":[${entry(1, "bob", 200)},${entry(2, "carol", 210)}],""" +
                                        """"viewer":${entry(40, "alice", 400)},"cursor":"210::did:plc:carol"}""",
                                    headers = json,
                                )
                            else -> respond("""{"entries":[${entry(3, "dan", 220)}],"viewer":${entry(40, "alice", 400)}}""", headers = json)
                        }
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        ) {
            expectSuccess = false
        }

    private suspend fun segments(): SegmentRepository = SegmentRepository(signedInTestSession(http, did = alice).clients)

    @Test
    fun loadsTheAllTimeBoardWithTheViewersPlaceThenMore() = runTest {
        val segments = segments()
        presenterTestOf({ SegmentLeaderboardPresenter(screen, FakeNavigator(screen), segments) }) {
            val first = awaitUntil { it.entries.isNotEmpty() && !it.isLoading }
            assertEquals(listOf(1, 2), first.entries.map { it.rank })
            assertEquals(40, assertNotNull(first.viewerEntry).rank)
            assertTrue(first.canChooseEBike)
            assertTrue(first.canLoadMore)
            assertEquals(mapOf("segment" to uri, "period" to "allTime", "scope" to "everyone"), requests.single())

            first.eventSink(SegmentLeaderboardEvent.LoadMore)
            val more = awaitUntil { it.entries.size == 3 && !it.isLoading }
            assertEquals(3, more.entries.last().rank)
            assertFalse(more.canLoadMore)
            assertEquals("210::did:plc:carol", requests.last()["cursor"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFilterChangeReloadsFromTheFirstPage() = runTest {
        val segments = segments()
        presenterTestOf({ SegmentLeaderboardPresenter(screen, FakeNavigator(screen), segments) }) {
            val first = awaitUntil { it.entries.isNotEmpty() && !it.isLoading }
            // Each change waits for its own request, so the next one can't cancel it before it's sent.
            first.eventSink(SegmentLeaderboardEvent.PeriodChanged(LeaderboardPeriod.MONTH))
            awaitUntil { requests.size == 2 && it.period == LeaderboardPeriod.MONTH && !it.isLoading }
                .eventSink(SegmentLeaderboardEvent.ScopeChanged(LeaderboardScope.FOLLOWING))
            awaitUntil { requests.size == 3 && it.scope == LeaderboardScope.FOLLOWING && !it.isLoading }
                .eventSink(SegmentLeaderboardEvent.EBikeChanged(true))
            awaitUntil { requests.size == 4 && it.eBike && !it.isLoading }

            assertEquals(
                listOf(
                    mapOf("segment" to uri, "period" to "allTime", "scope" to "everyone"),
                    mapOf("segment" to uri, "period" to "month", "scope" to "everyone"),
                    mapOf("segment" to uri, "period" to "month", "scope" to "following"),
                    mapOf("segment" to uri, "period" to "month", "scope" to "following", "eBike" to "true"),
                ),
                requests,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRunSegmentHasNoEBikeBoard() = runTest {
        val segments = segments()
        val run = SegmentLeaderboardScreen(uri, "Hill", "run")
        presenterTestOf({ SegmentLeaderboardPresenter(run, FakeNavigator(run), segments) }) {
            val state = awaitUntil { it.entries.isNotEmpty() && !it.isLoading }
            assertFalse(state.canChooseEBike)
            state.eventSink(SegmentLeaderboardEvent.EBikeChanged(true))
            expectNoEvents()
            assertEquals(1, requests.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedLoadIsAnErrorThatRetries() = runTest {
        failing = true
        val segments = segments()
        presenterTestOf({ SegmentLeaderboardPresenter(screen, FakeNavigator(screen), segments) }) {
            val failed = awaitUntil { it.error != null }
            assertEquals(UiError.Kind.LoadLeaderboard, failed.error?.kind)
            assertNull(failed.viewerEntry)
            failing = false
            failed.eventSink(SegmentLeaderboardEvent.Retry)
            assertEquals(2, awaitUntil { it.entries.isNotEmpty() && !it.isLoading }.entries.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anEntryOpensItsActivity() = runTest {
        val segments = segments()
        val navigator = FakeNavigator(screen)
        presenterTestOf({ SegmentLeaderboardPresenter(screen, navigator, segments) }) {
            val state = awaitUntil { it.entries.isNotEmpty() && !it.isLoading }
            state.eventSink(SegmentLeaderboardEvent.OpenActivity(state.entries.first().effort.activity))
            assertEquals(ActivityDetailScreen("at://did:plc:bob/${Nsid.FEED_ACTIVITY}/3ka"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
