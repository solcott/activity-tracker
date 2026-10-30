package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.app.login.Expiry
import io.github.solcott.activitytracker.app.login.LoginEvent
import io.github.solcott.activitytracker.app.login.LoginPresenter
import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.oauth.OAuthClient
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.auth.AuthBrowser
import io.github.solcott.activitytracker.data.auth.AuthCoordinator
import io.github.solcott.activitytracker.data.auth.AuthCoordinator.Status
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.auth.InMemorySecretCipher
import io.github.solcott.activitytracker.data.auth.RoomOAuthStore
import io.github.solcott.activitytracker.data.db.inMemoryDatabase
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.location.ActivityRecorder
import io.github.solcott.activitytracker.location.FakeLocationTracker
import io.github.solcott.activitytracker.location.RecordingState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class LoginPresenterTest {
    private val opened = mutableListOf<String>()
    private val resolvedHandles = mutableListOf<String?>()

    // Every network call fails: handle resolution can't succeed, and a refresh is refused.
    private val http =
        HttpClient(
            MockEngine { request ->
                if (request.url.encodedPath.endsWith("/com.atproto.identity.resolveHandle")) {
                    resolvedHandles += request.url.parameters["handle"]
                }
                if (request.url.encodedPath == "/oauth/token") {
                    respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest, headersOf("Content-Type", "application/json"))
                } else {
                    respond("", HttpStatusCode.NotFound)
                }
            }
        ) {
            expectSuccess = false
        }
    private val db = inMemoryDatabase()
    private val store = RoomOAuthStore(db, Clock.System, InMemorySecretCipher(), Logger)
    private val recorder = ActivityRecorder(FakeLocationTracker(), Clock.System)
    private lateinit var manager: OAuthSessionManager
    private lateinit var auth: AuthRepository

    private fun presenter(): LoginPresenter {
        val config =
            AtprotoConfig(
                plcUrl = "http://localhost:2582",
                handleResolverUrl = "http://localhost:2583",
                appViewUrl = "http://localhost:8081",
                appViewDid = "did:web:localhost%3A8081",
                oauth = OAuthClientConfig.loopback("http://127.0.0.1:8080/"),
            )
        manager = OAuthSessionManager(OAuthClient(http, IdentityResolver(http, config), config.oauth), store)
        auth = AuthRepository(manager, db, http, recorder)
        return LoginPresenter(AuthCoordinator(auth, AuthBrowser { opened += it }, Logger), auth, recorder)
    }

    /** Alice was signed in, and her refresh was refused. */
    private suspend fun TestScope.aliceExpired() {
        store.saveSession(
            OAuthSession(
                did = Did(ALICE),
                handle = Handle("alice.test"),
                pdsUrl = "http://localhost:2583",
                issuer = "http://localhost:2583",
                tokenEndpoint = "http://localhost:2583/oauth/token",
                accessToken = "at1",
                refreshToken = "rt1",
                expiresAt = null,
                scope = "atproto",
                dpopKey = DpopKey.generate().serialize(),
            )
        )
        manager.restore()
        backgroundScope.launch { auth.endExpiredSessions() }
        runSuspendCatching { manager.freshSession(forceRefresh = true) }
        auth.lastExpiry.first { it != null }
    }

    @Test
    fun afterAnExpiryTheHandleIsFilledIn() = runTest {
        val presenter = presenter()
        aliceExpired()
        presenter.test {
            val state = awaitItem()
            assertEquals(Expiry(recording = false), state.expired)
            assertEquals("alice.test", state.handle.text.toString())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theExpiryMessageFollowsTheRecording() = runTest {
        val presenter = presenter()
        recorder.start(ALICE)
        recorder.state.first { it.status == RecordingState.Status.Recording }
        aliceExpired()
        presenter.test {
            assertEquals(Expiry(recording = true), awaitItem().expired)
            recorder.discard()
            var state = awaitItem()
            while (state.expired?.recording != false) state = awaitItem()
            assertEquals(Expiry(recording = false), state.expired)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aChosenSignOutShowsNoExpiry() = runTest {
        val presenter = presenter()
        aliceExpired()
        auth.signOut()
        presenter.test {
            val state = awaitItem()
            assertNull(state.expired)
            assertEquals("", state.handle.text.toString())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unresolvableHandleSurfacesError() = runTest {
        presenter().test {
            val initial = awaitItem()
            assertEquals("", initial.handle.text.toString())
            // Surrounding spaces are trimmed on submit.
            initial.handle.setTextAndPlaceCursorAtEnd(" nobody.test ")
            initial.eventSink(LoginEvent.Submit)

            var state = awaitItem()
            while (state.status !is Status.Failed) state = awaitItem()
            assertIs<Status.Failed>(state.status)
            assertTrue(opened.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun submitLowercasesHandle() = runTest {
        presenter().test {
            val initial = awaitItem()
            initial.handle.setTextAndPlaceCursorAtEnd("Nobody.Test")
            initial.eventSink(LoginEvent.Submit)

            var state = awaitItem()
            while (state.status !is Status.Failed) state = awaitItem()
            assertEquals(Status.Failed(Status.Stage.FindAccount), state.status)
            assertEquals(listOf<String?>("nobody.test"), resolvedHandles)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val ALICE = "did:plc:alice"
    }
}
