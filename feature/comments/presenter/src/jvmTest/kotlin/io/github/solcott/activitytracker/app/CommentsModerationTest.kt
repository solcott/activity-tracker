package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.report.BlockOffer
import io.github.solcott.activitytracker.app.report.ReportOuterEvent
import io.github.solcott.activitytracker.app.report.ReportingEvent
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Reporting a comment on the Comments screen and the block it offers (`moderation/reports`). */
class CommentsModerationTest : CommentsTestBase() {
    @Test
    fun reportingACommentOffersBlockThroughTheCommentMenusBlock() = runTest {
        commentsFromAppView = listOf(comment(bob, "did:plc:bob", "3kc1"), comment(carol, "did:plc:carol", "3kc2"))
        val r = repos()
        commentsOf(r) {
            val state = awaitUntil { it.page()?.comments?.roots?.size == 2 }
            assertTrue(state.reporting.available, "a labeler is configured")
            val bobs = state.page()!!.comments.roots.first()
            val target = ReportTarget.Comment(bobs.uri, bobs.cid, bobs.author.did)
            state.reporting.eventSink(ReportingEvent.Open(target, "bob"))
            val open = awaitUntil { it.reporting.sheet != null }
            assertEquals(target, open.reporting.sheet?.target)

            // The sheet sent the report and its block offer was taken: the usual confirmation shows first.
            open.reporting.eventSink(ReportingEvent.Sheet(ReportOuterEvent.Block(bobs.author.did)))
            val confirming = awaitUntil { it.reporting.confirmBlock != null }
            assertEquals(null, confirming.reporting.sheet)
            assertEquals(BlockOffer(bobs.author.did, "bob"), confirming.reporting.confirmBlock)
            confirming.reporting.eventSink(ReportingEvent.ConfirmBlock(true))
            val blocked = awaitUntil { it.page()?.comments?.roots?.size == 1 }.page()!!
            assertEquals("did:plc:carol", blocked.comments.roots.single().author.did.value, "as the comment menu's Block does")
            // Blocked through BlockRepository, whatever the outbox has sent so far.
            assertEquals(true, r.feed.localBlocks.blocked(Did("did:plc:bob")))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aDeclinedBlockOfferBlocksNoOne() = runTest {
        commentsFromAppView = listOf(comment(bob, "did:plc:bob", "3kc1"))
        val r = repos()
        commentsOf(r) {
            val state = awaitUntil { it.page()?.comments?.roots?.size == 1 }
            val bobs = state.page()!!.comments.roots.single()
            state.reporting.eventSink(ReportingEvent.Open(ReportTarget.Comment(bobs.uri, bobs.cid, bobs.author.did), "bob"))
            awaitUntil { it.reporting.sheet != null }
            state.reporting.eventSink(ReportingEvent.Sheet(ReportOuterEvent.Block(bobs.author.did)))
            val confirming = awaitUntil { it.reporting.confirmBlock != null }
            confirming.reporting.eventSink(ReportingEvent.ConfirmBlock(false))
            val after = awaitUntil { it.reporting.confirmBlock == null }.page()!!
            assertEquals(1, after.comments.roots.size)
            assertTrue(r.db.outbox().pending(alice.value).none { it.collection == Nsid.GRAPH_BLOCK })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
