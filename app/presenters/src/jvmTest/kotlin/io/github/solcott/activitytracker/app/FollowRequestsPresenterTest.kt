package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.profile.FollowRequestsEvent
import io.github.solcott.activitytracker.app.profile.FollowRequestsPresenter
import io.github.solcott.activitytracker.app.profile.FollowRequestsScreen
import io.github.solcott.activitytracker.app.profile.FollowRequestsState
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.profile.FollowRequestsRepository
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

/** Alice answers her follow requests: Bob and Carol ask, and she declined Carol before. */
class FollowRequestsPresenterTest {
    private val alice = Did("did:plc:alice")
    private val bob = Did("did:plc:bob")
    private val carol = Did("did:plc:carol")

    private var scopeMissing = false
    private var failRequests = false

    /** Holds each approval write until completed, so the test sees it in flight. */
    private val approveGate = CompletableDeferred<Unit>().apply { complete(Unit) }
    private var gate = approveGate
    private var approvals = 0

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                fun error(name: String, status: HttpStatusCode = HttpStatusCode.BadRequest) = respond("""{"error":"$name"}""", status, json)
                when ("${request.url.host}${request.url.encodedPath}") {
                    "pds.test/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)

                    "appview.test/xrpc/${Nsid.GRAPH_GET_FOLLOW_REQUESTS}" ->
                        if (failRequests) {
                            error("InternalServerError", HttpStatusCode.InternalServerError)
                        } else {
                            respond(
                                """{"requests":[{"did":"$bob","handle":"bob.test"},{"did":"$carol","handle":"carol.test"}]}""",
                                headers = json,
                            )
                        }

                    "appview.test/.well-known/did.json" ->
                        respond(
                            """{"id":"did:web:appview.test","service":[
                               {"id":"#activitytracker_space","type":"AtprotoSpaceService","serviceEndpoint":"http://appview.test"}]}""",
                            headers = json,
                        )

                    "pds.test/xrpc/com.atproto.space.listRecords" ->
                        if (scopeMissing) {
                            error(SpaceErrors.SCOPE_MISSING, HttpStatusCode.Forbidden)
                        } else {
                            respond(
                                """{"records":[{"collection":"${Nsid.GRAPH_FOLLOW_DECLINE}","rkey":"$carol","cid":"bafy"}]}""",
                                headers = json,
                            )
                        }

                    "pds.test/xrpc/com.atproto.simplespace.createSpace" ->
                        if (scopeMissing) error(SpaceErrors.SCOPE_MISSING, HttpStatusCode.Forbidden)
                        else respond("""{"uri":"at://$alice/space/${Nsid.SPACE_ATHLETE}/self"}""", headers = json)

                    "pds.test/xrpc/com.atproto.space.putRecord",
                    "pds.test/xrpc/com.atproto.space.deleteRecord" ->
                        respond("""{"uri":"at://$alice/x/y","cid":"bafyrec"}""", headers = json)

                    "pds.test/xrpc/com.atproto.repo.putRecord" -> {
                        approvals++
                        gate.await()
                        respond("""{"uri":"at://$alice/x/y","cid":"bafyrec"}""", headers = json)
                    }

                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        ) {
            expectSuccess = false
        }

    private val navigator = FakeNavigator(FollowRequestsScreen)

    private suspend fun presenter(): @Composable () -> FollowRequestsState {
        val (db, manager, clients) = signedInTestSession(http, did = alice)
        val requests = FollowRequestsRepository(clients, Clock.System, Logger)
        val auth = AuthRepository(manager, db, http)
        return { FollowRequestsPresenter(navigator, requests, auth) }
    }

    @Test
    fun declinedRequestsShowUnderDeclined() = runTest {
        presenterTestOf(presenter()) {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(listOf(bob), loaded.requests.map { it.did })
            assertEquals(listOf(carol), loaded.declined.map { it.did })
            assertNull(loaded.error)

            loaded.eventSink(FollowRequestsEvent.Open(bob))
            assertEquals(ProfileScreen(bob.value), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun approvingRemovesTheRequestAndWritesOnce() = runTest {
        gate = CompletableDeferred()
        presenterTestOf(presenter()) {
            val loaded = awaitUntil { !it.isLoading }
            loaded.eventSink(FollowRequestsEvent.Approve(bob))
            val answering = awaitUntil { bob in it.answering }
            answering.eventSink(FollowRequestsEvent.Approve(bob))

            gate.complete(Unit)
            val done = awaitUntil { it.requests.isEmpty() && bob !in it.answering }
            assertEquals(1, approvals)
            assertEquals(listOf(carol), done.declined.map { it.did })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun decliningMovesTheRequestToDeclined() = runTest {
        presenterTestOf(presenter()) {
            awaitUntil { !it.isLoading }.eventSink(FollowRequestsEvent.Decline(bob))
            val done = awaitUntil { it.requests.isEmpty() }
            assertEquals(listOf(bob, carol), done.declined.map { it.did })
            assertEquals(0, approvals)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun approvingFromDeclinedClearsIt() = runTest {
        presenterTestOf(presenter()) {
            awaitUntil { !it.isLoading }.eventSink(FollowRequestsEvent.Approve(carol))
            val done = awaitUntil { it.declined.isEmpty() }
            assertEquals(listOf(bob), done.requests.map { it.did })
            assertEquals(1, approvals)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSessionWithoutTheDeclineScopeAsksToSignInAgain() = runTest {
        scopeMissing = true
        presenterTestOf(presenter()) {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(UiError.Kind.SignInAgain, loaded.error?.kind)
            assertEquals(listOf(bob, carol), loaded.requests.map { it.did })

            loaded.eventSink(FollowRequestsEvent.Decline(bob))
            awaitUntil { bob !in it.answering && it.error?.kind == UiError.Kind.SignInAgain }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedLoadCanBeRetried() = runTest {
        failRequests = true
        presenterTestOf(presenter()) {
            val failed = awaitUntil { !it.isLoading && it.error != null }
            assertEquals(UiError.Kind.LoadFollowRequests, failed.error?.kind)
            failRequests = false
            failed.eventSink(FollowRequestsEvent.Retry)
            val recovered = awaitUntil { !it.isLoading && it.error == null && it.requests.isNotEmpty() }
            assertEquals(listOf(bob), recovered.requests.map { it.did })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
