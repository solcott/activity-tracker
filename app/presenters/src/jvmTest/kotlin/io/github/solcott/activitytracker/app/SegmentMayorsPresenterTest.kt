package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.segment.SegmentMayorsEvent
import io.github.solcott.activitytracker.app.segment.SegmentMayorsPresenter
import io.github.solcott.activitytracker.app.segment.SegmentMayorsScreen
import io.github.solcott.activitytracker.app.segment.SegmentMayorsState
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.LeaderboardAgeGroup
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.MayorPeriod
import io.github.solcott.uistate.LoadStatus
import io.github.solcott.uistate.hasAnswer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

/** The Mayors screen (segments/segment-mayors): the default window, choices, paging, the viewer's own place, errors and profiles. */
class SegmentMayorsPresenterTest {
    private val alice = Did("did:plc:alice")
    private val uri = "at://did:plc:carol/${Nsid.FEED_SEGMENT}/3kshill"
    private val screen = SegmentMayorsScreen(uri, "Hill", "ride")
    private var failing = false
    /** Holds this week's board's answer until completed, while set. */
    private var weekGate: CompletableDeferred<Unit>? = null
    /** Each request's query parameters, in order. */
    private val requests = mutableListOf<Map<String, String>>()

    private fun entry(rank: Int, athlete: String, efforts: Int) =
        """{"rank":$rank,"athlete":{"did":"did:plc:$athlete","handle":"$athlete.test"},"efforts":$efforts,"reachedOn":"2026-10-18"}"""

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/${Nsid.FEED_GET_SEGMENT_MAYORS}" -> {
                        val params = request.url.parameters.entries().associate { it.key to it.value.single() }
                        requests += params
                        when {
                            failing -> respond("""{"error":"InternalError"}""", HttpStatusCode.InternalServerError, json)
                            params["period"] == MayorPeriod.THIS_WEEK -> {
                                weekGate?.await()
                                respond("""{"entries":[${entry(1, "erin", 3)}]}""", headers = json)
                            }
                            params["cursor"] == null ->
                                respond(
                                    """{"entries":[${entry(1, "bob", 14)},${entry(2, "carol", 11)}],""" +
                                        """"viewer":${entry(58, "alice", 2)},"cursor":"11::2026-10-18::did:plc:carol"}""",
                                    headers = json,
                                )
                            else -> respond("""{"entries":[${entry(3, "dan", 9)}],"viewer":${entry(58, "alice", 2)}}""", headers = json)
                        }
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        ) {
            expectSuccess = false
        }

    private suspend fun segments(): SegmentRepository = SegmentRepository(signedInTestSession(http, did = alice).clients)

    private val SegmentMayorsState.answered
        get() = entries.hasAnswer && entries.refresh is LoadStatus.Idle && entries.append is LoadStatus.Idle

    @Test
    fun loadsTheLast90DaysWithTheViewersPlaceThenMore() = runTest {
        val segments = segments()
        presenterTestOf({ SegmentMayorsPresenter(screen, FakeNavigator(screen), segments) }) {
            val first = awaitUntil { it.answered }
            assertEquals(MayorPeriod.LAST_90_DAYS, first.period)
            assertEquals(listOf(14, 11), first.entries.items.map { it.efforts })
            assertEquals(58, assertNotNull(first.viewerEntry).rank, "own place pinned")
            assertTrue(first.canChooseEBike)
            assertEquals(mapOf("segment" to uri, "period" to "last90Days", "scope" to "everyone"), requests.single())

            first.eventSink(SegmentMayorsEvent.LoadMore)
            val more = awaitUntil { it.entries.items.size == 3 && it.answered }
            assertEquals(3, more.entries.items.last().rank)
            assertFalse(more.entries.canLoadMore)
            assertEquals("11::2026-10-18::did:plc:carol", requests.last()["cursor"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aNewWindowClearsTheBoardAndTheViewersPlaceUntilItAnswers() = runTest {
        val gate = CompletableDeferred<Unit>()
        weekGate = gate
        val segments = segments()
        presenterTestOf({ SegmentMayorsPresenter(screen, FakeNavigator(screen), segments) }) {
            awaitUntil { it.answered }.eventSink(SegmentMayorsEvent.PeriodChanged(MayorPeriod.THIS_WEEK))
            val loading = awaitUntil { it.period == MayorPeriod.THIS_WEEK }
            assertEquals(emptyList(), loading.entries.items, "no last-90-days entries under this week")
            assertNull(loading.viewerEntry)

            gate.complete(Unit)
            val week = awaitUntil { it.answered }
            assertEquals(listOf("erin.test"), week.entries.items.map { it.athlete.handle.value })
            assertEquals("thisWeek", requests.last()["period"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun scopeEBikeAndBracketsReloadAndUnknownChoicesAreIgnored() = runTest {
        val segments = segments()
        presenterTestOf({ SegmentMayorsPresenter(screen, FakeNavigator(screen), segments) }) {
            val first = awaitUntil { it.answered }
            first.eventSink(SegmentMayorsEvent.PeriodChanged("decade"))
            first.eventSink(SegmentMayorsEvent.ScopeChanged(LeaderboardScope.FOLLOWING))
            awaitUntil { requests.size == 2 && it.scope == LeaderboardScope.FOLLOWING && it.answered }
                .eventSink(SegmentMayorsEvent.EBikeChanged(true))
            awaitUntil { requests.size == 3 && it.eBike && it.answered }
                .eventSink(SegmentMayorsEvent.AgeGroupChanged(LeaderboardAgeGroup.FROM_35))
            val last = awaitUntil { requests.size == 4 && it.ageGroup != null && it.answered }
            assertEquals(MayorPeriod.LAST_90_DAYS, last.period, "an unknown window changes nothing")
            assertEquals(
                mapOf("segment" to uri, "period" to "last90Days", "scope" to "following", "eBike" to "true", "ageGroup" to "35-44"),
                requests.last(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedLoadSaysWhyAndRetries() = runTest {
        failing = true
        val segments = segments()
        presenterTestOf({ SegmentMayorsPresenter(screen, FakeNavigator(screen), segments) }) {
            val failed = awaitUntil { it.entries.refresh is LoadStatus.Failed }
            assertIs<LoadStatus.Failed>(failed.entries.refresh)
            failing = false
            failed.eventSink(SegmentMayorsEvent.Retry)
            assertEquals(2, awaitUntil { it.answered }.entries.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anEntryOpensItsAthlete() = runTest {
        val segments = segments()
        val navigator = FakeNavigator(screen)
        presenterTestOf({ SegmentMayorsPresenter(screen, navigator, segments) }) {
            val state = awaitUntil { it.answered }
            state.eventSink(SegmentMayorsEvent.OpenAthlete(state.entries.items.first().athlete.did))
            assertEquals(ProfileScreen("did:plc:bob"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
