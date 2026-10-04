package io.github.solcott.activitytracker.app

import app.cash.turbine.ReceiveTurbine
import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.app.detail.ActivityDetailPresenter
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.app.detail.KudosList
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.oauth.OAuthClient
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.auth.InMemorySecretCipher
import io.github.solcott.activitytracker.data.auth.RoomOAuthStore
import io.github.solcott.activitytracker.data.db.inMemoryDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalKudos
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.AtUri
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
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.runTest

class ActivityDetailPresenterTest {
    private val alice = Did("did:plc:alice")
    private val activityUri = "at://did:plc:bob/${Nsid.FEED_ACTIVITY}/3kbob1"
    private val bob = """{"did":"did:plc:bob","handle":"bob.test","displayName":"Bob"}"""
    private val carol = """{"did":"did:plc:carol","handle":"carol.test"}"""
    private val staleAlice = """{"did":"did:plc:alice","handle":"alice.test"}"""
    private var kudosFromAppView = listOf(carol)

    private val activity =
        """{"uri":"$activityUri","cid":"bafy3kbob1","author":$bob,""" +
            """"record":{"sport":"run","startedAt":"2026-09-30T10:00:00Z","elapsedTime":1800,"createdAt":"2026-09-30T10:31:00Z"},""" +
            """"kudosCount":${kudosFromAppView.size},"commentCount":0,"indexedAt":"2026-09-30T10:31:01Z","viewer":{}}"""

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> {
                        respond("""{"token":"service-jwt"}""", headers = json)
                    }

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY}" -> {
                        respond("""{"activity":$activity,"comments":[]}""", headers = json)
                    }

                    "/xrpc/${Nsid.FEED_GET_KUDOS}" -> {
                        respond("""{"uri":"$activityUri","kudos":[${kudosFromAppView.joinToString(",")}]}""", headers = json)
                    }

                    "/xrpc/${Nsid.ACTOR_GET_PROFILE}" -> {
                        respond("""{"did":"did:plc:alice","handle":"alice.test","displayName":"Alice"}""", headers = json)
                    }

                    else -> {
                        respond("", HttpStatusCode.NotFound)
                    }
                }
            }
        ) {
            expectSuccess = false
        }

    private val localKudos = LocalKudos()

    private class Repos(val feed: FeedRepository, val activities: ActivityRepository, val profiles: ProfileRepository)

    private suspend fun repos(): Repos {
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
        val clients = SessionClients(session, http, manager, config, Clock.System)
        val feed = FeedRepository(db, clients, localKudos, Logger)
        val activities = ActivityRepository(db, clients, OutboxProcessor(db, clients, Logger), feed, localKudos, Clock.System)
        return Repos(feed, activities, ProfileRepository(clients, Clock.System))
    }

    private val screen = ActivityDetailScreen(activityUri)

    @Test
    fun showsHidesAndOpensKudosAthletes() = runTest {
        val navigator = FakeNavigator(screen)
        val r = repos()
        presenterTestOf({ ActivityDetailPresenter(screen, navigator, r.feed, r.activities, r.profiles, localKudos) }) {
            val loaded = awaitLoaded()
            assertNull(loaded.kudosList)

            loaded.eventSink(ActivityDetailEvent.ToggleKudosList)
            val shown = awaitKudos { it.athletes.isNotEmpty() }
            assertEquals(listOf("carol.test"), shown.kudosList?.athletes?.map { it.handle.value })

            shown.eventSink(ActivityDetailEvent.OpenProfile(Did("did:plc:carol")))
            assertEquals(ProfileScreen("did:plc:carol"), navigator.awaitNextScreen())

            shown.eventSink(ActivityDetailEvent.ToggleKudosList)
            var hidden = awaitItem() as ActivityDetailState.Loaded
            while (hidden.kudosList != null) hidden = awaitItem() as ActivityDetailState.Loaded
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun viewersOwnToggleShowsBeforeTheAppViewCatchesUp() = runTest {
        val r = repos()
        presenterTestOf({ ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localKudos) }) {
            awaitLoaded().eventSink(ActivityDetailEvent.ToggleKudosList)
            awaitKudos { it.athletes.isNotEmpty() }

            // Alice gives kudos locally; the AppView still lists only Carol, so Alice is added from her profile.
            localKudos.set(AtUri(activityUri), AtUri("at://did:plc:alice/${Nsid.FEED_KUDOS}/3kk1"))
            val given = awaitKudos { it.athletes.size == 2 }
            assertEquals(listOf("Alice", null), given.kudosList?.athletes?.map { it.displayName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun viewersRemovedKudosIsDroppedFromAStaleList() = runTest {
        kudosFromAppView = listOf(staleAlice, carol)
        val r = repos()
        localKudos.set(AtUri(activityUri), null)
        presenterTestOf({ ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localKudos) }) {
            awaitLoaded().eventSink(ActivityDetailEvent.ToggleKudosList)
            val shown = awaitKudos { it.athletes.isNotEmpty() }
            assertEquals(listOf("carol.test"), shown.kudosList?.athletes?.map { it.handle.value })
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<ActivityDetailState>.awaitLoaded(): ActivityDetailState.Loaded {
        var state = awaitItem()
        while (state !is ActivityDetailState.Loaded) state = awaitItem()
        return state
    }

    private suspend fun ReceiveTurbine<ActivityDetailState>.awaitKudos(done: (KudosList) -> Boolean): ActivityDetailState.Loaded {
        var state = awaitLoaded()
        while (state.kudosList?.let { !it.isLoading && done(it) } != true) state = awaitLoaded()
        return state
    }
}
