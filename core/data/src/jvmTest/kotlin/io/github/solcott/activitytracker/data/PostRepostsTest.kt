package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.FakeAthletePds.Session
import io.github.solcott.activitytracker.data.db.FeedItemEntity
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
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

/** Reposts of posts (feed/reposts): placed by the post's visibility like likes, and shown at once. */
class PostRepostsTest {
    private val pds = FakeAthletePds()
    private val did = pds.did
    private val writes = pds.writes
    private val stored = pds.stored
    private val bob = Did("did:plc:bob")
    private val at = Instant.parse("2026-11-07T09:00:00Z")

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
        cache(view)
        return view
    }

    private suspend fun Session.cache(view: PostView) =
        db.feed()
            .upsert(listOf(FeedItemEntity("timeline", "post:${view.uri.value}", 0, AtJson.encodeToString(PostView.serializer(), view))))

    private suspend fun Session.shown(post: PostView): PostView =
        feed.timelineItems().first().filterIsInstance<TimelineItem.Post>().single { it.post.uri == post.uri }.post

    @Test
    fun aRepostOfAFollowersPostGoesInTheInteractionsSpaceAndShowsAtOnce() = runTest {
        val s = pds.session()
        val post = s.post(bob, Visibility.FOLLOWERS)
        s.activities.toggleRepost(post)
        val repost = writes.single()
        assertTrue(repost.startsWith("put ${AthleteSpace.INTERACTIONS} ${Nsid.FEED_REPOST}/"), repost)
        val record = stored.getValue(AthleteSpace.INTERACTIONS).getValue(repost.substringAfter(' ').substringAfter(' '))
        assertEquals(post.uri, AtJson.decodeFromJsonElement(StrongRef.serializer(), record.getValue("subject")).uri)
        val shown = s.shown(post)
        assertEquals(1, shown.repostCount)
        assertNotNull(shown.viewer?.repost)
    }

    @Test
    fun aRepostOfAPublicPostGoesInTheRepo() = runTest {
        val s = pds.session()
        s.activities.toggleRepost(s.post(bob, Visibility.PUBLIC))
        assertEquals(listOf("put repo ${Nsid.FEED_REPOST}"), writes.map { it.substringBeforeLast('/') })
    }

    @Test
    fun undoingDeletesFromBothPlacesAndLowersTheCount() = runTest {
        val s = pds.session()
        val post = s.post(bob, Visibility.FOLLOWERS)
        s.activities.toggleRepost(post)
        val rkey = writes.single().substringAfterLast('/')
        writes.clear()
        // Since made Public: the repost is still in the space, and undoing finds it there.
        s.activities.toggleRepost(post.copy(visibility = null))
        assertEquals(
            listOf("delete repo ${Nsid.FEED_REPOST}/$rkey", "delete ${AthleteSpace.INTERACTIONS} ${Nsid.FEED_REPOST}/$rkey"),
            writes,
        )
        val shown = s.shown(post)
        assertEquals(0, shown.repostCount)
        assertNull(shown.viewer?.repost)
    }

    @Test
    fun theRepostOutlastsARefreshFromAnAppViewThatHasntCaughtUp() = runTest {
        val s = pds.session()
        val post = s.post(bob, Visibility.FOLLOWERS)
        s.activities.toggleRepost(post)
        // The refresh brings the AppView's view back, which doesn't know of the repost yet.
        s.cache(post)
        val shown = s.shown(post)
        assertEquals(1, shown.repostCount)
        assertNotNull(shown.viewer?.repost)
    }

    @Test
    fun theAuthorCantRepostTheirOwnPost() = runTest {
        val s = pds.session()
        s.activities.toggleRepost(s.post(did, Visibility.PUBLIC))
        assertEquals(emptyList(), writes)
    }

    @Test
    fun aSessionWithoutTheSpaceScopeIsRefusedOnNonPublicPosts() = runTest {
        val s = pds.session(scope = "atproto")
        val post = s.post(bob, Visibility.MUTUALS)
        assertEquals(SpaceErrors.SCOPE_MISSING, assertFailsWith<XrpcException> { s.activities.toggleRepost(post) }.error)
        assertEquals(emptyList(), writes)
        assertEquals(0, s.shown(post).repostCount)
    }
}
