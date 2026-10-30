package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withCompositionLocals
import com.slack.circuit.retained.LocalRetainedStateRegistry
import com.slack.circuit.retained.RetainedStateRegistry
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.app.detail.RepliesState
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.CommentRecord
import io.github.solcott.uistate.LoadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** The detail screen's replies: replying, threads with their first replies, the rest a page at a time, and sent replies. */
class ActivityDetailRepliesTest : ActivityDetailTestBase() {
    /** Bob's comment `3kroot` and [count] replies to it from carol, `3kr00`…, oldest first. */
    private fun bobsThread(count: Int): List<String> {
        val root = commentUri("did:plc:bob", "3kroot")
        return listOf(comment(bob, "did:plc:bob", "3kroot")) +
            (0 until count).map { comment(carol, "did:plc:carol", "3kr" + it.toString().padStart(2, '0'), root = root) }
    }

    /** The `reply` of the one comment Alice has queued, as (root rkey, parent rkey); null for a top-level comment. */
    private suspend fun Repos.queuedReply(): Pair<String, String>? {
        val record = AtJson.decodeFromString(CommentRecord.serializer(), queuedComments().single().second!!)
        return record.reply?.let { it.root.uri.rkey!! to it.parent.uri.rkey!! }
    }

    @Test
    fun repliesToAComment() = runTest {
        commentsFromAppView = bobsThread(2)
        val r = repos()
        detail(r) {
            val loaded = awaitUntil { it.loaded()?.comments?.items?.size == 1 }.loaded()!!
            val bobs = loaded.comments.items.single().root!!
            loaded.eventSink(ActivityDetailEvent.ReplyTo(bobs.uri))
            val replying = awaitUntil { it.loaded()?.replyingTo != null }.loaded()!!
            assertEquals(bobs, replying.replyingTo)
            replying.draftComment.setTextAndPlaceCursorAtEnd("Agreed")
            replying.eventSink(ActivityDetailEvent.SendComment)

            val sent = awaitUntil { it.loaded()?.comments?.items?.single()?.sentReplies?.size == 1 && it.loaded()?.replyingTo == null }
            assertEquals(4, sent.loaded()!!.activity.commentCount, "counted at once")
            assertEquals(emptyList(), sent.loaded()!!.sentComments, "in its thread, not after the threads")
            r.db.outbox().count(alice.value).first { it > 0 }
            assertEquals("3kroot" to "3kroot", r.queuedReply(), "bob's comment is both its parent and its thread's root")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun repliesToAReplyInTheSameThread() = runTest {
        commentsFromAppView = bobsThread(2)
        val r = repos()
        detail(r) {
            val loaded = awaitUntil { it.loaded()?.comments?.items?.size == 1 }.loaded()!!
            val carols = loaded.comments.items.single().replies.last()
            loaded.eventSink(ActivityDetailEvent.ReplyTo(carols.uri))
            val replying = awaitUntil { it.loaded()?.replyingTo == carols }.loaded()!!
            replying.draftComment.setTextAndPlaceCursorAtEnd("Me too")
            replying.eventSink(ActivityDetailEvent.SendComment)
            r.db.outbox().count(alice.value).first { it > 0 }
            assertEquals("3kroot" to "3kr01", r.queuedReply())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun cancellingAReplyKeepsTheText() = runTest {
        commentsFromAppView = bobsThread(0)
        val r = repos()
        detail(r) {
            val loaded = awaitUntil { it.loaded()?.comments?.items?.size == 1 }.loaded()!!
            loaded.eventSink(ActivityDetailEvent.ReplyTo(loaded.comments.items.single().uri))
            val replying = awaitUntil { it.loaded()?.replyingTo != null }.loaded()!!
            replying.draftComment.setTextAndPlaceCursorAtEnd("Half a th")
            replying.eventSink(ActivityDetailEvent.CancelReply)
            val cancelled = awaitUntil { it.loaded()?.replyingTo == null }.loaded()!!
            assertEquals("Half a th", cancelled.draftComment.text.toString())
            cancelled.eventSink(ActivityDetailEvent.SendComment)
            r.db.outbox().count(alice.value).first { it > 0 }
            assertEquals(null, r.queuedReply(), "a top-level comment")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aHiddenOrJustSentCommentCantBeRepliedTo() = runTest {
        author = staleAlice
        commentsFromAppView = listOf(comment(bob, "did:plc:bob", "3kc1", hidden = true), comment(carol, "did:plc:carol", "3kc2"))
        val r = repos()
        detail(r) {
            val loaded = awaitUntil { it.loaded()?.comments?.items?.size == 2 }.loaded()!!
            loaded.eventSink(ActivityDetailEvent.ReplyTo(loaded.comments.roots.first().uri))
            loaded.draftComment.setTextAndPlaceCursorAtEnd("Mine")
            loaded.eventSink(ActivityDetailEvent.SendComment)
            val sent = awaitUntil { it.loaded()?.sentComments?.size == 1 }.loaded()!!
            assertEquals(null, sent.replyingTo, "not the hidden one")
            sent.eventSink(ActivityDetailEvent.ReplyTo(sent.sentComments.single().uri))
            sent.eventSink(ActivityDetailEvent.ReplyTo(AtUri(commentUri("did:plc:carol", "3kc2"))))
            // Only carol's listed comment took: the sent one was ignored.
            assertEquals("3kc2", awaitUntil { it.loaded()?.replyingTo != null }.loaded()!!.replyingTo!!.uri.rkey)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRefusedReplyKeepsItsTextAndWhatItAnswers() = runTest {
        // A Followers comment on an activity since made Public: the reply is for Followers.
        commentsFromAppView = listOf(comment(bob, "did:plc:bob", "3kc1", audience = "followers"))
        val r = repos(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}&collection=${Nsid.FEED_LIKE}")
        detail(r) {
            val loaded = awaitUntil { it.loaded()?.comments?.items?.size == 1 }.loaded()!!
            loaded.eventSink(ActivityDetailEvent.ReplyTo(loaded.comments.items.single().uri))
            val replying = awaitUntil { it.loaded()?.replyingTo != null }.loaded()!!
            replying.draftComment.setTextAndPlaceCursorAtEnd("Agreed")
            replying.eventSink(ActivityDetailEvent.SendComment)
            // Not counted once the refusal settles.
            val refused = awaitUntil { it.loaded()?.commentError != null && it.loaded()?.activity?.commentCount == 1L }.loaded()!!
            assertEquals(UiError(UiError.Kind.SignInAgain), refused.commentError)
            assertEquals("Agreed", refused.draftComment.text.toString())
            assertEquals("3kc1", refused.replyingTo?.uri?.rkey, "still a reply to bob's comment")
            assertEquals(emptyList(), r.queuedComments())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aThreadComesWithItsFirstThreeReplies() = runTest {
        val two = commentUri("did:plc:carol", "3kc2")
        commentsFromAppView =
            bobsThread(5) + comment(carol, "did:plc:carol", "3kc2") + (0 until 2).map { comment(bob, "did:plc:bob", "3kt$it", root = two) }
        val r = repos()
        detail(r) {
            val loaded = awaitUntil { it.loaded()?.comments?.items?.size == 2 && it.loaded()?.comments?.isLoading == false }.loaded()!!
            val (five, twoReplies) = loaded.comments.items
            assertEquals(listOf("3kr00", "3kr01", "3kr02"), five.replies.map { it.uri.rkey })
            assertEquals(2, five.moreReplies, "a control to view 2 more")
            assertEquals(listOf("3kt0", "3kt1"), twoReplies.replies.map { it.uri.rkey })
            assertEquals(0, twoReplies.moreReplies, "no control")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aThreadWhoseTopLevelCommentIsntListedIsAPlaceholder() = runTest {
        // Bob's comment isn't listed; carol's reply to it is.
        commentsFromAppView = listOf(comment(carol, "did:plc:carol", "3kr00", root = commentUri("did:plc:bob", "3kroot")))
        val r = repos()
        detail(r) {
            val thread = awaitUntil { it.loaded()?.comments?.items?.size == 1 }.loaded()!!.comments.items.single()
            assertEquals(null, thread.root)
            assertEquals(commentUri("did:plc:bob", "3kroot"), thread.uri.value)
            assertEquals(listOf("3kr00"), thread.replies.map { it.uri.rkey })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun viewsTheRestOfAThreadsRepliesOnceForADoubleTap() = runTest {
        commentsFromAppView = bobsThread(5)
        val r = repos()
        detail(r) {
            val loaded = awaitUntil { it.loaded()?.comments?.items?.size == 1 }.loaded()!!
            val thread = loaded.comments.items.single().uri
            repliesGate = CompletableDeferred()
            loaded.eventSink(ActivityDetailEvent.LoadMoreReplies(thread))
            awaitUntil { it.loaded()?.comments?.items?.single()?.repliesState == RepliesState.Loading }
            loaded.eventSink(ActivityDetailEvent.LoadMoreReplies(thread))
            repliesGate!!.complete(Unit)
            val all = awaitUntil { it.loaded()?.comments?.items?.single()?.replies?.size == 5 }.loaded()!!.comments.items.single()
            assertEquals((0 until 5).map { "3kr0$it" }, all.replies.map { it.uri.rkey }, "the 4th and 5th after the 3rd")
            assertEquals(0, all.moreReplies, "the control goes away")
            assertEquals(RepliesState.Idle, all.repliesState)
            assertEquals(listOf<Pair<String?, String?>>("3kroot" to "3"), replyRequests, "asked once")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun viewsManyRepliesAPageAtATime() = runTest {
        commentsFromAppView = bobsThread(80)
        val r = repos()
        detail(r) {
            var state = awaitUntil { it.loaded()?.comments?.items?.size == 1 }.loaded()!!
            assertEquals(77, state.comments.items.single().moreReplies)
            while (state.comments.items.single().moreReplies > 0) {
                val shown = state.comments.items.single().replies.size
                state.eventSink(ActivityDetailEvent.LoadMoreReplies(state.comments.items.single().uri))
                state = awaitUntil { (it.loaded()?.comments?.items?.single()?.replies?.size ?: 0) > shown }.loaded()!!
            }
            assertEquals(
                (0 until 80).map { "3kr" + it.toString().padStart(2, '0') },
                state.comments.items.single().replies.map { it.uri.rkey },
            )
            assertEquals(listOf("3", "33", "63"), replyRequests.map { it.second })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun viewingMoreRepliesThatFailsKeepsThemAndCanBeTriedAgain() = runTest {
        commentsFromAppView = bobsThread(5)
        val r = repos()
        detail(r) {
            val loaded = awaitUntil { it.loaded()?.comments?.items?.size == 1 }.loaded()!!
            val thread = loaded.comments.items.single().uri
            failReplies = true
            loaded.eventSink(ActivityDetailEvent.LoadMoreReplies(thread))
            val failed = awaitUntil { it.loaded()?.comments?.items?.single()?.repliesState is RepliesState.Failed }.loaded()!!
            val shown = failed.comments.items.single()
            assertEquals(UiError.Kind.LoadReplies, (shown.repliesState as RepliesState.Failed).error.kind)
            assertEquals(3, shown.replies.size, "the replies shown stay")
            assertEquals(2, shown.moreReplies)

            failReplies = false
            failed.eventSink(ActivityDetailEvent.LoadMoreReplies(thread))
            val all = awaitUntil { it.loaded()?.comments?.items?.single()?.replies?.size == 5 }.loaded()!!.comments.items.single()
            assertEquals(RepliesState.Idle, all.repliesState)
            assertEquals(listOf("3", "3"), replyRequests.map { it.second }, "tried the same page again")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSentReplyShowsAtTheEndOfItsThread() = runTest {
        commentsFromAppView = bobsThread(5)
        val r = repos()
        detail(r) {
            val loaded = awaitUntil { it.loaded()?.comments?.items?.size == 1 }.loaded()!!
            loaded.eventSink(ActivityDetailEvent.ReplyTo(loaded.comments.items.single().uri))
            val replying = awaitUntil { it.loaded()?.replyingTo != null }.loaded()!!
            replying.draftComment.setTextAndPlaceCursorAtEnd("Agreed")
            replying.eventSink(ActivityDetailEvent.SendComment)
            val sent =
                awaitUntil { it.loaded()?.comments?.items?.single()?.sentReplies?.size == 1 && it.loaded()?.activity?.commentCount == 7L }
                    .loaded()!!
            val thread = sent.comments.items.single()
            assertEquals(3, thread.replies.size, "after the three shown")
            assertEquals(listOf("Agreed"), thread.sentReplies.map { it.text })
            assertEquals(2, thread.moreReplies, "the control still offers the 2 more")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSentReplyOnALaterPageShowsOnceItsThreadIsLoaded() = runTest {
        commentsFromAppView = manyComments(35)
        val r = repos()
        detail(r) {
            val first = awaitUntil { it.loaded()?.comments?.items?.size == 30 && it.loaded()?.comments?.isLoading == false }.loaded()!!
            first.eventSink(ActivityDetailEvent.LoadMoreComments)
            val all = awaitUntil { it.loaded()?.comments?.items?.size == 35 }.loaded()!!
            all.eventSink(ActivityDetailEvent.ReplyTo(all.comments.items[32].uri))
            val replying = awaitUntil { it.loaded()?.replyingTo != null }.loaded()!!
            replying.draftComment.setTextAndPlaceCursorAtEnd("Agreed")
            replying.eventSink(ActivityDetailEvent.SendComment)
            val sent = awaitUntil { it.loaded()?.comments?.items?.get(32)?.sentReplies?.size == 1 }.loaded()!!

            // Reloaded before the AppView indexes it: counted, but shown only once its thread's page is loaded.
            sent.eventSink(ActivityDetailEvent.Retry)
            val reloaded = awaitUntil { it.loaded()?.comments?.items?.size == 30 && it.loaded()?.comments?.isLoading == false }.loaded()!!
            assertEquals(36, reloaded.activity.commentCount)
            assertTrue(reloaded.comments.items.all { it.sentReplies.isEmpty() } && reloaded.sentComments.isEmpty())
            reloaded.eventSink(ActivityDetailEvent.LoadMoreComments)
            val end = awaitUntil { it.loaded()?.comments?.items?.size == 35 }.loaded()!!
            assertEquals(listOf("Agreed"), end.comments.items[32].sentReplies.map { it.text })
            assertEquals(36, end.activity.commentCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deletingOrHidingTheCommentRepliedToStopsTheReply() = runTest {
        author = staleAlice
        commentsFromAppView = listOf(comment(staleAlice, alice.value, "3kmine"), comment(bob, "did:plc:bob", "3kbobs"))
        val r = repos()
        detail(r) {
            val loaded = awaitUntil { it.loaded()?.comments?.items?.size == 2 }.loaded()!!
            val (mine, bobs) = loaded.comments.roots
            loaded.eventSink(ActivityDetailEvent.ReplyTo(mine.uri))
            val replying = awaitUntil { it.loaded()?.replyingTo == mine }.loaded()!!
            replying.draftComment.setTextAndPlaceCursorAtEnd("Kept")
            replying.eventSink(ActivityDetailEvent.DeleteComment(mine.uri))
            val deleted = awaitUntil { it.loaded()?.replyingTo == null }.loaded()!!
            assertEquals("Kept", deleted.draftComment.text.toString(), "back to a top-level comment, keeping the text")

            deleted.eventSink(ActivityDetailEvent.ReplyTo(bobs.uri))
            val replyingToBob = awaitUntil { it.loaded()?.replyingTo == bobs }.loaded()!!
            replyingToBob.eventSink(ActivityDetailEvent.HideComment(bobs.uri))
            assertEquals(null, awaitUntil { it.loaded()?.replyingTo == null }.loaded()!!.replyingTo, "a hidden comment can't be answered")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun extraRepliesStayThroughAReloadUntilItsThreadsAnswer() = runTest {
        commentsFromAppView = bobsThread(5)
        val r = repos()
        detail(r) {
            val loaded = awaitUntil { it.loaded()?.comments?.items?.size == 1 }.loaded()!!
            loaded.eventSink(ActivityDetailEvent.LoadMoreReplies(loaded.comments.items.single().uri))
            val all = awaitUntil { it.loaded()?.comments?.items?.single()?.replies?.size == 5 }.loaded()!!
            val gate = CompletableDeferred<Unit>().also { commentsGate = it }
            all.eventSink(ActivityDetailEvent.Retry)
            val reloading = awaitUntil { it.loaded()?.comments?.refresh is LoadStatus.Loading }.loaded()!!
            assertEquals(5, reloading.comments.items.single().replies.size, "the extra replies stay while the reload runs")

            gate.complete(Unit)
            val reloaded = awaitUntil {
                it.loaded()?.comments?.let { c -> c.refresh is LoadStatus.Idle && c.items.single().replies.size == 3 } == true
            }
            val thread = reloaded.loaded()!!.comments.items.single()
            assertEquals(2, thread.moreReplies, "then replaced by the thread as served again")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun comingBackShowsTheExtraRepliesAtOnce() = runTest {
        commentsFromAppView = bobsThread(5)
        val r = repos()
        val registry = RetainedStateRegistry()
        var shown by mutableStateOf(true)
        val presenter = presenterOf(r)
        presenterTestOf({
            withCompositionLocals(LocalRetainedStateRegistry provides registry) {
                if (shown) presenter() else ActivityDetailState(eventSink = {})
            }
        }) {
            val loaded = awaitUntil { it.loaded()?.comments?.items?.size == 1 }.loaded()!!
            loaded.eventSink(ActivityDetailEvent.LoadMoreReplies(loaded.comments.items.single().uri))
            awaitUntil { it.loaded()?.comments?.items?.single()?.replies?.size == 5 }
            registry.saveAll()
            shown = false
            awaitUntil { it.page() == null }
            shown = true
            val back = assertNotNull(awaitItem().loaded(), "no loading state in between")
            assertEquals(5, back.comments.items.single().replies.size)
            assertEquals(listOf<String?>("3"), replyRequests.map { it.second }, "the replies aren't asked for again")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
