package io.github.solcott.activitytracker.app.ui

import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.fakeComment
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.interactions.CommentPreviewRow
import io.github.solcott.activitytracker.app.interactions.CommentThread
import io.github.solcott.activitytracker.app.interactions.CommentsPreview
import io.github.solcott.activitytracker.data.feed.SentComment
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.LabelView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** feed/comments: which comments the page's comments block shows, and what it offers. */
class CommentsPreviewTest {
    private val bob = fakeProfileBasic(Fixed.BOB)
    private val carol = fakeProfileBasic(Fixed.CAROL)

    private fun thread(root: CommentView?, uri: AtUri = root!!.uri, vararg replies: CommentView) =
        CommentThread(uri, root, replies.toList().toImmutableList())

    private fun sent(rkey: String, root: AtUri? = null) =
        SentComment(AtUri.of(Fixed.ALICE, Nsid.FEED_COMMENT, rkey), fakeProfileBasic(Fixed.ALICE), "Mine $rkey", Fixed.NOW, root)

    private fun keys(preview: CommentsPreview) = preview.rows.map { it.key }

    @Test
    fun theFirstTwoTopLevelCommentsWithoutReplies() {
        val first = fakeComment("3kc1", bob, "Brutal climb")
        val reply = fakeComment("3kr1", carol, "Agreed")
        val second = fakeComment("3kc2", carol)
        val third = fakeComment("3kc3", bob)
        val preview =
            CommentsPreview.of(14, listOf(thread(first, first.uri, reply), thread(second), thread(third)), emptyList(), Fixed.ALICE)

        assertEquals(listOf(first.uri.value, second.uri.value), keys(preview))
        assertEquals("Brutal climb", preview.rows.first().text)
        assertEquals("Bob", preview.rows.first().author)
    }

    @Test
    fun hiddenLabelledAndPlaceholdersAreLeftOut() {
        val hidden = fakeComment("3kc1", bob).copy(hidden = true)
        val labelled = fakeComment("3kc2", bob).copy(labels = listOf(label("graphic-media")))
        val placeholder = AtUri.of(Fixed.CAROL, Nsid.FEED_COMMENT, "3kgone")
        val shown = fakeComment("3kc3", carol)
        val preview =
            CommentsPreview.of(
                4,
                listOf(thread(hidden), thread(labelled), thread(null, placeholder, fakeComment("3kr", bob)), thread(shown)),
                emptyList(),
                Fixed.ALICE,
            )

        assertEquals(listOf(shown.uri.value), keys(preview))
    }

    @Test
    fun justSentTopLevelCommentsFillTheRoom() {
        val listed = fakeComment("3kc1", bob)
        val mine = sent("3ks1")
        val preview = CommentsPreview.of(3, listOf(thread(listed)), listOf(sent("3ks0", root = listed.uri), mine), Fixed.ALICE)

        assertEquals(listOf(listed.uri.value, mine.uri.value), keys(preview))
    }

    @Test
    fun loadingCommentsWhileTheFirstPageLoads() {
        val preview = CommentsPreview.of(14, emptyList(), emptyList(), Fixed.ALICE, loading = true)

        assertEquals(emptyList(), keys(preview))
        assertNotNull(preview.loadingText)
        assertNull(preview.viewAll)
    }

    @Test
    fun noLoadingLineWithoutCommentsOrOnceAnswered() {
        assertNull(CommentsPreview(count = 0, loading = true).loadingText)
        assertNull(CommentsPreview(count = 3, rows = persistentListOf(row("a")), loading = true).loadingText)
        // A first page that failed: nothing, and View all leads to the screen that says so.
        val failed = CommentsPreview.of(14, emptyList(), emptyList(), Fixed.ALICE, loading = false)
        assertNull(failed.loadingText)
        assertNotNull(failed.viewAll)
    }

    @Test
    fun viewAllOnlyWhenThereAreMoreThanShown() {
        assertNull(CommentsPreview(count = 0).viewAll)
        val two = persistentListOf(row("a"), row("b"))
        assertNull(CommentsPreview(count = 2, rows = two).viewAll)
        assertNotNull(CommentsPreview(count = 3, rows = two).viewAll)
    }

    private fun row(key: String) = CommentPreviewRow(key, "Bob", "Hi", Instant.fromEpochSeconds(0))

    private fun label(value: String) = LabelView(src = Fixed.ALICE, value = value)
}
