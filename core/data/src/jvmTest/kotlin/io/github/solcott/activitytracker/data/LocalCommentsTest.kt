package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.feed.LocalCommentHides
import io.github.solcott.activitytracker.data.feed.LocalDeletes
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class LocalCommentHidesTest {
    private val alice = Did("did:plc:alice")
    private val bob = Did("did:plc:bob")
    private val carol = Did("did:plc:carol")

    private val activity =
        ActivityView(
            uri = AtUri.of(alice, Nsid.FEED_ACTIVITY, "3kact1"),
            cid = Cid("bafyact"),
            author = ProfileViewBasic(alice, Handle("alice.test")),
            record =
                ActivityRecord(
                    sport = "run",
                    startedAt = Instant.parse("2026-10-14T07:00:00Z"),
                    elapsedTime = 1800,
                    createdAt = Instant.parse("2026-10-14T07:31:00Z"),
                ),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse("2026-10-14T07:32:00Z"),
        )

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

    /** What the AppView serves the athlete: every comment listed, those it knows are hidden marked and left out of [count]. */
    private fun served(count: Long, vararg comments: CommentView) =
        GetActivity.Output(activity.copy(commentCount = count), comments.toList())

    @Test
    fun aHideTheAppViewHasntSeenLowersTheCountOnce() {
        val hides = LocalCommentHides()
        hides.set(bobs.uri, true)

        val shown = hides.applyTo(served(2, bobs, carols))
        assertEquals(1, shown.activity.commentCount)
        assertEquals(listOf(true, null), shown.comments.map { it.hidden })
        // Once the AppView has caught up, nothing moves again.
        assertEquals(served(1, bobs.copy(hidden = true), carols), hides.applyTo(served(1, bobs.copy(hidden = true), carols)))
    }

    @Test
    fun anUnhideRaisesTheCountOnlyWhileTheAppViewStillHidesIt() {
        val hides = LocalCommentHides()
        hides.set(bobs.uri, false)

        val shown = hides.applyTo(served(1, bobs.copy(hidden = true), carols))
        assertEquals(2, shown.activity.commentCount)
        assertEquals(listOf(null, null), shown.comments.map { it.hidden })
        assertEquals(served(2, bobs, carols), hides.applyTo(served(2, bobs, carols)))
    }

    @Test
    fun aDeletedCommentIsntCountedBackIn() {
        val deletes = LocalDeletes()
        val hides = LocalCommentHides()
        val stale = served(1, bobs.copy(hidden = true), carols)
        deletes.deleteComment(bobs.uri)
        hides.set(bobs.uri, false)

        val shown = hides.applyTo(deletes.applyTo(stale))
        assertEquals(1, shown.activity.commentCount)
        assertEquals(listOf(carols), shown.comments)
    }
}
