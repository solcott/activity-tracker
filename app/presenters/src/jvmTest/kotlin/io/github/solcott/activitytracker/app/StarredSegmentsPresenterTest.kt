package io.github.solcott.activitytracker.app

import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.segment.SegmentScreen
import io.github.solcott.activitytracker.app.segment.StarredSegmentsEvent
import io.github.solcott.activitytracker.app.segment.StarredSegmentsPresenter
import io.github.solcott.activitytracker.app.segment.StarredSegmentsScreen
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
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

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

    private fun star(rkey: String, at: String) = stored.put(rkey, at)

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                val params = request.url.parameters
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/com.atproto.space.listRecords" ->
                        if (pdsDown) respond("", HttpStatusCode.InternalServerError)
                        else {
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

    @Test
    fun listsNewestStarFirst() = runTest {
        star("3ka", "2026-10-01T00:00:00Z")
        star("3kb", "2026-10-03T00:00:00Z")
        star("3kc", "2026-10-02T00:00:00Z")
        signIn()
        presenterTestOf({ StarredSegmentsPresenter(navigator, stars, segments) }) {
            val state = awaitUntil { !it.isLoading && it.segments.isNotEmpty() }
            assertEquals(listOf("Bridge", "Dam", "Hill"), state.segments.map { it.segment?.record?.name })
            assertNull(state.error)

            state.eventSink(StarredSegmentsEvent.Open(uri("3kb")))
            assertEquals(SegmentScreen(uri("3kb").value), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aGoneSegmentIsUnavailableAndCanBeUnstarred() = runTest {
        star("3ka", "2026-10-01T00:00:00Z")
        star("3kx", "2026-10-02T00:00:00Z")
        signIn()
        presenterTestOf({ StarredSegmentsPresenter(navigator, stars, segments) }) {
            val state = awaitUntil { !it.isLoading && it.segments.isNotEmpty() }
            assertEquals(listOf(uri("3kx"), uri("3ka")), state.segments.map { it.uri })
            assertNull(state.segments.first().segment, "unavailable")
            assertEquals("Hill", state.segments.last().segment?.record?.name, "the others list as usual")

            state.eventSink(StarredSegmentsEvent.Open(uri("3kx")))
            state.eventSink(StarredSegmentsEvent.Unstar(uri("3kx")))
            assertEquals(listOf(uri("3ka")), awaitUntil { it.segments.size == 1 }.segments.map { it.uri })
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
    fun unreadableStarsAreAnErrorThatRetries() = runTest {
        star("3ka", "2026-10-01T00:00:00Z")
        pdsDown = true
        signIn()
        presenterTestOf({ StarredSegmentsPresenter(navigator, stars, segments) }) {
            val failed = awaitUntil { it.error != null }
            assertEquals(UiError.Kind.LoadStarredSegments, failed.error?.kind)
            assertEquals(emptyList(), failed.segments, "never shown as none")

            pdsDown = false
            failed.eventSink(StarredSegmentsEvent.Retry)
            assertEquals(listOf("Hill"), awaitUntil { it.segments.isNotEmpty() }.segments.map { it.segment?.record?.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noStarsIsAnEmptyList() = runTest {
        signIn()
        presenterTestOf({ StarredSegmentsPresenter(navigator, stars, segments) }) {
            val state = awaitUntil { !it.isLoading }
            assertEquals(emptyList(), state.segments)
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSessionWithoutTheGrantIsToldToSignInAgain() = runTest {
        star("3ka", "2026-10-01T00:00:00Z")
        signIn(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}")
        presenterTestOf({ StarredSegmentsPresenter(navigator, stars, segments) }) {
            val state = awaitItem()
            assertEquals(UiError.Kind.SignInAgain, state.error?.kind)
            assertEquals(false, state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
