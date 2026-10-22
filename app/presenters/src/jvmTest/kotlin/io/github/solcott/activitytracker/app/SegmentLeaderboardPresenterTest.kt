package io.github.solcott.activitytracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withCompositionLocals
import com.slack.circuit.retained.LocalRetainedStateRegistry
import com.slack.circuit.retained.RetainedStateRegistry
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.segment.SegmentLeaderboardEvent
import io.github.solcott.activitytracker.app.segment.SegmentLeaderboardPresenter
import io.github.solcott.activitytracker.app.segment.SegmentLeaderboardScreen
import io.github.solcott.activitytracker.app.segment.SegmentLeaderboardState
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.LeaderboardAgeGroup
import io.github.solcott.activitytracker.lexicon.app.LeaderboardGender
import io.github.solcott.activitytracker.lexicon.app.LeaderboardPeriod
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
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

/** The leaderboard screen (segments/segment-leaderboards): filters, paging, the viewer's own place and errors. */
class SegmentLeaderboardPresenterTest {
    private val alice = Did("did:plc:alice")
    private val uri = "at://did:plc:carol/${Nsid.FEED_SEGMENT}/3kshill"
    private val screen = SegmentLeaderboardScreen(uri, "Hill", "ride")
    private var failing = false
    /** Fail every page after the first, while set. */
    private var failingMore = false
    /** Holds the this-year board's answer until completed, while set. */
    private var yearGate: CompletableDeferred<Unit>? = null
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
                            params["period"] == LeaderboardPeriod.YEAR -> {
                                yearGate?.await()
                                respond("""{"entries":[${entry(1, "erin", 190)}]}""", headers = json)
                            }
                            params["scope"] == LeaderboardScope.FOLLOWING -> respond("""{"entries":[]}""", headers = json)
                            params["eBike"] == "true" ->
                                respond("""{"entries":[${entry(1, "frank", 150)}],"viewer":${entry(5, "alice", 300)}}""", headers = json)
                            failingMore && params["cursor"] != null ->
                                respond("""{"error":"InternalError"}""", HttpStatusCode.InternalServerError, json)
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

    private val SegmentLeaderboardState.answered
        get() = entries.hasAnswer && entries.refresh is LoadStatus.Idle && entries.append is LoadStatus.Idle

    @Test
    fun loadsTheAllTimeBoardWithTheViewersPlaceThenMore() = runTest {
        val segments = segments()
        presenterTestOf({ SegmentLeaderboardPresenter(screen, FakeNavigator(screen), segments) }) {
            val first = awaitUntil { it.answered }
            assertEquals(listOf(1, 2), first.entries.items.map { it.rank })
            assertEquals(40, assertNotNull(first.viewerEntry).rank, "own place pinned")
            assertTrue(first.canChooseEBike)
            assertTrue(first.entries.canLoadMore)
            assertEquals(mapOf("segment" to uri, "period" to "allTime", "scope" to "everyone"), requests.single())

            first.eventSink(SegmentLeaderboardEvent.LoadMore)
            val more = awaitUntil { it.entries.items.size == 3 && it.answered }
            assertEquals(3, more.entries.items.last().rank)
            assertFalse(more.entries.canLoadMore)
            assertEquals("210::did:plc:carol", requests.last()["cursor"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFilterChangeReloadsFromTheFirstPage() = runTest {
        val segments = segments()
        presenterTestOf({ SegmentLeaderboardPresenter(screen, FakeNavigator(screen), segments) }) {
            val first = awaitUntil { it.answered }
            // Each change waits for its own request, so the next one can't cancel it before it's sent.
            first.eventSink(SegmentLeaderboardEvent.PeriodChanged(LeaderboardPeriod.MONTH))
            awaitUntil { requests.size == 2 && it.period == LeaderboardPeriod.MONTH && it.answered }
                .eventSink(SegmentLeaderboardEvent.ScopeChanged(LeaderboardScope.FOLLOWING))
            awaitUntil { requests.size == 3 && it.scope == LeaderboardScope.FOLLOWING && it.answered }
                .eventSink(SegmentLeaderboardEvent.EBikeChanged(true))
            awaitUntil { requests.size == 4 && it.eBike && it.answered }

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
    fun bracketFiltersCombineAndReloadFromTheFirstPage() = runTest {
        val segments = segments()
        presenterTestOf({ SegmentLeaderboardPresenter(screen, FakeNavigator(screen), segments) }) {
            val first = awaitUntil { it.answered }
            assertNull(first.ageGroup, "every age by default")
            first.eventSink(SegmentLeaderboardEvent.AgeGroupChanged(LeaderboardAgeGroup.FROM_45))
            awaitUntil { requests.size == 2 && it.ageGroup == LeaderboardAgeGroup.FROM_45 && it.answered }
                .eventSink(SegmentLeaderboardEvent.GenderChanged(LeaderboardGender.FEMALE))
            val both = awaitUntil { requests.size == 3 && it.gender == LeaderboardGender.FEMALE && it.answered }
            // An unknown class is ignored, so it changes nothing; clearing the age group reloads.
            both.eventSink(SegmentLeaderboardEvent.WeightClassChanged("60-70"))
            both.eventSink(SegmentLeaderboardEvent.AgeGroupChanged(null))
            val cleared = awaitUntil { requests.size == 4 && it.ageGroup == null && it.answered }
            assertNull(cleared.weightClass)

            assertEquals(
                listOf(
                    mapOf("segment" to uri, "period" to "allTime", "scope" to "everyone"),
                    mapOf("segment" to uri, "period" to "allTime", "scope" to "everyone", "ageGroup" to "45-54"),
                    mapOf("segment" to uri, "period" to "allTime", "scope" to "everyone", "ageGroup" to "45-54", "gender" to "female"),
                    mapOf("segment" to uri, "period" to "allTime", "scope" to "everyone", "gender" to "female"),
                ),
                requests,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aNewPeriodClearsTheBoardAndTheViewersPlaceUntilItAnswers() = runTest {
        val gate = CompletableDeferred<Unit>()
        yearGate = gate
        val segments = segments()
        presenterTestOf({ SegmentLeaderboardPresenter(screen, FakeNavigator(screen), segments) }) {
            val allTime = awaitUntil { it.answered }
            assertNotNull(allTime.viewerEntry)
            allTime.eventSink(SegmentLeaderboardEvent.PeriodChanged(LeaderboardPeriod.YEAR))
            // The first state under this year already has none of the all-time board.
            val loading = awaitUntil { it.period == LeaderboardPeriod.YEAR }
            assertEquals(emptyList(), loading.entries.items, "no all-time entries under this year")
            assertNull(loading.viewerEntry, "nor the all-time place")
            assertEquals(LoadStatus.Loading, loading.entries.refresh)

            gate.complete(Unit)
            val year = awaitUntil { it.answered }
            assertEquals(listOf("erin.test"), year.entries.items.map { it.athlete.handle.value })
            assertNull(year.viewerEntry, "not on this year's board")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theEBikeBoardHasTheViewersEBikePlace() = runTest {
        val segments = segments()
        presenterTestOf({ SegmentLeaderboardPresenter(screen, FakeNavigator(screen), segments) }) {
            awaitUntil { it.answered }.eventSink(SegmentLeaderboardEvent.EBikeChanged(true))
            val eBike = awaitUntil { it.eBike && it.answered }
            assertEquals(listOf("frank.test"), eBike.entries.items.map { it.athlete.handle.value })
            assertEquals(5, eBike.viewerEntry?.rank)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun nobodyOnABoardIsAnAnswer() = runTest {
        val segments = segments()
        presenterTestOf({ SegmentLeaderboardPresenter(screen, FakeNavigator(screen), segments) }) {
            awaitUntil { it.answered }.eventSink(SegmentLeaderboardEvent.ScopeChanged(LeaderboardScope.FOLLOWING))
            val empty = awaitUntil { it.scope == LeaderboardScope.FOLLOWING && it.answered }
            assertEquals(emptyList(), empty.entries.items)
            assertNull(empty.viewerEntry)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRunSegmentHasNoEBikeBoard() = runTest {
        val segments = segments()
        val run = SegmentLeaderboardScreen(uri, "Hill", "run")
        presenterTestOf({ SegmentLeaderboardPresenter(run, FakeNavigator(run), segments) }) {
            val state = awaitUntil { it.answered }
            assertFalse(state.canChooseEBike)
            state.eventSink(SegmentLeaderboardEvent.EBikeChanged(true))
            expectNoEvents()
            assertEquals(1, requests.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedLoadSaysWhyAndRetries() = runTest {
        failing = true
        val segments = segments()
        presenterTestOf({ SegmentLeaderboardPresenter(screen, FakeNavigator(screen), segments) }) {
            val failed = awaitUntil { it.entries.refresh is LoadStatus.Failed }
            assertIs<LoadStatus.Failed>(failed.entries.refresh)
            assertNull(failed.viewerEntry)
            failing = false
            failed.eventSink(SegmentLeaderboardEvent.Retry)
            assertEquals(2, awaitUntil { it.answered }.entries.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedLaterPageKeepsTheFirst() = runTest {
        failingMore = true
        val segments = segments()
        presenterTestOf({ SegmentLeaderboardPresenter(screen, FakeNavigator(screen), segments) }) {
            awaitUntil { it.answered }.eventSink(SegmentLeaderboardEvent.LoadMore)
            val failed = awaitUntil { it.entries.append is LoadStatus.Failed }
            assertEquals(listOf(1, 2), failed.entries.items.map { it.rank })
            failingMore = false
            failed.eventSink(SegmentLeaderboardEvent.Retry)
            assertEquals(listOf(1, 2, 3), awaitUntil { it.entries.items.size == 3 }.entries.items.map { it.rank })
            assertEquals(3, requests.size, "only the failed page again")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun comingBackShowsTheChosenBoardsPagesAtOnce() = runTest {
        val segments = segments()
        val retained = RetainedStateRegistry()
        var saveable by mutableStateOf(SaveableStateRegistry(null) { true })
        var shown by mutableStateOf(true)
        presenterTestOf({
            withCompositionLocals(LocalRetainedStateRegistry provides retained, LocalSaveableStateRegistry provides saveable) {
                // A stand-in for the activity it opened while it's on the back stack.
                if (shown) SegmentLeaderboardPresenter(screen, FakeNavigator(screen), segments) else SegmentLeaderboardState(name = "")
            }
        }) {
            awaitUntil { it.answered }.eventSink(SegmentLeaderboardEvent.PeriodChanged(LeaderboardPeriod.MONTH))
            awaitUntil { it.period == LeaderboardPeriod.MONTH && it.answered }.eventSink(SegmentLeaderboardEvent.LoadMore)
            awaitUntil { it.entries.items.size == 3 && it.answered }
            val sent = requests.size
            // What the back stack does to a record it leaves.
            retained.saveAll()
            val saved = saveable.performSave()
            shown = false
            awaitUntil { it.name.isEmpty() }
            saveable = SaveableStateRegistry(saved) { true }
            shown = true
            val back = awaitUntil { it.name == "Hill" }
            assertEquals(LeaderboardPeriod.MONTH, back.period)
            assertEquals(listOf(1, 2, 3), back.entries.items.map { it.rank }, "no loading state in between")
            expectNoEvents()
            assertEquals(sent, requests.size, "no page loaded again")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anEntryOpensItsActivity() = runTest {
        val segments = segments()
        val navigator = FakeNavigator(screen)
        presenterTestOf({ SegmentLeaderboardPresenter(screen, navigator, segments) }) {
            val state = awaitUntil { it.answered }
            state.eventSink(SegmentLeaderboardEvent.OpenActivity(state.entries.items.first().effort.activity))
            assertEquals(ActivityDetailScreen("at://did:plc:bob/${Nsid.FEED_ACTIVITY}/3ka"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
