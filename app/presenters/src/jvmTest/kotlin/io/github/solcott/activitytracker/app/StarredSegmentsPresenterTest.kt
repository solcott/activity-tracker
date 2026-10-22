package io.github.solcott.activitytracker.app

import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.segment.SegmentScreen
import io.github.solcott.activitytracker.app.segment.StarredSegmentsEvent
import io.github.solcott.activitytracker.app.segment.StarredSegmentsPresenter
import io.github.solcott.activitytracker.app.segment.StarredSegmentsScreen
import io.github.solcott.activitytracker.app.segment.StarredSegmentsState
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.segmentStarRkey
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.segments.SegmentStars
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.dataresult.DataError
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

/** The starred segments list (segments/segment-favorites): newest star first, gone segments still removable, and failures shown. */
class StarredSegmentsPresenterTest {
    private val alice = Did("did:plc:alice")
    private val carol = Did("did:plc:carol")

    private fun uri(rkey: String) = AtUri.of(carol, Nsid.FEED_SEGMENT, rkey)

    /** Stars in alice's `self` space: segment rkey to when it was starred. */
    private val stored = mutableMapOf<String, String>()
    /** Segments the AppView serves, by rkey; any other is NotFound. */
    private val served = mutableMapOf("3ka" to "Hill", "3kb" to "Bridge", "3kc" to "Dam")
    private var pdsDown = false
    /** Lookups of these rkeys fail as a lost connection would. */
    private val unreachable = mutableSetOf<String>()
    /** Holds the stars' read until completed, while set. */
    private var starsGate: CompletableDeferred<Unit>? = null

    private fun star(rkey: String, at: String) = stored.put(rkey, at)

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                val params = request.url.parameters
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/com.atproto.space.listRecords" -> {
                        starsGate?.await()
                        if (pdsDown) throw IOException("Connection refused")
                        val records = stored.map { (rkey, at) ->
                            val segment = uri(rkey)
                            """{"collection":"${Nsid.FEED_SEGMENT_STAR}","rkey":"${runBlocking { segmentStarRkey(segment) }}",""" +
                                """"cid":"bafystar","value":{"subject":{"uri":"$segment","cid":"bafy$rkey"},"createdAt":"$at"}}"""
                        }
                        respond("""{"records":[${records.joinToString(",")}]}""", headers = json)
                    }
                    "/xrpc/${Nsid.FEED_GET_SEGMENT}" -> {
                        val rkey = AtUri(params["uri"]!!).rkey
                        val name = served[rkey]
                        when {
                            rkey in unreachable -> throw IOException("Connection refused")
                            name == null -> respond("""{"error":"NotFound"}""", HttpStatusCode.BadRequest, json)
                            else ->
                                respond(
                                    """{"segment":{"uri":"${uri(rkey!!)}","cid":"bafy$rkey",""" +
                                        """"creator":{"did":"$carol","handle":"carol.test"},""" +
                                        """"record":{"name":"$name","sport":"run","line":"_p~iF","source":"athlete",""" +
                                        """"license":"CC0-1.0",""" +
                                        """"createdAt":"2026-10-01T00:00:00Z"},"distanceMeters":1000}}""",
                                    headers = json,
                                )
                        }
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        ) {
            expectSuccess = false
        }

    private val navigator = FakeNavigator(StarredSegmentsScreen)
    private lateinit var db: AppDatabase
    private lateinit var stars: SegmentStars
    private lateinit var segments: SegmentRepository

    private suspend fun signIn(scope: String = OAuthClientConfig.DEFAULT_SCOPE) {
        val (db, _, clients) = signedInTestSession(http, did = alice, scope = scope)
        this.db = db
        stars = SegmentStars(clients, OutboxWriter(db, clients, Clock.System), OutboxProcessor(db, clients, Logger), Clock.System)
        segments = SegmentRepository(clients)
    }

    private val StarredSegmentsState.answered
        get() = segments.hasAnswer && segments.refresh is LoadStatus.Idle && segments.append is LoadStatus.Idle

    @Test
    fun listsNewestStarFirst() = runTest {
        star("3ka", "2026-10-01T00:00:00Z")
        star("3kb", "2026-10-03T00:00:00Z")
        star("3kc", "2026-10-02T00:00:00Z")
        signIn()
        presenterTestOf({ StarredSegmentsPresenter(navigator, stars, segments) }) {
            val state = awaitUntil { it.answered }
            assertEquals(listOf("Bridge", "Dam", "Hill"), state.segments.items.map { it.segment?.record?.name })
            assertNull(state.actionError)

            state.eventSink(StarredSegmentsEvent.Open(uri("3kb")))
            assertEquals(SegmentScreen(uri("3kb").value), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun itShowsLoadingNotNoneUntilTheStarsAreRead() = runTest {
        star("3ka", "2026-10-01T00:00:00Z")
        val gate = CompletableDeferred<Unit>()
        starsGate = gate
        signIn()
        presenterTestOf({ StarredSegmentsPresenter(navigator, stars, segments) }) {
            val loading = awaitUntil { it.segments.refresh is LoadStatus.Loading }
            assertFalse(loading.segments.hasAnswer, "not that there are none")
            gate.complete(Unit)
            assertEquals(1, awaitUntil { it.answered }.segments.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aGoneSegmentIsUnavailableAndCanBeUnstarred() = runTest {
        star("3ka", "2026-10-01T00:00:00Z")
        star("3kx", "2026-10-02T00:00:00Z")
        signIn()
        presenterTestOf({ StarredSegmentsPresenter(navigator, stars, segments) }) {
            val state = awaitUntil { it.answered }
            assertEquals(listOf(uri("3kx"), uri("3ka")), state.segments.items.map { it.uri })
            assertNull(state.segments.items.first().segment, "unavailable")
            assertEquals("Hill", state.segments.items.last().segment?.record?.name, "the others list as usual")

            state.eventSink(StarredSegmentsEvent.Open(uri("3kx")))
            state.eventSink(StarredSegmentsEvent.Unstar(uri("3kx")))
            assertEquals(listOf(uri("3ka")), awaitUntil { it.segments.items.size == 1 }.segments.items.map { it.uri })
            val queued = db.outbox().pending(alice.value).single()
            assertEquals(
                "delete ${Nsid.FEED_SEGMENT_STAR} ${segmentStarRkey(uri("3kx"))}",
                "${queued.operation} ${queued.collection} ${queued.rkey}",
            )
            navigator.expectNoGoToEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aLookupThatFailsOfflineFailsThePageRatherThanShowingItUnavailable() = runTest {
        star("3ka", "2026-10-01T00:00:00Z")
        star("3kb", "2026-10-02T00:00:00Z")
        unreachable += "3kb"
        signIn()
        presenterTestOf({ StarredSegmentsPresenter(navigator, stars, segments) }) {
            val failed = awaitUntil { it.segments.refresh is LoadStatus.Failed }
            assertEquals(DataError.Network, (failed.segments.refresh as LoadStatus.Failed).error)
            assertEquals(emptyList(), failed.segments.items, "no segment shown as unavailable")

            unreachable.clear()
            failed.eventSink(StarredSegmentsEvent.Retry)
            assertEquals(listOf("Bridge", "Hill"), awaitUntil { it.answered }.segments.items.map { it.segment?.record?.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedSecondPageKeepsTheFirst() = runTest {
        (1..31).forEach { star("3k$it", "2026-10-01T00:00:${it.toString().padStart(2, '0')}Z") }
        (1..31).forEach { served["3k$it"] = "Segment $it" }
        // The oldest star is the only one on the second page.
        unreachable += "3k1"
        signIn()
        presenterTestOf({ StarredSegmentsPresenter(navigator, stars, segments) }) {
            val first = awaitUntil { it.answered }
            assertEquals(30, first.segments.items.size)
            first.eventSink(StarredSegmentsEvent.LoadMore)
            val failed = awaitUntil { it.segments.append is LoadStatus.Failed }
            assertEquals(30, failed.segments.items.size, "the first page stays")

            unreachable.clear()
            failed.eventSink(StarredSegmentsEvent.Retry)
            assertEquals(31, awaitUntil { it.segments.items.size == 31 }.segments.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unreadableStarsSayWhyAndRetry() = runTest {
        star("3ka", "2026-10-01T00:00:00Z")
        pdsDown = true
        signIn()
        presenterTestOf({ StarredSegmentsPresenter(navigator, stars, segments) }) {
            val failed = awaitUntil { it.segments.refresh is LoadStatus.Failed }
            assertEquals(DataError.Network, (failed.segments.refresh as LoadStatus.Failed).error)
            assertEquals(emptyList(), failed.segments.items, "never shown as none")
            assertFalse(failed.segments.hasAnswer)

            pdsDown = false
            failed.eventSink(StarredSegmentsEvent.Retry)
            assertEquals(listOf("Hill"), awaitUntil { it.answered }.segments.items.map { it.segment?.record?.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedUnstarKeepsTheRowAndPaging() = runTest {
        (1..31).forEach { star("3k$it", "2026-10-01T00:00:${it.toString().padStart(2, '0')}Z") }
        (1..31).forEach { served["3k$it"] = "Segment $it" }
        signIn()
        presenterTestOf({ StarredSegmentsPresenter(navigator, stars, segments) }) {
            val first = awaitUntil { it.answered }
            // The outbox can't take the delete, so the unstar is undone.
            db.close()
            first.eventSink(StarredSegmentsEvent.Unstar(uri("3k31")))
            val failed = awaitUntil { it.actionError != null }
            assertEquals(UiError.Kind.UpdateStar, failed.actionError?.kind)
            assertEquals(30, failed.segments.items.size, "the row stays")
            assertTrue(failed.segments.canLoadMore)
            failed.eventSink(StarredSegmentsEvent.LoadMore)
            assertEquals(31, awaitUntil { it.segments.items.size == 31 }.segments.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noStarsIsAnEmptyList() = runTest {
        signIn()
        presenterTestOf({ StarredSegmentsPresenter(navigator, stars, segments) }) {
            val state = awaitUntil { it.answered }
            assertEquals(emptyList(), state.segments.items)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSessionWithoutTheGrantIsToldToSignInAgain() = runTest {
        star("3ka", "2026-10-01T00:00:00Z")
        signIn(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}")
        presenterTestOf({ StarredSegmentsPresenter(navigator, stars, segments) }) {
            val state = awaitUntil { it.segments.refresh is LoadStatus.Idle }
            assertTrue(state.needsSignIn)
            assertEquals(emptyList(), state.segments.items, "nothing loads")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
