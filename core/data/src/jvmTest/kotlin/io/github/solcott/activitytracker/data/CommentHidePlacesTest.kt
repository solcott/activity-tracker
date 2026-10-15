package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.commentHideRkey
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.FakeAthletePds.Session
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ActivityViewerState
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive

/** Hiding and unhiding comments on the athlete's own activities in the data layer (`feed/comments`): where hides go, and what shows. */
class CommentHidePlacesTest {
    private val pds = FakeAthletePds()
    private val did = pds.did
    private val repo = pds.repo
    private val bob = Did("did:plc:bob")
    private val stored = pds.stored
    private val writes = pds.writes
    private val created = pds.created
    private var offline by pds::offline
    private var served by pds::served

    private fun placesOf(collection: String, rkey: String) = pds.placesOf(collection, rkey)

    private suspend fun session(scope: String? = null) = pds.session(scope)

    private fun view(uri: AtUri, author: Did, visibility: String, comments: Long) =
        ActivityView(
            uri = uri,
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
            visibility = visibility,
            viewer = ActivityViewerState(),
        )

    /** One of Bob's activities, as Alice sees it at [visibility], cached in her feed. */
    private suspend fun Session.bobs(visibility: String, comments: Long = 0): ActivityView =
        cache(view(AtUri.of(bob, Nsid.FEED_ACTIVITY, "3kbob"), bob, visibility, comments))

    /** A comment by [author] with rkey [rkey], as the AppView lists it. */
    private fun commentOn(author: Did, rkey: String) =
        CommentView(
            AtUri.of(author, Nsid.FEED_COMMENT, rkey),
            Cid("bafy$rkey"),
            ProfileViewBasic(author, Handle("${author.value.substringAfterLast(':')}.test")),
            "Nice",
            Instant.parse("2026-10-14T08:00:00Z"),
        )

    /** One of Alice's own activities at [visibility] with [comments] counted, cached in her feed. */
    private suspend fun Session.alices(visibility: String, comments: Long = 2): ActivityView =
        cache(view(AtUri.of(did, Nsid.FEED_ACTIVITY, "3kown"), did, visibility, comments))

    private fun hideRkey(comment: CommentView) = runBlocking { commentHideRkey(comment.uri) }

    @Test
    fun aHideOnAPublicActivityGoesInTheRepoAtTheCommentsKey() = runTest {
        val s = session()
        val public = s.alices(Visibility.PUBLIC)
        val bobs = commentOn(bob, "3kc2")
        s.activities.setCommentHidden(public, bobs, hidden = true)

        assertEquals(listOf("put repo ${Nsid.FEED_COMMENT_HIDE}/${hideRkey(bobs)}"), writes)
        assertEquals(
            bobs.uri.value,
            stored.getValue(repo).getValue("${Nsid.FEED_COMMENT_HIDE}/${hideRkey(bobs)}")["subject"]?.jsonPrimitive?.content,
        )
        assertEquals(emptyList(), created)
        assertEquals(1, s.cached(public).commentCount)
        assertEquals(0, s.db.outbox().pending(did.value).size)
    }

    @Test
    fun aHideOnANonPublicActivityGoesInTheInteractionsSpace() = runTest {
        val s = session()
        val followers = s.alices(Visibility.FOLLOWERS)
        val bobs = commentOn(bob, "3kc2")
        s.activities.setCommentHidden(followers, bobs, hidden = true)
        assertEquals(listOf("put ${AthleteSpace.INTERACTIONS} ${Nsid.FEED_COMMENT_HIDE}/${hideRkey(bobs)}"), writes)
        assertEquals(setOf(AthleteSpace.INTERACTIONS), placesOf(Nsid.FEED_COMMENT_HIDE, hideRkey(bobs)))
    }

    @Test
    fun hidingTwiceWritesAndCountsOnce() = runTest {
        val s = session()
        val public = s.alices(Visibility.PUBLIC)
        val bobs = commentOn(bob, "3kc2")
        s.activities.setCommentHidden(public, bobs, hidden = true)
        s.activities.setCommentHidden(public, bobs, hidden = true)
        assertEquals(1, writes.size)
        assertEquals(1, s.cached(public).commentCount)
    }

    @Test
    fun aHiddenCommentStaysHiddenUntilTheAppViewCatchesUp() = runTest {
        val s = session()
        val public = s.alices(Visibility.PUBLIC)
        val bobs = commentOn(bob, "3kc2")
        val carols = commentOn(Did("did:plc:carol"), "3kc3")
        served = GetActivity.Output(public, listOf(bobs, carols))
        s.activities.setCommentHidden(public, bobs, hidden = true)

        val stale = s.feed.activity(public.uri)
        assertEquals(listOf(true, null), stale.comments.map { it.hidden })
        assertEquals(1, stale.activity.commentCount)

        served = GetActivity.Output(public.copy(commentCount = 1), listOf(bobs.copy(hidden = true), carols))
        assertEquals(1, s.feed.activity(public.uri).activity.commentCount, "not taken off twice")
    }

    @Test
    fun unhidingDeletesFromBothPlaces() = runTest {
        val s = session()
        val followers = s.alices(Visibility.FOLLOWERS, comments = 1)
        val bobs = commentOn(bob, "3kc2")
        s.activities.setCommentHidden(followers, bobs, hidden = true)
        writes.clear()

        // Made public since: the hide is still in the space, and unhiding finds it there.
        s.activities.setCommentHidden(followers.copy(visibility = Visibility.PUBLIC), bobs.copy(hidden = true), hidden = false)
        val rkey = hideRkey(bobs)
        assertEquals(
            listOf("delete repo ${Nsid.FEED_COMMENT_HIDE}/$rkey", "delete ${AthleteSpace.INTERACTIONS} ${Nsid.FEED_COMMENT_HIDE}/$rkey"),
            writes,
        )
        assertEquals(emptySet(), placesOf(Nsid.FEED_COMMENT_HIDE, rkey))
        assertEquals(1, s.cached(followers).commentCount)
        assertEquals(0, s.db.outbox().pending(did.value).size)
    }

    @Test
    fun anUnhideWaitsInTheOutboxWhileOffline() = runTest {
        val s = session()
        val public = s.alices(Visibility.PUBLIC, comments = 0)
        val hidden = commentOn(bob, "3kc2").copy(hidden = true)
        offline = true
        s.activities.setCommentHidden(public, hidden, hidden = false)
        assertEquals(2, s.db.outbox().pending(did.value).size, "both deletes kept")
        offline = false
        s.activities.sync()
        assertEquals(0, s.db.outbox().pending(did.value).size)
    }

    @Test
    fun aSessionWhoseSpaceScopeLacksHidesHidesOnPublicActivitiesOnlyAndUnhidesFromTheRepo() = runTest {
        // Signed in before the declaration listed feed.commentHide.
        val s = session(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}&collection=${Nsid.FEED_COMMENT}")
        val followers = s.alices(Visibility.FOLLOWERS)
        val bobs = commentOn(bob, "3kc2")
        val e = assertFailsWith<XrpcException> { s.activities.setCommentHidden(followers, bobs, hidden = true) }
        assertEquals(SpaceErrors.SCOPE_MISSING, e.error)
        assertEquals(emptyList(), writes, "nothing written")
        assertEquals(emptyList(), created, "nor a space created")
        assertEquals(2, s.cached(followers).commentCount, "nor uncounted")
        assertEquals(0, s.db.outbox().pending(did.value).size, "nor left in the outbox")

        s.activities.setCommentHidden(followers, bobs.copy(hidden = true), hidden = false)
        assertEquals(listOf("delete repo ${Nsid.FEED_COMMENT_HIDE}/${hideRkey(bobs)}"), writes)

        writes.clear()
        s.activities.setCommentHidden(s.alices(Visibility.PUBLIC), commentOn(bob, "3kc4"), hidden = true)
        assertEquals(listOf("put repo ${Nsid.FEED_COMMENT_HIDE}"), writes.map { it.substringBeforeLast('/') })
    }

    @Test
    fun onlyOthersCommentsOnOwnActivitiesAreHidden() = runTest {
        val s = session()
        s.activities.setCommentHidden(s.bobs(Visibility.PUBLIC), commentOn(Did("did:plc:carol"), "3kc3"), hidden = true)
        s.activities.setCommentHidden(s.alices(Visibility.PUBLIC), commentOn(did, "3kc1"), hidden = true)
        assertEquals(emptyList(), writes)
    }

    @Test
    fun deletingAHiddenCommentDoesntLowerTheCount() = runTest {
        val s = session()
        val public = s.bobs(Visibility.PUBLIC, comments = 1)
        val mine = commentOn(did, "3kc1").copy(hidden = true)
        served = GetActivity.Output(public, listOf(mine, commentOn(Did("did:plc:carol"), "3kc3")))
        s.activities.deleteComment(public, mine)
        assertEquals(1, s.cached(public).commentCount)
        assertEquals(1, s.feed.activity(public.uri).activity.commentCount)
    }
}
