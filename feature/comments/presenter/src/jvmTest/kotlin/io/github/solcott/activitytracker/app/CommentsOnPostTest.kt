package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import io.github.solcott.activitytracker.app.comments.CommentsScreen
import io.github.solcott.activitytracker.app.interactions.CommentAction
import io.github.solcott.activitytracker.app.interactions.CommentEvent
import io.github.solcott.activitytracker.app.interactions.CommentsTexts
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.uistate.LoadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** A post's Comments screen (feed/post-page): commenting for the post's audience, replying, the author's hides, and Report. */
class CommentsOnPostTest : CommentsTestBase() {
    private val postUri = "at://did:plc:bob/${Nsid.FEED_POST}/3kpost"
    private val onPost = CommentsScreen(postUri)

    private fun post(visibility: String = "followers", author: String = bob) =
        """{"${'$'}type":"${Nsid.FEED_DEFS}#postView","uri":"$postUri","cid":"bafypost","author":$author,"text":"Why",""" +
            """"visibility":"$visibility","embed":{"${'$'}type":"${Nsid.FEED_DEFS}#postProposalEmbedView",""" +
            """"proposal":{"uri":"at://did:plc:bob/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/3kpost","cid":"bafyq"},"name":"Wiggly Woods",""" +
            """"status":"open","openedAt":"2026-11-05T10:00:00Z","up":1,"down":0,""" +
            """"segment":{"uri":"at://did:plc:importer/${Nsid.FEED_SEGMENT}/osm-1","name":"Trail 214",""" +
            """"sport":"ride","distanceMeters":1200}},""" +
            """"createdAt":"2026-11-05T10:00:00Z","indexedAt":"2026-11-05T10:00:00Z",""" +
            """"likeCount":0,"commentCount":${commentsFromAppView.size},"viewer":{}}"""

    private suspend fun Repos.queued(collection: String) =
        db.outbox()
            .pending(alice.value)
            .filter { it.collection == collection }
            .map { it.space?.substringAfterLast('/') to it.recordJson.orEmpty() }

    @Test
    fun aCommentIsWrittenForThePostsAudienceAndCountedAtOnce() = runTest {
        postFromAppView = post(visibility = "followers")
        val r = repos()
        presenterTestOf(presenterOf(r, onPost)) {
            val state = awaitUntil { it.page()?.comments?.refresh is LoadStatus.Idle }
            assertTrue(state.onPost)
            val page = state.page()!!
            page.draftComment.setTextAndPlaceCursorAtEnd("Agreed")
            page.eventSink(CommentEvent.Send)
            page.eventSink(CommentEvent.Send)
            val sent = awaitUntil { it.page()?.sentComments?.isNotEmpty() == true }.page()!!
            assertEquals(1, sent.count)
            val queued = r.queued(Nsid.FEED_COMMENT).single()
            assertEquals("interactions", queued.first)
            assertTrue("\"audience\":\"followers\"" in queued.second, queued.second)
            assertTrue(postUri in queued.second)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aReplyAnswersTheListedComment() = runTest {
        commentsFromAppView = listOf(comment(carol, "did:plc:carol", "3kc1"))
        postFromAppView = post(visibility = "followers")
        val r = repos()
        presenterTestOf(presenterOf(r, onPost)) {
            val page = awaitUntil { it.page()?.comments?.items?.isNotEmpty() == true }.page()!!
            page.eventSink(CommentEvent.ReplyTo(page.comments.items.single().uri))
            val replying = awaitUntil { it.page()?.replyingTo != null }.page()!!
            replying.draftComment.setTextAndPlaceCursorAtEnd("Me too")
            replying.eventSink(CommentEvent.Send)
            awaitUntil { it.page()?.comments?.items?.single()?.sentReplies?.isNotEmpty() == true }
            val queued = r.queued(Nsid.FEED_COMMENT).single()
            assertEquals("interactions", queued.first)
            assertTrue("\"reply\"" in queued.second && commentUri("did:plc:carol", "3kc1") in queued.second, queued.second)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theAuthorHidesACommentOnTheirPost() = runTest {
        commentsFromAppView = listOf(comment(carol, "did:plc:carol", "3kc1"))
        postFromAppView = post(visibility = "public", author = staleAlice)
        val r = repos()
        presenterTestOf(presenterOf(r, onPost)) {
            val page = awaitUntil { it.page()?.comments?.items?.isNotEmpty() == true }.page()!!
            assertTrue(page.isOwn)
            page.eventSink(CommentEvent.Hide(page.comments.items.single().uri))
            val hidden = awaitUntil { it.page()?.comments?.items?.single()?.root?.hidden == true && it.page()?.count == 0L }.page()!!
            assertEquals(0, hidden.count)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aCommentsReportOpensTheSheetForTheComment() = runTest {
        commentsFromAppView = listOf(comment(carol, "did:plc:carol", "3kc1"))
        postFromAppView = post()
        val r = repos()
        presenterTestOf(presenterOf(r, onPost)) {
            val state = awaitUntil { it.page()?.comments?.items?.isNotEmpty() == true }
            val list = state.page!!.comments
            val row = CommentsTexts.rows(list, state.reporting.available).single()
            val report = row.menu.single { it.action == CommentAction.Report }
            assertEquals(null, report.event(), "Report opens the sheet rather than sending a comment event")
            state.reporting.eventSink(state.reporting.reportComment(row.comment!!, "@carol.test"))
            val sheet = awaitUntil { it.reporting.sheet != null }.reporting.sheet!!
            val comment = list.paging.items.single().root!!
            assertEquals(ReportTarget.Comment(comment.uri, comment.cid, Did("did:plc:carol")), sheet.target)
            assertEquals("@carol.test", sheet.authorName)
            assertTrue(sheet.offerBlock)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theScreenSaysWhetherTheFieldTakesFocus() = runTest {
        postFromAppView = post()
        val r = repos()
        presenterTestOf(presenterOf(r, CommentsScreen(postUri, focus = true))) {
            assertTrue(awaitUntil { it.page() != null }.focus)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
