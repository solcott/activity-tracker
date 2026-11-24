package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.comments.CommentsScreen
import io.github.solcott.activitytracker.app.interactions.CommentsPreviewEvent
import io.github.solcott.activitytracker.data.feed.asSubject
import io.github.solcott.activitytracker.lexicon.AtUri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** The activity page's comments block (feed/comments): its count and first comments, the Comments screen it opens, and coming back. */
class ActivityDetailCommentsBlockTest : ActivityDetailTestBase() {
    @Test
    fun showsTheCountAndTheFirstTwoTopLevelComments() = runTest {
        commentsFromAppView =
            listOf(comment(bob, "did:plc:bob", "3kc1"), comment(carol, "did:plc:carol", "3kc2"), comment(carol, "did:plc:carol", "3kc3"))
        detail(repos()) {
            val block = awaitUntil { it.page()?.comments?.rows?.size == 2 }.page()!!.comments
            assertEquals(3, block.count)
            assertEquals(listOf(commentUri("did:plc:bob", "3kc1"), commentUri("did:plc:carol", "3kc2")), block.rows.map { it.key })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theBlockOpensTheCommentsScreenFocusedToAddOne() = runTest {
        val navigator = FakeNavigator(screen)
        presenterTestOf(presenterOf(repos(), navigator = navigator)) {
            val block = awaitLoaded().comments
            block.eventSink(CommentsPreviewEvent.OpenAll)
            assertEquals(CommentsScreen(activityUri), navigator.awaitNextScreen())
            block.eventSink(CommentsPreviewEvent.Compose)
            assertEquals(CommentsScreen(activityUri, focus = true), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aCommentSentOnTheCommentsScreenShowsOnReturnBeforeTheAppViewHasIt() = runTest {
        val r = repos()
        detail(r) {
            val loaded = awaitUntil { it.page()?.comments?.rows?.isEmpty() == true && it.page()?.activity?.commentCount == 0L }
            // What the Comments screen's Send does: the comment, and the move of the count it records.
            r.activities.comment(loaded.page()!!.activity.asSubject(), "Nice ride", null)
            r.feed.commentMoves.move(AtUri(activityUri), 1)
            val back = awaitUntil { it.page()?.let { p -> p.comments.count == 1L && p.comments.rows.size == 1 } == true }.page()!!
            assertEquals(1, back.activity.commentCount)
            assertEquals("Nice ride", back.comments.rows.single().text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aCommentActionElsewhereLoadsTheBlocksFirstPageAgain() = runTest {
        val r = repos()
        detail(r) {
            awaitUntil { it.page()?.comments?.rows?.isEmpty() == true }
            // The AppView lists a comment by now, so it's no longer only the viewer's own just-sent one: the first page must say so.
            commentsFromAppView = listOf(comment(carol, "did:plc:carol", "3kc1"))
            r.feed.commentMoves.move(AtUri(activityUri), 1)
            val block = awaitUntil { it.page()?.comments?.rows?.size == 1 }.page()!!.comments
            assertEquals(commentUri("did:plc:carol", "3kc1"), block.rows.single().key)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
