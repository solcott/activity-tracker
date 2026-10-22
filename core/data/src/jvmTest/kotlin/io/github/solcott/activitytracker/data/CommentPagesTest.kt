package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.FakeAthletePds.Session
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ActivityViewerState
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.ThreadView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.Page
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * The viewer's own comment changes on pages beyond the first (`feed/comments`, "Own changes hold on every page" and "A sent comment shows
 * at once"): the count is moved once, whether or not the AppView has caught up, without loading every page.
 */
class CommentPagesTest {
    private val pds = FakeAthletePds()
    private val did = pds.did
    private val bob = Did("did:plc:bob")
    private var served by pds::served

    private fun view(author: Did, rkey: String, comments: Long) =
        ActivityView(
            uri = AtUri.of(author, Nsid.FEED_ACTIVITY, rkey),
            cid = Cid("bafyrec"),
            author = ProfileViewBasic(author, Handle("${author.value.substringAfterLast(':')}.test")),
            record =
                ActivityRecord(
                    sport = Sport.RUN,
                    startedAt = Instant.parse("2026-10-14T07:00:00Z"),
                    elapsedTime = 1800,
                    createdAt = Instant.parse("2026-10-14T07:31:00Z"),
                ),
            likeCount = 0,
            commentCount = comments,
            indexedAt = Instant.parse("2026-10-14T07:32:00Z"),
            visibility = Visibility.PUBLIC,
            viewer = ActivityViewerState(),
        )

    private fun commentOn(author: Did, rkey: String) =
        CommentView(
            AtUri.of(author, Nsid.FEED_COMMENT, rkey),
            Cid("bafy$rkey"),
            ProfileViewBasic(author, Handle("${author.value.substringAfterLast(':')}.test")),
            "Nice",
            Instant.parse("2026-10-14T08:00:00Z"),
        )

    /** [count] of bob's comments, `3kc000`…, with [own] (if any) in place of the one at [ownAt]. */
    private fun comments(count: Int, own: CommentView? = null, ownAt: Int = -1) =
        (0 until count).map { i -> if (i == ownAt && own != null) own else commentOn(bob, "3kc" + i.toString().padStart(3, '0')) }

    /** Every page of [activity]'s comments, as the viewer sees them. */
    private suspend fun Session.allPages(activity: ActivityView): List<List<CommentView>> {
        val pages = mutableListOf<List<CommentView>>()
        var cursor: String? = null
        do {
            val page = feed.comments(activity.uri, cursor)
            pages += feed.localComments.applyTo(page.comments)
            cursor = page.cursor
        } while (cursor != null)
        return pages
    }

    @Test
    fun aDeleteOnTheThirdPageStaysOutOfTheCountAndThePageAfterAReload() = runTest {
        val s = session()
        val mine = commentOn(did, "3kmine")
        val activity = s.cache(view(bob, "3kbob", comments = 70))
        served = GetActivity.Output(activity, comments(70, mine, ownAt = 65))
        assertTrue(mine in s.allPages(activity)[2], "on the third page")

        s.activities.deleteComment(activity, mine.uri)
        // The screen reloads before the AppView has indexed the delete.
        assertEquals(69, s.feed.activity(activity.uri).commentCount)
        assertEquals(listOf(listOf(mine.uri.value)), pds.lookups, "looked up without loading the third page")
        assertTrue(s.allPages(activity).flatten().none { it.uri == mine.uri })
    }

    @Test
    fun aDeleteTheAppViewHasCaughtUpWithIsntTakenOffAgain() = runTest {
        val s = session()
        val mine = commentOn(did, "3kmine")
        val activity = s.cache(view(bob, "3kbob", comments = 70))
        served = GetActivity.Output(activity, comments(70, mine, ownAt = 65))
        s.allPages(activity)
        s.activities.deleteComment(activity, mine.uri)

        served = GetActivity.Output(activity.copy(commentCount = 69), comments(70).filterNot { it.uri.rkey == "3kc065" })
        assertEquals(69, s.feed.activity(activity.uri).commentCount, "the AppView's count, not one lower again")
        assertEquals(69, s.feed.activity(activity.uri).commentCount)
        assertEquals(1, pds.lookups.size, "settled, so not looked up again")
    }

    @Test
    fun aHideOnTheSecondPageHoldsAfterAReload() = runTest {
        val s = session()
        val own = s.cache(view(did, "3kown", comments = 40))
        val list = comments(40)
        served = GetActivity.Output(own, list)
        val target = s.allPages(own)[1][5]

        s.activities.setCommentHidden(own, target, hidden = true)
        assertEquals(39, s.feed.activity(own.uri).commentCount)
        val second = s.allPages(own)[1]
        assertEquals(true, second.single { it.uri == target.uri }.hidden, "marked when the second page is listed")

        served = GetActivity.Output(own.copy(commentCount = 39), list.map { if (it.uri == target.uri) it.copy(hidden = true) else it })
        assertEquals(39, s.feed.activity(own.uri).commentCount)
    }

    @Test
    fun aSentCommentIsCountedOnceWhetherOrNotTheAppViewHasIndexedIt() = runTest {
        val s = session()
        val activity = s.cache(view(bob, "3kbob", comments = 40))
        served = GetActivity.Output(activity, comments(40))
        val uri = s.activities.comment(activity, "  Mine  ")

        val sent = s.feed.localComments.sent(activity.uri).single()
        assertEquals(uri, sent.uri)
        assertEquals("Mine", sent.text)
        assertEquals(did, sent.author.did)
        assertEquals(41, s.feed.activity(activity.uri).commentCount, "refreshed before the AppView indexes it")
        assertEquals(41, s.cached(activity).commentCount)

        // Indexed: the AppView counts it and lists it last, on a page the viewer hasn't loaded.
        val indexed = commentOn(did, uri.rkey!!)
        served = GetActivity.Output(activity.copy(commentCount = 41), comments(40) + indexed)
        assertEquals(41, s.feed.activity(activity.uri).commentCount, "send then refresh counts it once")
        assertEquals(emptyList(), s.feed.localComments.sent(activity.uri))
        assertEquals(listOf(indexed), s.allPages(activity).last().takeLast(1))
    }

    /** [author]'s reply [rkey] in [root]'s thread. */
    private fun replyOn(author: Did, rkey: String, root: CommentView) = commentOn(author, rkey).copy(root = root.ref, parent = root.ref)

    /** Every page of [activity]'s threads, as the viewer sees them. */
    private suspend fun Session.allThreads(activity: ActivityView): List<ThreadView> {
        val threads = mutableListOf<ThreadView>()
        var cursor: String? = null
        do {
            val page = feed.comments(activity.uri, cursor)
            threads += page.threads.orEmpty().map(feed.localComments::applyTo)
            cursor = page.cursor
        } while (cursor != null)
        return threads
    }

    @Test
    fun aDeletedReplyOnTheSecondPageLeavesItsThreadAndTheCount() = runTest {
        val s = session()
        val top = comments(40)
        val mine = replyOn(did, "3kmine", top[35])
        val activity = s.cache(view(bob, "3kbob", comments = 41))
        served = GetActivity.Output(activity, top + mine)
        assertEquals(listOf(mine), s.allThreads(activity)[35].replies, "listed in bob's thread, on the second page")

        s.activities.deleteComment(activity, mine.uri)
        assertEquals(40, s.feed.activity(activity.uri).commentCount)
        assertEquals(listOf(listOf(mine.uri.value)), pds.lookups)
        val thread = s.allThreads(activity)[35]
        assertEquals(emptyList(), thread.replies)
        assertEquals(0, thread.replyCount)
    }

    @Test
    fun aHiddenReplyIsMarkedAndUncounted() = runTest {
        val s = session()
        val top = comments(3)
        val carols = replyOn(Did("did:plc:carol"), "3kreply", top[1])
        val own = s.cache(view(did, "3kown", comments = 4))
        served = GetActivity.Output(own, top + carols)
        s.allThreads(own)

        s.activities.setCommentHidden(own, carols, hidden = true)
        assertEquals(3, s.feed.activity(own.uri).commentCount)
        assertEquals(true, s.allThreads(own)[1].replies.single().hidden)
    }

    @Test
    fun aSentReplyIsCountedOnceAndSettlesThroughALookup() = runTest {
        val s = session()
        val top = comments(40)
        val activity = s.cache(view(bob, "3kbob", comments = 40))
        served = GetActivity.Output(activity, top)
        val uri = s.activities.comment(activity, "Agreed", replyTo = top[35])

        assertEquals(top[35].uri, s.feed.localComments.sent(activity.uri).single().root)
        assertEquals(41, s.feed.activity(activity.uri).commentCount, "counted before the AppView indexes it")

        // Indexed in bob's thread, on a page the viewer hasn't loaded: the lookup settles it.
        val indexed = replyOn(did, uri.rkey!!, top[35])
        served = GetActivity.Output(activity.copy(commentCount = 41), top + indexed)
        assertEquals(41, s.feed.activity(activity.uri).commentCount, "counted once")
        assertEquals(emptyList(), s.feed.localComments.sent(activity.uri))
        assertEquals(listOf(indexed), s.allThreads(activity)[35].replies)
    }

    @Test
    fun theRestOfAThreadsRepliesComeAPageAtATime() = runTest {
        val s = session()
        val root = commentOn(bob, "3kroot")
        val replies = (0 until 8).map { replyOn(Did("did:plc:carol"), "3kr$it", root) }
        val activity = s.cache(view(bob, "3kbob", comments = 9))
        served = GetActivity.Output(activity, listOf(root) + replies)
        val thread = s.allThreads(activity).single()
        assertEquals(replies.take(3), thread.replies)
        val rest = s.feed.comments(activity.uri, thread.repliesCursor, root = root.uri)
        assertEquals(replies.drop(3), rest.comments)
    }

    @Test
    fun commentAndReplyPagesAreOutcomes() = runTest {
        val s = session()
        val root = commentOn(bob, "3kroot")
        val replies = (0 until 8).map { replyOn(Did("did:plc:carol"), "3kr$it", root) }
        val activity = s.cache(view(bob, "3kbob", comments = 9))
        served = GetActivity.Output(activity, listOf(root) + replies)
        val threads = assertIs<Outcome.Data<Page<ThreadView, Unit>>>(s.feed.commentsPage(activity.uri, null)).data
        val thread = threads.items.single()
        assertEquals(replies.take(3), thread.replies)
        assertEquals(null, threads.next)
        val rest = assertIs<Outcome.Data<Page<CommentView, Unit>>>(s.feed.repliesPage(activity.uri, root.uri, thread.repliesCursor)).data
        assertEquals(replies.drop(3), rest.items)

        pds.appViewOffline = true
        assertEquals(DataError.Network, assertIs<Outcome.Error>(s.feed.commentsPage(activity.uri, null)).cause)
        assertEquals(DataError.Network, assertIs<Outcome.Error>(s.feed.repliesPage(activity.uri, root.uri, thread.repliesCursor)).cause)
    }

    @Test
    fun anActivityWithNoChangesIsntLookedUp() = runTest {
        val s = session()
        val activity = s.cache(view(bob, "3kbob", comments = 2))
        served = GetActivity.Output(activity, comments(2))
        assertEquals(2, s.feed.activity(activity.uri).commentCount)
        assertEquals(emptyList(), pds.lookups)
    }

    private suspend fun session() = pds.session()
}
