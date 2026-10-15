package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.activitylist.ActivityListEvent
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.profile.FollowRequestsScreen
import io.github.solcott.activitytracker.app.profile.ProfileEvent
import io.github.solcott.activitytracker.app.profile.ProfilePresenter
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.profile.ProfileState
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.profile.FollowRequestsRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Alice views Bob's profile. The AppView never catches up with her follow, as when Jetstream lags. */
class ProfilePresenterTest {
    private val alice = Did("did:plc:alice")
    private var bob = """{"did":"did:plc:bob","handle":"bob.test","followersCount":3}"""

    /** Holds each `putRecord` until completed, so the test sees the follow in flight. */
    private val putGate = CompletableDeferred<Unit>()
    private val putStarted = CompletableDeferred<Unit>()
    private var failPut = false
    private var puts = 0

    /** Each `com.atproto.space.putRecord`, as `<space key> <collection>`. */
    private val spacePuts = mutableListOf<String>()

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> {
                        respond("""{"token":"service-jwt"}""", headers = json)
                    }

                    "/xrpc/${Nsid.ACTOR_GET_PROFILE}" -> {
                        if (request.url.parameters["actor"] == alice.value) {
                            respond("""{"did":"$alice","handle":"alice.test","followPolicy":"approve"}""", headers = json)
                        } else {
                            respond(bob, headers = json)
                        }
                    }

                    "/xrpc/${Nsid.GRAPH_GET_FOLLOW_REQUESTS}" -> {
                        respond(
                            """{"requests":[{"did":"did:plc:bob","handle":"bob.test"},{"did":"did:plc:carol","handle":"carol.test"}]}""",
                            headers = json,
                        )
                    }

                    "/xrpc/com.atproto.space.listRecords" -> {
                        respond("""{"error":"SpaceNotFound"}""", HttpStatusCode.BadRequest, json)
                    }

                    "/.well-known/did.json" -> {
                        respond(
                            """{"id":"did:web:appview.test","service":[
                               {"id":"#activitytracker_space","type":"AtprotoSpaceService","serviceEndpoint":"http://appview.test"}]}""",
                            headers = json,
                        )
                    }

                    "/xrpc/com.atproto.simplespace.createSpace" -> {
                        respond("""{"uri":"at://$alice/space/${Nsid.SPACE_ATHLETE}/interactions"}""", headers = json)
                    }

                    "/xrpc/com.atproto.space.putRecord" -> {
                        val input = AtJson.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                        val space = input.jsonObject.getValue("space").jsonPrimitive.content.substringAfterLast('/')
                        spacePuts += "$space ${input.jsonObject.getValue("collection").jsonPrimitive.content}"
                        respond("""{"uri":"at://$alice/x/y","cid":"bafyrec"}""", headers = json)
                    }

                    "/xrpc/${Nsid.FEED_GET_AUTHOR_ACTIVITIES}" -> {
                        respond("""{"activities":[]}""", headers = json)
                    }

                    "/xrpc/com.atproto.repo.putRecord",
                    "/xrpc/com.atproto.repo.deleteRecord" -> {
                        puts++
                        putStarted.complete(Unit)
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

    private val navigator = FakeNavigator(ProfileScreen("did:plc:bob"))
    private lateinit var activities: ActivityRepository

    private suspend fun presenter(actor: String = "did:plc:bob"): @Composable () -> ProfileState {
        val (db, _, clients) = signedInTestSession(http, did = alice)
        val localLikes = LocalLikes()
        val feed = FeedRepository(db, clients, localLikes, Logger)
        val zones = TestZones(db, clients)
        activities =
            ActivityRepository(
                db,
                clients,
                zones.outbox,
                zones.writer,
                zones.streams,
                zones.store,
                feed,
                localLikes,
                RecentActivityTypes(db, clients, feed),
                Clock.System,
            )
        val profiles = ProfileRepository(clients, Clock.System, Logger, zones.store)
        val requests = FollowRequestsRepository(clients, Clock.System, Logger)
        return { ProfilePresenter(ProfileScreen(actor), navigator, profiles, feed, activities, requests) }
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

    @Test
    fun likeIgnoresTapsUntilItLands() = runTest {
        presenterTestOf(presenter()) {
            val loaded = awaitUntil { it.profile != null && it.error == null }
            loaded.eventSink(ProfileEvent.Activity(ActivityListEvent.ToggleLike(bobsRide)))
            putStarted.await()
            loaded.eventSink(ProfileEvent.Activity(ActivityListEvent.ToggleLike(bobsRide)))

            putGate.complete(Unit)
            // Room emits off the test scheduler, so this waits on the real drain (bounded by runTest\'s timeout).
            activities.pendingWrites.first { it == 0 }
            assertEquals(1, puts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aLikeOnAFollowersActivityGoesInTheInteractionsSpace() = runTest {
        putGate.complete(Unit)
        presenterTestOf(presenter()) {
            val loaded = awaitUntil { it.profile != null && it.error == null }
            loaded.eventSink(ProfileEvent.Activity(ActivityListEvent.ToggleLike(bobsRide.copy(visibility = Visibility.FOLLOWERS))))
            activities.pendingWrites.first { it == 0 }
            assertEquals(0, puts, "nothing in the repo")
            assertEquals(listOf("${AthleteSpace.INTERACTIONS} ${Nsid.FEED_LIKE}"), spacePuts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun followingAnAthleteWhoApprovesShowsRequested() = runTest {
        bob = """{"did":"did:plc:bob","handle":"bob.test","followersCount":3,"followPolicy":"approve"}"""
        putGate.complete(Unit)
        presenterTestOf(presenter()) {
            awaitUntil { it.profile != null && it.error == null }.eventSink(ProfileEvent.ToggleFollow)
            val requested = awaitUntil { it.profile?.viewer?.following != null && !it.followPending }
            assertEquals(true, requested.profile?.viewer?.followRequested)
            assertEquals(3L, requested.profile?.followersCount)

            // Tapping Requested cancels the request.
            requested.eventSink(ProfileEvent.ToggleFollow)
            val cancelled = awaitUntil { it.profile?.viewer?.following == null && !it.followPending }
            assertNull(cancelled.profile?.viewer?.followRequested)
            assertEquals(2, puts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun ownProfileCountsFollowRequestsAndOpensThem() = runTest {
        presenterTestOf(presenter(actor = alice.value)) {
            val loaded = awaitUntil { it.followRequestCount != null }
            assertEquals(2, loaded.followRequestCount)
            assertEquals(false, loaded.moreFollowRequests)
            loaded.eventSink(ProfileEvent.OpenFollowRequests)
            assertEquals(FollowRequestsScreen, navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anotherAthletesProfileHasNoRequests() = runTest {
        presenterTestOf(presenter()) {
            assertNull(awaitUntil { it.profile != null }.followRequestCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun tappingAnActivityOpensIt() = runTest {
        presenterTestOf(presenter()) {
            awaitItem().eventSink(ProfileEvent.Activity(ActivityListEvent.Open(bobsRide)))
            assertEquals(ActivityDetailScreen(bobsRide.uri.value), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private val bobsRide =
        ActivityView(
            uri = AtUri.of(Did("did:plc:bob"), Nsid.FEED_ACTIVITY, "3kride"),
            cid = Cid("bafy3kride"),
            author = ProfileViewBasic(Did("did:plc:bob"), Handle("bob.test")),
            record = ActivityRecord("ride", Instant.parse("2026-03-05T08:00:00Z"), 1800, Instant.parse("2026-03-05T09:00:00Z")),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse("2026-03-05T09:00:00Z"),
        )
}
