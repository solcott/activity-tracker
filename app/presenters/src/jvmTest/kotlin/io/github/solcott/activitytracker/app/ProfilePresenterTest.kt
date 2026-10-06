package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.profile.ProfileEvent
import io.github.solcott.activitytracker.app.profile.ProfilePresenter
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.profile.ProfileState
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalKudos
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

/** Alice views Bob's profile. The AppView never catches up with her follow, as when Jetstream lags. */
class ProfilePresenterTest {
    private val alice = Did("did:plc:alice")
    private val bob = """{"did":"did:plc:bob","handle":"bob.test","followersCount":3}"""

    /** Holds each `putRecord` until completed, so the test sees the follow in flight. */
    private val putGate = CompletableDeferred<Unit>()
    private var failPut = false
    private var puts = 0

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> {
                        respond("""{"token":"service-jwt"}""", headers = json)
                    }

                    "/xrpc/${Nsid.ACTOR_GET_PROFILE}" -> {
                        respond(bob, headers = json)
                    }

                    "/xrpc/${Nsid.FEED_GET_AUTHOR_ACTIVITIES}" -> {
                        respond("""{"activities":[]}""", headers = json)
                    }

                    "/xrpc/com.atproto.repo.putRecord" -> {
                        puts++
                        putGate.await()
                        if (failPut) {
                            respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        } else {
                            respond("""{"uri":"at://$alice/x/y","cid":"bafyrec"}""", headers = json)
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

    private suspend fun presenter(): @Composable () -> ProfileState {
        val (db, _, clients) = signedInTestSession(http, did = alice)
        val localKudos = LocalKudos()
        val feed = FeedRepository(db, clients, localKudos, Logger)
        val activities =
            ActivityRepository(
                db,
                clients,
                OutboxProcessor(db, clients, Logger),
                feed,
                localKudos,
                RecentActivityTypes(db, clients, feed),
                Clock.System,
            )
        val profiles = ProfileRepository(clients, Clock.System)
        val screen = ProfileScreen("did:plc:bob")
        val navigator = FakeNavigator(screen)
        return { ProfilePresenter(screen, navigator, profiles, feed, activities) }
    }

    @Test
    fun followShowsProgressAndIgnoresTapsUntilItLands() = runTest {
        presenterTestOf(presenter()) {
            val loaded = awaitUntil { it.profile != null && it.error == null }
            loaded.eventSink(ProfileEvent.ToggleFollow)
            val pending = awaitUntil { it.followPending }
            pending.eventSink(ProfileEvent.ToggleFollow)
            loaded.eventSink(ProfileEvent.ToggleFollow)

            putGate.complete(Unit)
            val done = awaitUntil { !it.followPending }
            assertNotNull(done.profile?.viewer?.following)
            assertEquals(4L, done.profile?.followersCount)
            assertEquals(1, puts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun failedFollowShowsAnErrorAndCanBeRetried() = runTest {
        failPut = true
        putGate.complete(Unit)
        presenterTestOf(presenter()) {
            awaitUntil { it.profile != null && it.error == null }.eventSink(ProfileEvent.ToggleFollow)
            val failed = awaitUntil { it.error != null && !it.followPending }
            assertEquals(UiError.Kind.UpdateFollow, failed.error?.kind)
            assertNull(failed.profile?.viewer?.following)

            failPut = false
            failed.eventSink(ProfileEvent.ToggleFollow)
            assertNotNull(awaitUntil { it.profile?.viewer?.following != null }.profile)
            assertEquals(2, puts)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
