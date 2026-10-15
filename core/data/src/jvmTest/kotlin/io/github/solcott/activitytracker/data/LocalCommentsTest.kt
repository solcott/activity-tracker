package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.feed.LocalComments
import io.github.solcott.activitytracker.data.feed.LocalComments.Want
import io.github.solcott.activitytracker.data.feed.SentComment
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** [LocalComments]: what the viewer did to comments against what the AppView last said of them. */
class LocalCommentsTest {
    private val alice = Did("did:plc:alice")
    private val bob = Did("did:plc:bob")
    private val carol = Did("did:plc:carol")
    private val run = AtUri.of(alice, Nsid.FEED_ACTIVITY, "3krun")
    private val ride = AtUri.of(alice, Nsid.FEED_ACTIVITY, "3kride")

    private fun comment(author: Did, rkey: String, hidden: Boolean? = null) =
        CommentView(
            AtUri.of(author, Nsid.FEED_COMMENT, rkey),
            Cid("bafy$rkey"),
            ProfileViewBasic(author, Handle("${author.value.substringAfterLast(':')}.test")),
            "Hi",
            Instant.parse("2026-10-14T08:00:00Z"),
            hidden,
        )

    private val bobs = comment(bob, "3kcb")
    private val carols = comment(carol, "3kcc")

    private fun sent(rkey: String) =
        SentComment(
            AtUri.of(alice, Nsid.FEED_COMMENT, rkey),
            ProfileViewBasic(alice, Handle("alice.test")),
            "Mine",
            Instant.parse("2026-10-14T09:00:00Z"),
        )

    @Test
    fun aSentCommentIsListedAndCountedUntilTheAppViewListsIt() {
        val local = LocalComments()
        val mine = sent("3kca")
        local.send(run, mine)
        assertEquals(listOf(mine), local.sent(run))
        assertEquals(1, local.delta(run))
        assertEquals(0, local.delta(ride), "other activities don't move")

        // A page without it, or a lookup that leaves it out, means the AppView hasn't caught up.
        local.report(listOf(bobs))
        local.lookup(run, listOf(mine.uri), emptyList()).report()
        assertEquals(listOf(mine), local.sent(run))
        assertEquals(1, local.delta(run))

        val listed = comment(alice, "3kca")
        local.report(listOf(bobs, listed))
        assertEquals(emptyList(), local.sent(run), "listed in its place now")
        assertEquals(0, local.delta(run), "counted once, by the AppView")
        assertEquals(listOf(bobs, listed), local.applyTo(listOf(bobs, listed)))
    }

    @Test
    fun aDeleteIsLeftOutAndUncountedUntilALookupNoLongerFindsIt() {
        val local = LocalComments()
        local.change(run, bobs.uri, Want.Gone, listedHidden = false)
        assertEquals(listOf(carols), local.applyTo(listOf(bobs, carols)))
        assertEquals(-1, local.delta(run))

        local.report(listOf(bobs, carols))
        assertEquals(-1, local.delta(run), "still listed, so still counted by the AppView")
        assertEquals(listOf(bobs.uri), local.pending(run))

        local.lookup(run, listOf(bobs.uri), emptyList()).report()
        assertEquals(0, local.delta(run), "not taken off twice once the AppView drops it")
        assertEquals(emptyList(), local.pending(run))
    }

    @Test
    fun aHiddenCommentsDeleteMovesNoCount() {
        val local = LocalComments()
        local.change(run, bobs.uri, Want.Gone, listedHidden = true)
        assertEquals(0, local.delta(run), "the AppView never counted it")
        assertEquals(emptyList(), local.applyTo(listOf(bobs.copy(hidden = true))))
    }

    @Test
    fun deletingASentCommentTheAppViewDoesntListNetsToNothing() {
        val local = LocalComments()
        val mine = sent("3kca")
        local.send(run, mine)
        local.change(run, mine.uri, Want.Gone, listedHidden = false)
        assertEquals(emptyList(), local.sent(run))
        assertEquals(0, local.delta(run))
        assertEquals(emptyList(), local.pending(run), "settled at once")

        // The AppView indexes the comment before its delete: it's still left out, and taken off the count while listed.
        val listed = comment(alice, "3kca")
        local.report(listOf(listed))
        assertEquals(emptyList(), local.applyTo(listOf(listed)))
        assertEquals(-1, local.delta(run))
        assertEquals(listOf(listed.uri), local.pending(run))
        local.lookup(run, listOf(listed.uri), emptyList()).report()
        assertEquals(0, local.delta(run))
        assertEquals(emptyList(), local.applyTo(listOf(listed)), "a delete is kept for the session")
    }

    @Test
    fun deletingASentCommentTheAppViewListsTakesItOff() {
        val local = LocalComments()
        val mine = sent("3kca")
        local.send(run, mine)
        local.report(listOf(comment(alice, "3kca")))
        local.change(run, mine.uri, Want.Gone, listedHidden = false)
        assertEquals(-1, local.delta(run))
        assertEquals(emptyList(), local.applyTo(listOf(comment(alice, "3kca"))))
    }

    @Test
    fun aHideLowersTheCountOnceAndMarksTheComment() {
        val local = LocalComments()
        local.change(run, bobs.uri, Want.Present(true), listedHidden = false)
        assertTrue(local.isHidden(bobs.uri, viewHidden = false))
        assertEquals(listOf(true, null), local.applyTo(listOf(bobs, carols)).map { it.hidden })
        assertEquals(-1, local.delta(run))

        local.report(listOf(bobs.copy(hidden = true), carols))
        assertEquals(0, local.delta(run), "once the AppView has caught up, nothing moves again")
        assertEquals(emptyList(), local.pending(run))
    }

    @Test
    fun anUnhideRaisesTheCountOnlyWhileTheAppViewStillHidesIt() {
        val local = LocalComments()
        local.change(run, bobs.uri, Want.Present(false), listedHidden = true)
        assertFalse(local.isHidden(bobs.uri, viewHidden = true))
        assertEquals(listOf(null), local.applyTo(listOf(bobs.copy(hidden = true))).map { it.hidden })
        assertEquals(1, local.delta(run))
        local.lookup(run, listOf(bobs.uri), listOf(bobs)).report()
        assertEquals(0, local.delta(run))
    }

    @Test
    fun hidingAndUnhidingBeforeTheAppViewSeesEitherSettles() {
        val local = LocalComments()
        local.change(run, bobs.uri, Want.Present(true), listedHidden = false)
        // The view now shows it hidden, but the AppView still lists it shown.
        local.change(run, bobs.uri, Want.Present(false), listedHidden = true)
        assertEquals(0, local.delta(run))
        assertEquals(emptyList(), local.pending(run))
    }

    @Test
    fun aDeletedCommentIsntCountedBackInByAnUnhide() {
        val local = LocalComments()
        local.change(run, bobs.uri, Want.Present(false), listedHidden = true)
        local.change(run, bobs.uri, Want.Gone, listedHidden = false)
        assertEquals(0, local.delta(run), "the AppView still lists it hidden, so it never counted it")
        assertEquals(listOf(carols), local.applyTo(listOf(bobs.copy(hidden = true), carols)))
    }

    @Test
    fun aCommentTheLookupLeavesOutIsAbsentWhateverTheViewerWanted() {
        val local = LocalComments()
        // bob deleted his comment meanwhile, or it's out of the viewer's audience now.
        local.change(run, bobs.uri, Want.Present(false), listedHidden = true)
        local.lookup(run, listOf(bobs.uri), emptyList()).report()
        assertEquals(1, local.delta(run), "the viewer wants it counted and the AppView doesn't list it")
    }

    @Test
    fun restorePutsBackWhatAChangeReplaced() {
        val local = LocalComments()
        val first = local.change(run, bobs.uri, Want.Present(true), listedHidden = false)
        assertEquals(null, first)
        local.restore(bobs.uri, first)
        assertEquals(0, local.delta(run))
        assertFalse(local.isHidden(bobs.uri, viewHidden = false))

        local.change(run, bobs.uri, Want.Present(true), listedHidden = false)
        val previous = local.change(run, bobs.uri, Want.Gone, listedHidden = true)
        local.restore(bobs.uri, previous)
        assertTrue(local.isHidden(bobs.uri, viewHidden = false))
        assertEquals(-1, local.delta(run))
    }

    @Test
    fun aLookupAsksAboutTheNewestChangesAndTakesOlderOnesAsCaughtUp() {
        val local = LocalComments()
        val comments = (0 until 30).map { comment(bob, "3kc$it") }
        comments.forEach { local.change(run, it.uri, Want.Gone, listedHidden = false) }
        local.change(ride, carols.uri, Want.Gone, listedHidden = false)
        val pending = local.pending(run)
        assertEquals(comments.takeLast(25).reversed().map { it.uri }, pending, "the 25 newest, newest first")

        local.lookup(run, pending, comments.takeLast(25)).report()
        assertEquals(-25, local.delta(run), "the five oldest are taken as caught up")
        assertEquals(-1, local.delta(ride), "another activity's changes stay")
    }
}
