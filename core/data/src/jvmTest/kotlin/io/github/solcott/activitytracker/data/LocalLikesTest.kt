package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.feed.asSubject
import io.github.solcott.activitytracker.data.feed.withLikeOverrides
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ActivityViewerState
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.CommentViewerState
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class LocalLikesTest {
    private val bob = Did("did:plc:bob")
    private val mine = AtUri.of(Did("did:plc:alice"), Nsid.FEED_LIKE, "3kk1")

    private fun view(likeCount: Long, like: AtUri?) =
        ActivityView(
            uri = AtUri.of(bob, Nsid.FEED_ACTIVITY, "3kact1"),
            cid = Cid("bafyact"),
            author = ProfileViewBasic(bob, Handle("bob.test")),
            record =
                ActivityRecord(
                    sport = "run",
                    startedAt = Instant.parse("2026-09-30T07:00:00Z"),
                    elapsedTime = 1800,
                    createdAt = Instant.parse("2026-09-30T07:31:00Z"),
                ),
            likeCount = likeCount,
            commentCount = 0,
            indexedAt = Instant.parse("2026-09-30T07:32:00Z"),
            viewer = ActivityViewerState(like),
        )

    @Test
    fun staleAppViewReadsReflectARemovedLike() {
        val local = LocalLikes()
        val stale = view(likeCount = 1, like = mine)
        local.set(stale.uri, null)

        val shown = local.applyTo(stale)
        assertEquals(0, shown.likeCount)
        assertNull(shown.viewer?.like)
        // Applying twice, or once the AppView has caught up, changes nothing.
        assertEquals(shown, local.applyTo(shown))
        assertEquals(view(likeCount = 0, like = null), local.applyTo(view(likeCount = 0, like = null)))
    }

    @Test
    fun staleAppViewReadsReflectAGivenLike() {
        val local = LocalLikes()
        val stale = view(likeCount = 2, like = null)
        local.set(stale.uri, mine)

        assertEquals(view(likeCount = 3, like = mine), local.applyTo(stale))
    }

    @Test
    fun aGivenLikeIsOverlaidOnANonPublicActivityToo() {
        val local = LocalLikes()
        val stale = view(likeCount = 1, like = null).copy(visibility = Visibility.FOLLOWERS)
        local.set(stale.uri, mine)

        assertEquals(view(likeCount = 2, like = mine).copy(visibility = Visibility.FOLLOWERS), local.applyTo(stale))
    }

    @Test
    fun untouchedActivitiesPassThrough() {
        val stale = view(likeCount = 1, like = mine)
        assertEquals(stale, LocalLikes().applyTo(stale))
    }

    private fun comment(likeCount: Long?, like: AtUri?, audience: String? = null) =
        CommentView(
            uri = AtUri.of(bob, Nsid.FEED_COMMENT, "3kc1"),
            cid = Cid("bafyc1"),
            author = ProfileViewBasic(bob, Handle("bob.test")),
            text = "Nice",
            createdAt = Instant.parse("2026-09-30T08:00:00Z"),
            audience = audience,
            likeCount = likeCount,
            viewer = like?.let { CommentViewerState(it) },
        )

    @Test
    fun aCommentsCountMovesByTheViewersOwnToggle() {
        val key = AtUri.of(bob, Nsid.FEED_COMMENT, "3kc1").value
        val liked = comment(likeCount = 2, like = null).withLikeOverrides(mapOf(key to mine))
        assertEquals(3, liked.likeCount)
        assertEquals(mine, liked.viewer?.like)
        val unliked = comment(likeCount = 3, like = mine).withLikeOverrides(mapOf(key to null))
        assertEquals(2, unliked.likeCount)
        assertNull(unliked.viewer?.like)
        // An AppView that doesn't count yet reads as none.
        assertEquals(1, comment(likeCount = null, like = null).withLikeOverrides(mapOf(key to mine)).likeCount)
    }

    @Test
    fun aCommentsLikeIsPlacedByTheNarrowerOfItsSubjectAndItsAudience() {
        val activity = view(likeCount = 0, like = null).asSubject()
        assertEquals(Visibility.PUBLIC, comment(0, null).asSubject(on = activity).visibility)
        assertEquals(Visibility.FOLLOWERS, comment(0, null, audience = Visibility.FOLLOWERS).asSubject(on = activity).visibility)
        val mutuals = activity.copy(visibility = Visibility.MUTUALS)
        assertEquals(Visibility.MUTUALS, comment(0, null, audience = Visibility.FOLLOWERS).asSubject(on = mutuals).visibility)
    }
}
