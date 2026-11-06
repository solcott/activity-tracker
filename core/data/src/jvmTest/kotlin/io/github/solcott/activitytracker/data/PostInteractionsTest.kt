package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.atproto.commentHideRkey
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.FakeAthletePds.Session
import io.github.solcott.activitytracker.data.db.FeedItemEntity
import io.github.solcott.activitytracker.data.feed.asSubject
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostProposalStatus
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.PostViewerState
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive

/** Likes, comments and hides on posts (feed/posts): placed by the post's visibility and shown at once, as on activities. */
class PostInteractionsTest {
    private val pds = FakeAthletePds()
    private val did = pds.did
    private val writes = pds.writes
    private val stored = pds.stored
    private val bob = Did("did:plc:bob")
    private val carol = Did("did:plc:carol")
    private val at = Instant.parse("2026-11-06T09:00:00Z")

    /** A post by [author] at [visibility], as Alice sees it, cached in her timeline. */
    private suspend fun Session.post(author: Did, visibility: String, rkey: String = "3kpost"): PostView {
        val view =
            PostView(
                uri = AtUri.of(author, Nsid.FEED_POST, rkey),
                cid = Cid("bafy$rkey"),
                author = ProfileViewBasic(author, Handle("${author.value.last()}.test")),
                embed =
                    PostEmbedView.Proposal(
                        proposal = StrongRef(AtUri.of(author, Nsid.FEED_SEGMENT_NAME_PROPOSAL, rkey), Cid("bafyq")),
                        name = "Wiggly Woods",
                        status = PostProposalStatus.OPEN,
                        openedAt = at,
                        up = 0,
                        down = 0,
                    ),
                createdAt = at,
                indexedAt = at,
                visibility = visibility.takeIf { it != Visibility.PUBLIC },
                viewer = PostViewerState(),
            )
        db.feed()
            .upsert(listOf(FeedItemEntity("timeline", "post:${view.uri.value}", 0, AtJson.encodeToString(PostView.serializer(), view))))
        return view
    }

    private suspend fun Session.shown(post: PostView): PostView =
        feed.timelineItems().first().filterIsInstance<TimelineItem.Post>().single { it.post.uri == post.uri }.post

    @Test
    fun aLikeOnAFollowersPostGoesInTheInteractionsSpaceAndShowsAtOnce() = runTest {
        val s = pds.session()
        val post = s.post(bob, Visibility.FOLLOWERS)
        s.activities.toggleLike(post.asSubject())
        val like = writes.single { Nsid.FEED_LIKE in it }
        assertTrue(like.startsWith("put ${AthleteSpace.INTERACTIONS} ${Nsid.FEED_LIKE}/"), like)
        val record = stored.getValue(AthleteSpace.INTERACTIONS).getValue(like.substringAfter(' ').substringAfter(' '))
        assertEquals(post.uri.value, record.getValue("subject").let { AtJson.decodeFromJsonElement(StrongRef.serializer(), it) }.uri.value)
        val shown = s.shown(post)
        assertEquals(1, shown.likeCount)
        assertNotNull(shown.viewer?.like)
    }

    @Test
    fun unlikingAPostDeletesFromBothPlacesAndLowersTheCount() = runTest {
        val s = pds.session()
        val post = s.post(bob, Visibility.FOLLOWERS)
        s.activities.toggleLike(post.asSubject())
        val rkey = writes.single().substringAfterLast('/')
        writes.clear()
        // Since made Public: the like is still in the space, and unliking finds it there.
        s.activities.toggleLike(post.copy(visibility = null).asSubject())
        assertEquals(listOf("delete repo ${Nsid.FEED_LIKE}/$rkey", "delete ${AthleteSpace.INTERACTIONS} ${Nsid.FEED_LIKE}/$rkey"), writes)
        val shown = s.shown(post)
        assertEquals(0, shown.likeCount)
        assertNull(shown.viewer?.like)
    }

    @Test
    fun theAuthorCantLikeTheirOwnPost() = runTest {
        val s = pds.session()
        s.activities.toggleLike(s.post(did, Visibility.PUBLIC).asSubject())
        assertEquals(emptyList(), writes)
    }

    @Test
    fun aCommentOnAFollowersPostRecordsItsAudienceAndCountsAtOnce() = runTest {
        val s = pds.session()
        val post = s.post(bob, Visibility.FOLLOWERS)
        s.activities.comment(post.asSubject(), "  Yes  ")
        val put = writes.single { Nsid.FEED_COMMENT in it }
        assertTrue(put.startsWith("put ${AthleteSpace.INTERACTIONS} ${Nsid.FEED_COMMENT}/"), put)
        val record = stored.getValue(AthleteSpace.INTERACTIONS).getValue(put.substringAfter(' ').substringAfter(' '))
        assertEquals(Visibility.FOLLOWERS, record.getValue("audience").jsonPrimitive.content)
        assertEquals("Yes", record.getValue("text").jsonPrimitive.content)
        assertEquals(1, s.shown(post).commentCount)
    }

    @Test
    fun aCommentOnAPublicPostGoesInTheRepo() = runTest {
        val s = pds.session()
        s.activities.comment(s.post(bob, Visibility.PUBLIC).asSubject(), "Yes")
        assertEquals(listOf("put repo ${Nsid.FEED_COMMENT}"), writes.map { it.substringBeforeLast('/') })
    }

    @Test
    fun theAuthorHidesACommentOnTheirFollowersPost() = runTest {
        val s = pds.session()
        val post = s.post(did, Visibility.FOLLOWERS).copy(commentCount = 1)
        s.feed.updateCachedPost(post.uri) { post }
        val carols =
            CommentView(
                uri = AtUri.of(carol, Nsid.FEED_COMMENT, "3kc"),
                cid = Cid("bafyc"),
                author = ProfileViewBasic(carol, Handle("c.test")),
                text = "No",
                createdAt = at,
                audience = Visibility.FOLLOWERS,
            )
        assertTrue(s.activities.setCommentHidden(post.asSubject(), carols, hidden = true))
        val rkey = commentHideRkey(carols.uri)
        assertEquals(listOf("put ${AthleteSpace.INTERACTIONS} ${Nsid.FEED_COMMENT_HIDE}/$rkey"), writes)
        assertEquals(0, s.shown(post).commentCount)
        assertEquals(false, s.activities.setCommentHidden(s.post(bob, Visibility.PUBLIC, "3kbob").asSubject(), carols, hidden = true))
    }

    @Test
    fun aSessionWithoutTheSpaceScopeIsRefusedOnNonPublicPosts() = runTest {
        val s = pds.session(scope = "atproto")
        val post = s.post(bob, Visibility.MUTUALS)
        assertEquals(SpaceErrors.SCOPE_MISSING, assertFailsWith<XrpcException> { s.activities.toggleLike(post.asSubject()) }.error)
        assertEquals(SpaceErrors.SCOPE_MISSING, assertFailsWith<XrpcException> { s.activities.comment(post.asSubject(), "Yes") }.error)
        assertEquals(emptyList(), writes)
        val shown = s.shown(post)
        assertEquals(0, shown.likeCount)
        assertEquals(0, shown.commentCount)
    }
}
