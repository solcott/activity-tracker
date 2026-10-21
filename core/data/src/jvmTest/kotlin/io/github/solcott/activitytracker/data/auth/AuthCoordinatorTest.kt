package io.github.solcott.activitytracker.data.auth

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.oauth.OAuthClient
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
import io.github.solcott.activitytracker.data.auth.AuthCoordinator.Status
import io.github.solcott.activitytracker.data.db.inMemoryDatabase
import io.github.solcott.dataresult.DataError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

class AuthCoordinatorTest {
    private fun coordinator(network: MockRequestHandler): AuthCoordinator {
        val http = HttpClient(MockEngine(network)) { expectSuccess = false }
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
        return AuthCoordinator(AuthRepository(manager, db, http), AuthBrowser {}, Logger)
    }

    @Test
    fun anUnknownHandleFindsNoAccount() = runTest {
        val coordinator = coordinator {
            respond(
                """{"error":"InvalidRequest","message":"Unable to resolve handle"}""",
                HttpStatusCode.BadRequest,
                headersOf("Content-Type", "application/json"),
            )
        }
        coordinator.signIn("nobody.test")
        assertEquals(Status.Failed(Status.Stage.FindAccount), coordinator.status.value)
    }

    @Test
    fun offlineFailsToStartWithANetworkReason() = runTest {
        val coordinator = coordinator { throw IOException("offline") }
        coordinator.signIn("alice.test")
        assertEquals(Status.Failed(Status.Stage.Start, DataError.Network), coordinator.status.value)
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
