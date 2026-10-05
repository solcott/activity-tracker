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
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalKudos
import io.github.solcott.activitytracker.data.profile.ProfileRepository
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
import kotlinx.coroutines.test.runTest

class FeedPresenterTest {
    private val http = HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) }) { expectSuccess = false }

    private class Repos(val feed: FeedRepository, val activities: ActivityRepository, val profiles: ProfileRepository)

    private suspend fun repos(): Repos {
        val (db, _, clients) = signedInTestSession(http)
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
