package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.interactions.CommentEvent
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Liking comments from the Comments screen (feed/comment-likes): shown at once, placed by the comment's tier, never one's own. */
class CommentLikesPresenterTest : CommentsTestBase() {
    /** The likes Alice has queued, as (operation, space key or null for the repo, record). */
    private suspend fun Repos.queuedLikes() =
        db.outbox()
            .pending(alice.value)
            .filter { it.collection == Nsid.FEED_LIKE }
            .map { Triple(it.operation, it.space?.substringAfterLast('/'), it.recordJson) }

    /** [queuedLikes] once one is queued: other writes (a non-public activity's space setup) can land in the outbox before it. */
    private suspend fun Repos.awaitQueuedLikes() = db.outbox().count(alice.value).first { queuedLikes().isNotEmpty() }.let { queuedLikes() }

    @Test
    fun likingSomeoneElsesCommentShowsAtOnceAndGoesToTheRepo() = runTest {
        commentsFromAppView = listOf(comment(bob, "did:plc:bob", "3kc1"))
        val r = repos()
        commentsOf(r) {
            val loaded = awaitUntil { it.page()?.comments?.roots?.size == 1 }.page()!!
            val bobs = loaded.comments.roots.single()
            loaded.eventSink(CommentEvent.Like(bobs.uri))
            val liked = awaitUntil { it.page()?.comments?.roots?.single()?.viewer?.like != null }.page()!!
            assertEquals(1, liked.comments.roots.single().likeCount)
            val (op, space, record) = r.awaitQueuedLikes().single()
            assertEquals(OutboxProcessor.OP_PUT, op)
            assertNull(space, "a public comment's like is in the repo")
            assertTrue(record!!.contains(bobs.uri.value), "the like names the comment")

            liked.eventSink(CommentEvent.Like(bobs.uri))
            val unliked = awaitUntil { it.page()?.comments?.roots?.single()?.viewer?.like == null }.page()!!
            assertEquals(0, unliked.comments.roots.single().likeCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aLikeOnACommentOnAFollowersActivityGoesToTheInteractionsSpace() = runTest {
        visibility = "followers"
        commentsFromAppView = listOf(comment(bob, "did:plc:bob", "3kc1", audience = "followers"))
        val r = repos()
        commentsOf(r) {
            val loaded = awaitUntil { it.page()?.comments?.roots?.size == 1 }.page()!!
            loaded.eventSink(CommentEvent.Like(loaded.comments.roots.single().uri))
            assertEquals(AthleteSpace.INTERACTIONS, r.awaitQueuedLikes().single().second)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aPublicCommentNarrowedByItsAudienceGoesToTheInteractionsSpace() = runTest {
        commentsFromAppView = listOf(comment(bob, "did:plc:bob", "3kc1", audience = "followers"))
        val r = repos()
        commentsOf(r) {
            val loaded = awaitUntil { it.page()?.comments?.roots?.size == 1 }.page()!!
            loaded.eventSink(CommentEvent.Like(loaded.comments.roots.single().uri))
            assertEquals(AthleteSpace.INTERACTIONS, r.awaitQueuedLikes().single().second, "written for followers on a public activity")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theViewersOwnCommentIsntLiked() = runTest {
        commentsFromAppView = listOf(comment(staleAlice, alice.value, "3kc1"))
        val r = repos()
        commentsOf(r) {
            val loaded = awaitUntil { it.page()?.comments?.roots?.size == 1 }.page()!!
            val own = loaded.comments.roots.single()
            loaded.eventSink(CommentEvent.Like(own.uri))
            assertNull(loaded.commentError)
            assertEquals(emptyList(), r.queuedLikes())
            assertNotNull(own)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
