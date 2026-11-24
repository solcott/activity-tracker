package io.github.solcott.activitytracker.app.ui

import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.fakeComment
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.interactions.CommentThread
import io.github.solcott.activitytracker.app.interactions.CommentsPaging
import io.github.solcott.activitytracker.app.interactions.CommentsState
import io.github.solcott.activitytracker.app.interactions.CommentsTexts
import io.github.solcott.activitytracker.data.feed.SentComment
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.uistate.LoadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toImmutableList

/** feed/comments: which rows on screen ask for the next page of threads, by the keys `CommentsTexts.rows` gives them. */
class CommentsLoadMoreTest {
    private val carol = fakeProfileBasic(Fixed.CAROL)

    /** Seven threads, each a top-level comment with one reply. */
    private val threads =
        (1..7).map { n ->
            val root = fakeComment("3kc$n", carol)
            CommentThread(root.uri, root, listOf(fakeComment("3kr$n", carol)).toImmutableList())
        }

    private val sent = SentComment(AtUri.of(Fixed.ALICE, Nsid.FEED_COMMENT, "3ks1"), fakeProfileBasic(Fixed.ALICE), "Also", Fixed.NOW)

    private fun state(canLoadMore: Boolean = true) =
        CommentsState(
            paging =
                CommentsPaging(items = threads.toImmutableList(), refresh = LoadStatus.Idle, hasAnswer = true, canLoadMore = canLoadMore),
            sent = listOf(sent).toImmutableList(),
            draft = textFieldStateOf(),
            replyingTo = null,
            viewerDid = Fixed.ALICE,
            author = fakeProfileBasic(Fixed.BOB),
            isOwn = false,
            count = 15,
        )

    /** The keys of thread [n]'s rows (1 is the oldest listed). */
    private fun threadKeys(n: Int): List<String> {
        val prefix = CommentsTexts.threadKey(threads[n - 1].uri)
        return CommentsTexts.rows(state(), reportAvailable = false).map { it.key }.filter { it.startsWith("$prefix|") }
    }

    @Test
    fun anEarlyThreadShownAsksForNothing() {
        assertEquals(2, threadKeys(2).size)
        assertFalse(CommentsTexts.wantsMore(threadKeys(1) + threadKeys(2), state()))
    }

    @Test
    fun anyRowOfTheLastFiveThreadsAsks() {
        // Threads 3 to 7 are the last five; their replies ask as their top-level comments do.
        for (n in 3..7) threadKeys(n).forEach { assertTrue(CommentsTexts.wantsMore(listOf(it), state()), it) }
    }

    @Test
    fun theRowsAfterTheThreadsAsk() {
        val sentRow = CommentsTexts.rows(state(), reportAvailable = false).last().key
        assertEquals(CommentsTexts.sentKey(sent.uri), sentRow)
        assertTrue(CommentsTexts.wantsMore(listOf(sentRow), state()))
        assertTrue(CommentsTexts.wantsMore(listOf(CommentsTexts.LOADING_KEY), state()))
    }

    @Test
    fun nothingAsksWhileNoPageCanLoad() =
        assertFalse(CommentsTexts.wantsMore(threadKeys(7) + CommentsTexts.LOADING_KEY, state(canLoadMore = false)))

    @Test fun theCommentsHeaderAsksForNothing() = assertFalse(CommentsTexts.wantsMore(listOf("comments"), state()))
}
