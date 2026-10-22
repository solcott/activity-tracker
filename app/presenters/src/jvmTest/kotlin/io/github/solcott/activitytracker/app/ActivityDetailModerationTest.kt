package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.media.MediaGalleryEvent
import io.github.solcott.activitytracker.app.media.MediaGalleryPresenter
import io.github.solcott.activitytracker.app.media.MediaGalleryScreen
import io.github.solcott.activitytracker.app.moderation.Revealed
import io.github.solcott.activitytracker.app.report.BlockOffer
import io.github.solcott.activitytracker.app.report.ReportOuterEvent
import io.github.solcott.activitytracker.app.report.ReportingEvent
import io.github.solcott.activitytracker.data.moderation.BlurReason
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Moderation on the detail page and in the gallery: what labels blur or hide, and revealing it for this view only (`moderation/labels`);
 * reporting a comment and the block it offers (`moderation/reports`).
 */
class ActivityDetailModerationTest : ActivityDetailTestBase() {
    private fun labels(vararg values: String) =
        values.joinToString(",", "\"labels\":[", "],") { """{"src":"did:plc:labeler","val":"$it"}""" }

    private val photo =
        "\"media\":[{\"\$type\":\"app.activitytracker.feed.defs#imageView\",\"fullsize\":\"http://appview.test/img/media/did:plc:bob/bafkphoto\"}],"

    @Test
    fun aHiddenActivityOpensBehindAWarningUntilRevealed() = runTest {
        viewExtra = labels("!hide")
        val r = repos()
        detail(r) {
            val loaded = awaitLoaded()
            assertEquals(ModerationDecision.Hidden, ModerationDecision.of(loaded.activity.labels, loaded.activity.author.labels, false))
            val key = Revealed.hiddenKey(activityUri)
            assertFalse(key in loaded.revealed)
            loaded.revealed.eventSink(key)
            val revealed = awaitUntil { it.page()?.revealed?.contains(key) == true }.page()!!
            assertFalse(activityUri in revealed.revealed, "its photos stay blurred until revealed on their own")
            cancelAndIgnoreRemainingEvents()
        }
        // A new view of the screen starts covered again.
        detail(r) {
            assertFalse(Revealed.hiddenKey(activityUri) in awaitLoaded().revealed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aBlurredPhotoRevealsBeforeItZooms() = runTest {
        viewExtra = photo + labels("nudity")
        val r = repos()
        val screen = MediaGalleryScreen(activityUri, 0)
        presenterTestOf({ MediaGalleryPresenter(screen, FakeNavigator(screen), r.feed, r.profiles, r.blocks, r.reports) }) {
            var state = awaitUntil { it.items.isNotEmpty() }
            assertEquals(BlurReason.Nudity, state.blur)
            assertFalse(activityUri in state.revealed)
            state.eventSink(MediaGalleryEvent.ToggleZoom)
            state = awaitUntil { activityUri in it.revealed }
            assertFalse(state.zoomed, "the first tap reveals")
            state.eventSink(MediaGalleryEvent.ToggleZoom)
            assertTrue(awaitUntil { it.zoomed }.zoomed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theAthletesOwnTakenDownActivityShowsPlaceholders() = runTest {
        author = staleAlice
        viewExtra = photo + labels("!takedown")
        val r = repos()
        val screen = MediaGalleryScreen(activityUri, 0)
        presenterTestOf({ MediaGalleryPresenter(screen, FakeNavigator(screen), r.feed, r.profiles, r.blocks, r.reports) }) {
            val state = awaitUntil { it.items.isNotEmpty() }
            assertTrue(state.takenDown)
            assertFalse(state.canReport, "nor can they report their own")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reportingACommentOffersBlockThroughTheCommentMenusBlock() = runTest {
        commentsFromAppView = listOf(comment(bob, "did:plc:bob", "3kc1"), comment(carol, "did:plc:carol", "3kc2"))
        val r = repos()
        detail(r) {
            val loaded = awaitUntil { it.page()?.comments?.roots?.size == 2 }.page()!!
            assertTrue(loaded.reporting.available, "a labeler is configured")
            val bobs = loaded.comments.roots.first()
            val target = ReportTarget.Comment(bobs.uri, bobs.cid, bobs.author.did)
            loaded.reporting.eventSink(ReportingEvent.Open(target, "bob"))
            val open = awaitUntil { it.page()?.reporting?.sheet != null }.page()!!
            assertEquals(target, open.reporting.sheet?.target)

            // The sheet sent the report and its block offer was taken: the usual confirmation shows first.
            open.reporting.eventSink(ReportingEvent.Sheet(ReportOuterEvent.Block(bobs.author.did)))
            val confirming = awaitUntil { it.page()?.reporting?.confirmBlock != null }.page()!!
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
        detail(r) {
            val loaded = awaitUntil { it.page()?.comments?.roots?.size == 1 }.page()!!
            val bobs = loaded.comments.roots.single()
            loaded.reporting.eventSink(ReportingEvent.Open(ReportTarget.Comment(bobs.uri, bobs.cid, bobs.author.did), "bob"))
            awaitUntil { it.page()?.reporting?.sheet != null }
            loaded.reporting.eventSink(ReportingEvent.Sheet(ReportOuterEvent.Block(bobs.author.did)))
            val confirming = awaitUntil { it.page()?.reporting?.confirmBlock != null }.page()!!
            confirming.reporting.eventSink(ReportingEvent.ConfirmBlock(false))
            val after = awaitUntil { it.page()?.reporting?.confirmBlock == null }.page()!!
            assertEquals(1, after.comments.roots.size)
            assertTrue(r.db.outbox().pending(alice.value).none { it.collection == Nsid.GRAPH_BLOCK })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
