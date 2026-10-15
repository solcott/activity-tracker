package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import app.cash.turbine.ReceiveTurbine
import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.app.detail.ActivityDetailPresenter
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.app.detail.DetailSection
import io.github.solcott.activitytracker.app.detail.LikesList
import io.github.solcott.activitytracker.app.detail.StreamKind
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.PdsActivityStreamsStore
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class ActivityDetailPresenterTest {
    private val alice = Did("did:plc:alice")
    private val activityUri = "at://did:plc:bob/${Nsid.FEED_ACTIVITY}/3kbob1"
    private val bob = """{"did":"did:plc:bob","handle":"bob.test","displayName":"Bob"}"""
    private val carol = """{"did":"did:plc:carol","handle":"carol.test"}"""
    private val staleAlice = """{"did":"did:plc:alice","handle":"alice.test"}"""
    private var likesFromAppView = listOf(carol)
    private var streams: String? = null
    private var visibility = "public"

    /** The comments the AppView lists, as JSON objects. */
    private var commentsFromAppView = listOf<String>()

    private fun comment(author: String, did: String, rkey: String) =
        """{"uri":"at://$did/${Nsid.FEED_COMMENT}/$rkey","cid":"bafy$rkey","author":$author,"text":"Nice",""" +
            """"createdAt":"2026-09-30T11:00:00Z"}"""

    private val activity
        get() =
            """{"uri":"$activityUri","cid":"bafy3kbob1","author":$bob,"visibility":"$visibility",""" +
                """"record":{"sport":"run","startedAt":"2026-09-30T10:00:00Z","elapsedTime":1800,"createdAt":"2026-09-30T10:31:00Z"},""" +
                """"likeCount":${likesFromAppView.size},"commentCount":${commentsFromAppView.size},"indexedAt":"2026-09-30T10:31:01Z","viewer":{}}"""

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> {
                        respond("""{"token":"service-jwt"}""", headers = json)
                    }

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY}" -> {
                        respond("""{"activity":$activity,"comments":[${commentsFromAppView.joinToString(",")}]}""", headers = json)
                    }

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY_STREAMS}" -> {
                        streams?.let { respond(it, headers = json) }
                            ?: respond("""{"error":"NotFound","message":"none"}""", HttpStatusCode.NotFound, json)
                    }

                    "/xrpc/${Nsid.FEED_GET_LIKES}" -> {
                        respond("""{"uri":"$activityUri","likes":[${likesFromAppView.joinToString(",")}]}""", headers = json)
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

    private val localLikes = LocalLikes()

    private class Repos(
        val db: AppDatabase,
        val feed: FeedRepository,
        val activities: ActivityRepository,
        val profiles: ProfileRepository,
        val manager: ActivityManager,
    )

    private suspend fun repos(scope: String = OAuthClientConfig.DEFAULT_SCOPE): Repos {
        val (db, _, clients) = signedInTestSession(http, did = alice, scope = scope)
        val feed = FeedRepository(db, clients, localLikes, Logger)
        val outbox = OutboxProcessor(db, clients, Logger)
        val writer = OutboxWriter(db, clients, Clock.System)
        val streams = PdsActivityStreamsStore(writer, clients, Clock.System)
        val activities =
            ActivityRepository(
                db,
                clients,
                outbox,
                writer,
                streams,
                feed,
                localLikes,
                RecentActivityTypes(db, clients, feed),
                Clock.System,
            )
        return Repos(
            db,
            feed,
            activities,
            ProfileRepository(clients, Clock.System, Logger),
            ActivityManager(db, clients, outbox, writer, streams, feed),
        )
    }

    private val screen = ActivityDetailScreen(activityUri)

    @Test
    fun showsHidesAndOpensLikingAthletes() = runTest {
        val navigator = FakeNavigator(screen)
        val r = repos()
        presenterTestOf({ ActivityDetailPresenter(screen, navigator, r.feed, r.activities, r.profiles, localLikes, r.manager) }) {
            val loaded = awaitLoaded()
            assertNull(loaded.likesList)

            loaded.eventSink(ActivityDetailEvent.ToggleLikesList)
            val shown = awaitLikes { it.athletes.isNotEmpty() }
            assertEquals(listOf("carol.test"), shown.likesList?.athletes?.map { it.handle.value })

            shown.eventSink(ActivityDetailEvent.OpenProfile(Did("did:plc:carol")))
            assertEquals(ProfileScreen("did:plc:carol"), navigator.awaitNextScreen())

            shown.eventSink(ActivityDetailEvent.ToggleLikesList)
            var hidden = awaitItem() as ActivityDetailState.Loaded
            while (hidden.likesList != null) hidden = awaitItem() as ActivityDetailState.Loaded
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun likesAFollowersActivity() = runTest {
        visibility = "followers"
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localLikes, r.manager)
        }) {
            val loaded = awaitLoaded()
            assertEquals(1, loaded.activity.likeCount)
            loaded.eventSink(ActivityDetailEvent.ToggleLike)
            val liked = awaitUntil { (it as? ActivityDetailState.Loaded)?.activity?.viewer?.like != null } as ActivityDetailState.Loaded
            assertEquals(2, liked.activity.likeCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSessionWhoseScopeLacksLikesIsAskedToSignInAgain() = runTest {
        visibility = "followers"
        // Signed in before the interactions space's declaration listed likes.
        val r = repos(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}")
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localLikes, r.manager)
        }) {
            val loaded = awaitLoaded()
            assertNull(loaded.likeError)
            loaded.eventSink(ActivityDetailEvent.ToggleLike)
            val refused = awaitUntil { (it as? ActivityDetailState.Loaded)?.likeError != null } as ActivityDetailState.Loaded
            assertEquals(UiError(UiError.Kind.SignInAgain, null), refused.likeError)
            assertEquals(1, refused.activity.likeCount, "the like isn't shown")
            assertNull(refused.activity.viewer?.like)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The comments Alice has queued, as (space key or null for the repo, record). */
    private suspend fun Repos.queuedComments() =
        db.outbox()
            .pending(alice.value)
            .filter { it.collection == Nsid.FEED_COMMENT }
            .map { it.space?.substringAfterLast('/') to it.recordJson }

    @Test
    fun commentsOnAFollowersActivityOnceForADoubleTap() = runTest {
        visibility = "followers"
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localLikes, r.manager)
        }) {
            val loaded = awaitLoaded()
            loaded.draftComment.setTextAndPlaceCursorAtEnd("Nice")
            loaded.eventSink(ActivityDetailEvent.SendComment)
            loaded.eventSink(ActivityDetailEvent.SendComment)
            // The write is queued off the composition; it's the only thing that reaches the outbox here.
            r.db.outbox().count(alice.value).first { it > 0 }
            val queued = r.queuedComments()
            assertEquals(listOf(AthleteSpace.INTERACTIONS), queued.map { it.first }, "one comment, in the interactions space")
            assertTrue(queued.single().second!!.contains("\"audience\":\"followers\""))
            assertEquals("", loaded.draftComment.text.toString())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSessionWhoseScopeLacksCommentsIsAskedToSignInAgainAndKeepsTheText() = runTest {
        visibility = "followers"
        // Signed in before the interactions space's declaration listed comments.
        val r = repos(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}&collection=${Nsid.FEED_LIKE}")
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localLikes, r.manager)
        }) {
            val loaded = awaitLoaded()
            loaded.draftComment.setTextAndPlaceCursorAtEnd("Nice")
            loaded.eventSink(ActivityDetailEvent.SendComment)
            val refused = awaitUntil { (it as? ActivityDetailState.Loaded)?.commentError != null } as ActivityDetailState.Loaded
            assertEquals(UiError(UiError.Kind.SignInAgain, null), refused.commentError)
            assertEquals("Nice", refused.draftComment.text.toString(), "what was typed isn't lost")
            assertEquals(emptyList(), r.queuedComments())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The deletes of comments Alice has queued, as (space key or null for the repo, rkey). */
    private suspend fun Repos.queuedCommentDeletes() =
        db.outbox()
            .pending(alice.value)
            .filter { it.collection == Nsid.FEED_COMMENT && it.operation == OutboxProcessor.OP_DELETE }
            .map { it.space?.substringAfterLast('/') to it.rkey }

    @Test
    fun deletesOwnCommentAtOnceAndOnceForADoubleConfirm() = runTest {
        visibility = "followers"
        commentsFromAppView = listOf(comment(staleAlice, alice.value, "3kc1"), comment(carol, "did:plc:carol", "3kc2"))
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localLikes, r.manager)
        }) {
            val loaded = awaitUntil { (it as? ActivityDetailState.Loaded)?.comments?.size == 2 } as ActivityDetailState.Loaded
            assertEquals(alice, loaded.viewerDid)
            val mine = loaded.comments.first().uri
            loaded.eventSink(ActivityDetailEvent.DeleteComment(mine))
            loaded.eventSink(ActivityDetailEvent.DeleteComment(mine))
            val after = awaitUntil { (it as? ActivityDetailState.Loaded)?.comments?.size == 1 } as ActivityDetailState.Loaded
            assertEquals(listOf("did:plc:carol"), after.comments.map { it.author.did.value }, "gone at once, and the count with it")
            r.db.outbox().count(alice.value).first { it >= 2 }
            assertEquals(listOf(null to "3kc1", AthleteSpace.INTERACTIONS to "3kc1"), r.queuedCommentDeletes(), "one pair of deletes")

            // A reload while the AppView still lists it keeps it hidden.
            after.eventSink(ActivityDetailEvent.Retry)
            val reloaded = awaitUntil { it is ActivityDetailState.Loaded && it.comments.size == 1 } as ActivityDetailState.Loaded
            assertEquals(listOf("did:plc:carol"), reloaded.comments.map { it.author.did.value })
            assertEquals(1, r.feed.activity(AtUri(activityUri)).activity.commentCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedDeletePutsTheCommentBackWithTheError() = runTest {
        commentsFromAppView = listOf(comment(carol, "did:plc:carol", "3kc0"), comment(staleAlice, alice.value, "3kc1"))
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localLikes, r.manager)
        }) {
            val loaded = awaitUntil { (it as? ActivityDetailState.Loaded)?.comments?.size == 2 } as ActivityDetailState.Loaded
            // Queueing the delete fails once the database is gone.
            r.db.close()
            loaded.eventSink(ActivityDetailEvent.DeleteComment(loaded.comments.last().uri))
            val failed = awaitUntil { (it as? ActivityDetailState.Loaded)?.commentError != null } as ActivityDetailState.Loaded
            assertEquals(UiError.Kind.DeleteComment, failed.commentError?.kind)
            assertEquals(loaded.comments, failed.comments, "back where it was")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun viewersOwnToggleShowsBeforeTheAppViewCatchesUp() = runTest {
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localLikes, r.manager)
        }) {
            awaitLoaded().eventSink(ActivityDetailEvent.ToggleLikesList)
            awaitLikes { it.athletes.isNotEmpty() }

            // Alice likes it locally; the AppView still lists only Carol, so Alice is added from her profile.
            localLikes.set(AtUri(activityUri), AtUri("at://did:plc:alice/${Nsid.FEED_LIKE}/3kk1"))
            val given = awaitLikes { it.athletes.size == 2 }
            assertEquals(listOf("Alice", null), given.likesList?.athletes?.map { it.displayName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun viewersRemovedLikeIsDroppedFromAStaleList() = runTest {
        likesFromAppView = listOf(staleAlice, carol)
        val r = repos()
        localLikes.set(AtUri(activityUri), null)
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localLikes, r.manager)
        }) {
            awaitLoaded().eventSink(ActivityDetailEvent.ToggleLikesList)
            val shown = awaitLikes { it.athletes.isNotEmpty() }
            assertEquals(listOf("carol.test"), shown.likesList?.athletes?.map { it.handle.value })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun chartsTheStreamsTheViewerMaySee() = runTest {
        streams =
            """{"uri":"$activityUri","visibility":"public","channels":["heartRate"],""" +
                """"data":{"time":[0,1,2,3],"heartRate":[100,150,160,null]},""" +
                """"heartRateZones":{"model":"cogganHeartRate","lows":[0,138,154,164,176]}}"""
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localLikes, r.manager)
        }) {
            var state = awaitLoaded()
            while (state.charts == null) state = awaitLoaded()
            val charts = state.charts!!
            assertEquals(listOf(StreamKind.HeartRate), charts.lines.map { it.kind })
            assertEquals(listOf(1, 1, 1, 0, 0), charts.heartRateZones?.map { it.seconds })
            assertFalse(state.chartsMissingZones)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sectionsStartCollapsedAndToggleOneOrAll() = runTest {
        streams =
            """{"uri":"$activityUri","visibility":"public","channels":["heartRate","power"],""" +
                """"data":{"time":[0,1,2,3],"heartRate":[100,150,160,null],"power":[200,250,300,null]}}"""
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localLikes, r.manager)
        }) {
            var state = awaitLoaded()
            while (state.charts == null) state = awaitLoaded()
            assertEquals(listOf(DetailSection.HeartRate, DetailSection.Power), state.sections)
            assertEquals(emptySet(), state.expanded)

            state.eventSink(ActivityDetailEvent.ToggleSection(DetailSection.HeartRate))
            state = awaitUntil { (it as? ActivityDetailState.Loaded)?.anyExpanded == true } as ActivityDetailState.Loaded
            assertEquals(setOf(DetailSection.HeartRate), state.expanded)

            // Something open, so all collapse; then nothing open, so all expand.
            state.eventSink(ActivityDetailEvent.ToggleAll)
            state = awaitUntil { (it as? ActivityDetailState.Loaded)?.anyExpanded == false } as ActivityDetailState.Loaded
            state.eventSink(ActivityDetailEvent.ToggleAll)
            state = awaitUntil { (it as? ActivityDetailState.Loaded)?.anyExpanded == true } as ActivityDetailState.Loaded
            assertEquals(state.sections.toSet(), state.expanded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noStreamsMeansNoCharts() = runTest {
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localLikes, r.manager)
        }) {
            assertNull(awaitLoaded().charts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<ActivityDetailState>.awaitLoaded() =
        awaitUntil { it is ActivityDetailState.Loaded } as ActivityDetailState.Loaded

    private suspend fun ReceiveTurbine<ActivityDetailState>.awaitLikes(done: (LikesList) -> Boolean): ActivityDetailState.Loaded {
        var state = awaitLoaded()
        while (state.likesList?.let { !it.isLoading && done(it) } != true) state = awaitLoaded()
        return state
    }
}
