package io.github.solcott.activitytracker.app

import app.cash.turbine.ReceiveTurbine
import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.feed.FeedEvent
import io.github.solcott.activitytracker.app.feed.FeedPresenter
import io.github.solcott.activitytracker.app.feed.FeedScreen
import io.github.solcott.activitytracker.app.feed.FeedState
import io.github.solcott.activitytracker.app.record.RecordScreen
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
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.location.FakeLocationTracker
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.activitytracker.location.UnsupportedLocationTracker
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.runTest

class FeedPresenterTest {
    private val http = HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) }) { expectSuccess = false }

    private class Repos(val feed: FeedRepository, val activities: ActivityRepository, val profiles: ProfileRepository)

    private suspend fun repos(): Repos {
        val db = inMemoryDatabase()
        val store = RoomOAuthStore(db, Clock.System, InMemorySecretCipher(), Logger)
        val session =
            OAuthSession(
                did = Did("did:plc:alice"),
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
        val localKudos = LocalKudos()
        val feed = FeedRepository(db, clients, localKudos, Logger)
        val activities = ActivityRepository(db, clients, OutboxProcessor(db, clients, Logger), feed, localKudos, Clock.System)
        return Repos(feed, activities, ProfileRepository(clients, Clock.System))
    }

    private suspend fun presenterTest(
        navigator: FakeNavigator,
        tracker: LocationTracker,
        block: suspend ReceiveTurbine<FeedState>.() -> Unit,
    ) {
        val r = repos()
        presenterTestOf({ FeedPresenter(navigator, r.feed, r.activities, r.profiles, tracker) }, block = block)
    }

    @Test
    fun phonesOfferRecording() = runTest {
        val navigator = FakeNavigator(FeedScreen)
        presenterTest(navigator, FakeLocationTracker()) {
            val state = awaitItem()
            assertTrue(state.canRecord)
            state.eventSink(FeedEvent.Record)
            assertEquals(RecordScreen, navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun desktopAndWebOnlyOfferManualEntry() = runTest {
        presenterTest(FakeNavigator(FeedScreen), UnsupportedLocationTracker()) {
            val state = awaitItem()
            assertFalse(state.canRecord)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
