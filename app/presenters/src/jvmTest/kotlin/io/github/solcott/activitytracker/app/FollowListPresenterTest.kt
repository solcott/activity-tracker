package io.github.solcott.activitytracker.app

import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.profile.FollowListEvent
import io.github.solcott.activitytracker.app.profile.FollowListKind
import io.github.solcott.activitytracker.app.profile.FollowListPresenter
import io.github.solcott.activitytracker.app.profile.FollowListScreen
import io.github.solcott.activitytracker.app.profile.FollowListState
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.oauth.OAuthClient
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
import io.github.solcott.activitytracker.data.auth.InMemorySecretCipher
import io.github.solcott.activitytracker.data.auth.RoomOAuthStore
import io.github.solcott.activitytracker.data.db.inMemoryDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.runTest

class FollowListPresenterTest {
    private val alice = Did("did:plc:alice")
    private val bob = """{"did":"did:plc:bob","handle":"bob.test","displayName":"Bob"}"""
    private val carol = """{"did":"did:plc:carol","handle":"carol.test"}"""
    private val dave = """{"did":"did:plc:dave","handle":"dave.test"}"""
    private var failAppView = false

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> {
                        respond("""{"token":"service-jwt"}""", headers = json)
                    }

                    "/xrpc/${Nsid.GRAPH_GET_FOLLOWERS}" -> {
                        when {
                            failAppView -> {
                                respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                            }

                            request.url.parameters["cursor"] == null -> {
                                respond("""{"subject":$bob,"followers":[$carol],"cursor":"c1"}""", headers = json)
                            }

                            else -> {
                                respond("""{"subject":$bob,"followers":[$dave]}""", headers = json)
                            }
                        }
                    }

                    else -> {
                        respond("", HttpStatusCode.NotFound)
                    }
                }
            }
        ) {
            expectSuccess = false
        }

    private suspend fun profiles(): ProfileRepository {
        val db = inMemoryDatabase()
        val store = RoomOAuthStore(db, Clock.System, InMemorySecretCipher(), Logger)
        val session =
            OAuthSession(
                did = alice,
                handle = Handle("alice.test"),
                pdsUrl = "http://pds.test",
                issuer = "http://pds.test",
                tokenEndpoint = "http://pds.test/oauth/token",
                accessToken = "at",
                refreshToken = "rt",
                expiresAt = Clock.System.now() + 1.hours,
                scope = "atproto",
                dpopKey = DpopKey.generate().serialize(),
            )
        store.saveSession(session)
        val config =
            AtprotoConfig(
                "http://plc.test",
                null,
                "http://appview.test",
                "did:web:appview.test",
                OAuthClientConfig.loopback("http://127.0.0.1/"),
            )
        val manager = OAuthSessionManager(OAuthClient(http, IdentityResolver(http, config), config.oauth), store).apply { restore() }
        return ProfileRepository(SessionClients(session, http, manager, config, Clock.System), Clock.System)
    }

    private val screen = FollowListScreen("bob.test", FollowListKind.Followers)

    @Test
    fun loadsPagesAndOpensProfiles() = runTest {
        val navigator = FakeNavigator(screen)
        val repo = profiles()
        presenterTestOf({ FollowListPresenter(screen, navigator, repo) }) {
            val first = awaitLoaded()
            assertEquals("bob.test", first.subject?.handle?.value)
            assertEquals(listOf("carol.test"), first.accounts.map { it.handle.value })
            assertEquals(true, first.canLoadMore)

            first.eventSink(FollowListEvent.LoadMore)
            var second = awaitItem()
            while (second.isLoading || second.accounts.size < 2) second = awaitItem()
            assertEquals(listOf("carol.test", "dave.test"), second.accounts.map { it.handle.value })
            assertEquals(false, second.canLoadMore)

            second.eventSink(FollowListEvent.Open(second.accounts.last().did))
            assertEquals(ProfileScreen("did:plc:dave"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun failureSurfacesErrorAndRetryRecovers() = runTest {
        failAppView = true
        val repo = profiles()
        presenterTestOf({ FollowListPresenter(screen, FakeNavigator(screen), repo) }) {
            val failed = awaitLoaded()
            assertNotNull(failed.error)
            assertEquals(false, failed.canLoadMore)

            failAppView = false
            failed.eventSink(FollowListEvent.Retry)
            var recovered = awaitItem()
            while (recovered.isLoading || recovered.error != null) recovered = awaitItem()
            assertEquals(listOf("carol.test"), recovered.accounts.map { it.handle.value })
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<FollowListState>.awaitLoaded(): FollowListState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }
}
