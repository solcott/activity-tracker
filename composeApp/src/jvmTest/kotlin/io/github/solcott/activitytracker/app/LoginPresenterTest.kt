package io.github.solcott.activitytracker.app

import com.slack.circuit.test.test
import io.github.solcott.activitytracker.app.auth.AuthBrowser
import io.github.solcott.activitytracker.app.auth.AuthCoordinator
import io.github.solcott.activitytracker.app.auth.AuthCoordinator.Status
import io.github.solcott.activitytracker.app.auth.parseRedirectParams
import io.github.solcott.activitytracker.app.login.LoginEvent
import io.github.solcott.activitytracker.app.login.LoginPresenter
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.oauth.OAuthClient
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
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
        val config = NetworkConfigs.web("http://127.0.0.1:8080")
        val db = inMemoryDatabase()
        val manager =
            OAuthSessionManager(
                OAuthClient(http, IdentityResolver(http, config), config.oauth),
                RoomOAuthStore(db, Clock.System, InMemorySecretCipher()),
            )
        return AuthCoordinator(AuthRepository(manager, db, http), AuthBrowser { opened += it })
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
    fun parsesRedirectQuery() {
        val params = parseRedirectParams("app.activitytracker:/oauth/callback?code=abc&state=s%2B1&iss=http%3A%2F%2Flocalhost%3A2583")
        assertEquals(
            mapOf("code" to "abc", "state" to "s+1", "iss" to "http://localhost:2583"),
            params,
        )
    }
}
