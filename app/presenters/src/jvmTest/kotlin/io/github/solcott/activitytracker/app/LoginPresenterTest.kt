package io.github.solcott.activitytracker.app

import co.touchlab.kermit.Logger
import com.slack.circuit.test.test
import io.github.solcott.activitytracker.app.login.LoginEvent
import io.github.solcott.activitytracker.app.login.LoginPresenter
import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.oauth.OAuthClient
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
import io.github.solcott.activitytracker.data.auth.AuthBrowser
import io.github.solcott.activitytracker.data.auth.AuthCoordinator
import io.github.solcott.activitytracker.data.auth.AuthCoordinator.Status
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.auth.InMemorySecretCipher
import io.github.solcott.activitytracker.data.auth.RoomOAuthStore
import io.github.solcott.activitytracker.data.db.inMemoryDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

class LoginPresenterTest {
    private val opened = mutableListOf<String>()

    private fun coordinator(): AuthCoordinator {
        // Every network call fails: handle resolution can't succeed.
        val http = HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) }) { expectSuccess = false }
        val config =
            AtprotoConfig(
                plcUrl = "http://localhost:2582",
                handleResolverUrl = "http://localhost:2583",
                appViewUrl = "http://localhost:8081",
                appViewDid = "did:web:localhost%3A8081",
                oauth = OAuthClientConfig.loopback("http://127.0.0.1:8080/"),
            )
        val db = inMemoryDatabase()
        val manager =
            OAuthSessionManager(
                OAuthClient(http, IdentityResolver(http, config), config.oauth),
                RoomOAuthStore(db, Clock.System, InMemorySecretCipher(), Logger),
            )
        return AuthCoordinator(AuthRepository(manager, db, http), AuthBrowser { opened += it }, Logger)
    }

    @Test
    fun unresolvableHandleSurfacesError() = runTest {
        LoginPresenter(coordinator()).test {
            val initial = awaitItem()
            assertEquals("", initial.handle)
            initial.eventSink(LoginEvent.HandleChanged(" nobody.test "))
            val typed = awaitItem()
            assertEquals("nobody.test", typed.handle)
            typed.eventSink(LoginEvent.Submit)

            var state = awaitItem()
            while (state.status !is Status.Failed) state = awaitItem()
            assertIs<Status.Failed>(state.status)
            assertTrue(opened.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun submitLowercasesHandle() = runTest {
        LoginPresenter(coordinator()).test {
            awaitItem().eventSink(LoginEvent.HandleChanged("Nobody.Test"))
            awaitItem().eventSink(LoginEvent.Submit)

            var state = awaitItem()
            while (state.status !is Status.Failed) state = awaitItem()
            val failed = assertIs<Status.Failed>(state.status)
            assertTrue("nobody.test" in failed.detail.orEmpty(), failed.detail)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
