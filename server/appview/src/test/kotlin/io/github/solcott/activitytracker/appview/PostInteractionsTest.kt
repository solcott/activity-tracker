package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.XrpcError
import io.github.solcott.activitytracker.atproto.commentHideRkey
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.app.GetLikes
import io.github.solcott.activitytracker.lexicon.app.GetPost
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Likes, comments and hides on posts (feed/posts, feed/post-page): stored like those on activities, judged against the post. */
class PostInteractionsTest : PostTestBase() {
    private val dave = "did:plc:dave"
    private val post
        get() = AtUri(postUri())

    /** Where [author]'s interaction on alice's post goes for a post at [tier]: their repo when public, else their interactions space. */
    private fun placeOf(tier: String) = if (tier == Visibility.PUBLIC) Visibility.PUBLIC else AthleteSpace.INTERACTIONS

    private fun like(author: String, rkey: String, tier: String) =
        put(author, Nsid.FEED_LIKE, rkey, """{"subject":{"uri":"${postUri()}","cid":"bafy"},"createdAt":"$now"}""", placeOf(tier))

    @Suppress("LongParameterList")
    private fun comment(
        author: String,
        rkey: String,
        tier: String,
        audience: String? = tier.takeIf { it != Visibility.PUBLIC },
        reply: Pair<String, String>? = null,
        text: String = "Yes",
    ) {
        val audienceField = audience?.let { ""","audience":"$it"""" }.orEmpty()
        val replyField =
            reply
                ?.let { (root, parent) -> ""","reply":{"root":{"uri":"$root","cid":"bafy"},"parent":{"uri":"$parent","cid":"bafy"}}""" }
                .orEmpty()
        put(
            author,
            Nsid.FEED_COMMENT,
            rkey,
            """{"subject":{"uri":"${postUri()}","cid":"bafy"},"text":"$text","createdAt":"$now"$audienceField$replyField}""",
            placeOf(if (audience == null) Visibility.PUBLIC else tier),
        )
    }

    private fun commentUri(author: String, rkey: String) = "at://$author/${Nsid.FEED_COMMENT}/$rkey"

    /** [author] hides [comment], stored where their like on a post at [tier] would be. */
    private fun hide(author: String, comment: String, tier: String) {
        val rkey = runBlocking { commentHideRkey(AtUri(comment)) }
        put(author, Nsid.FEED_COMMENT_HIDE, rkey, """{"subject":"$comment","createdAt":"$now"}""", placeOf(tier))
    }

    private fun likers(viewer: String?): List<String> = runBlocking {
        views.likes(viewer?.let(::Did), GetLikes.Params(post)).likes.map { it.did.value }
    }

    /** Every comment and reply listed for [viewer], across every page of threads and every page of each thread's replies. */
    private fun listed(viewer: String?): List<Pair<String, Boolean>> = runBlocking {
        val me = viewer?.let(::Did)
        buildList {
            var cursor: String? = null
            do {
                val page = views.comments(me, GetComments.Params(post, limit = 1, cursor = cursor))
                page.threads.orEmpty().forEach { thread ->
                    thread.root?.let { add(it.uri.value to (it.hidden == true)) }
                    var replies = thread.replies.map { it.uri.value to (it.hidden == true) }
                    var next = thread.repliesCursor
                    while (next != null) {
                        val more = views.comments(me, GetComments.Params(post, cursor = next, root = thread.uri))
                        replies = replies + more.comments.map { it.uri.value to (it.hidden == true) }
                        next = more.cursor
                    }
                    addAll(replies)
                }
                cursor = page.cursor
            } while (cursor != null)
        }
    }

    private fun postFor(viewer: String?): PostView = runBlocking { views.post(viewer?.let(::Did), GetPost.Params(post)).post }

    private fun notFound(block: suspend () -> Unit) {
        val error = assertFailsWith<XrpcError> { runBlocking { block() } }
        assertEquals("NotFound", error.error)
    }

    private fun assertNotFoundEverywhere(viewer: String?) {
        val me = viewer?.let(::Did)
        notFound { views.post(me, GetPost.Params(post)) }
        notFound { views.likes(me, GetLikes.Params(post)) }
        notFound { views.comments(me, GetComments.Params(post)) }
    }

    // ---------------------------------------------------------------- Likes

    @Test
    fun aFollowersLikeIsCountedForFollowersAndListed() {
        aliceProposes()
        follow(bob, alice)
        share(tier = Visibility.FOLLOWERS)
        like(bob, "3kl1", Visibility.FOLLOWERS)
        val seen = postFor(bob)
        assertEquals(1, seen.likeCount)
        assertEquals("at://$bob/${Nsid.FEED_LIKE}/3kl1", seen.viewer?.like?.value)
        assertEquals(listOf(bob), likers(alice))
        assertEquals(1, postFor(alice).likeCount)
        assertNull(postFor(alice).viewer?.like)
    }

    @Test
    fun narrowingLeavesOnlyLikesFromGiversWhoMaySeeIt() {
        aliceProposes()
        follow(bob, alice)
        follow(alice, bob)
        follow(carol, alice)
        share()
        like(bob, "3kl1", Visibility.PUBLIC)
        like(carol, "3kl2", Visibility.PUBLIC)
        assertEquals(2, postFor(null).likeCount)
        // Moved to Mutuals: only bob is a mutual, so carol's like, stored in her repo, no longer counts.
        share(tier = Visibility.MUTUALS)
        remove(alice, Nsid.FEED_POST, "3kp")
        assertEquals(1, postFor(bob).likeCount)
        assertEquals(listOf(bob), likers(alice))
    }

    @Test
    fun aBlockedGiversLikeIsLeftOutForTheBlocker() {
        aliceProposes()
        share()
        like(bob, "3kl1", Visibility.PUBLIC)
        like(carol, "3kl2", Visibility.PUBLIC)
        block(carol, bob)
        assertEquals(1, postFor(carol).likeCount)
        assertEquals(listOf(carol), likers(carol))
        assertEquals(2, postFor(alice).likeCount)
    }

    @Test
    fun likesAreCountedOnTimelineAndProfileItems() {
        aliceProposes()
        follow(bob, alice)
        share()
        like(bob, "3kl1", Visibility.PUBLIC)
        comment(bob, "3kc1", Visibility.PUBLIC)
        val inTimeline = timelinePosts(bob).single()
        assertEquals(1, inTimeline.likeCount)
        assertEquals(1, inTimeline.commentCount)
        assertEquals(1, profilePosts(alice, null).single().likeCount)
        assertNull(profilePosts(alice, null).single().viewer, "signed out")
    }

    // ---------------------------------------------------------------- Comments

    @Test
    fun aFollowersCommentIsListedForFollowersOnly() {
        aliceProposes()
        follow(bob, alice)
        share(tier = Visibility.FOLLOWERS)
        comment(bob, "3kc1", Visibility.FOLLOWERS)
        assertEquals(listOf(commentUri(bob, "3kc1") to false), listed(alice))
        assertEquals(1, postFor(alice).commentCount)
        assertNotFoundEverywhere(carol)
        assertNotFoundEverywhere(null)
    }

    @Test
    fun wideningNeverShowsACommentWiderThanWritten() {
        aliceProposes()
        follow(bob, alice)
        share(tier = Visibility.FOLLOWERS)
        comment(bob, "3kc1", Visibility.FOLLOWERS)
        share()
        remove(alice, Nsid.FEED_POST, "3kp", Visibility.FOLLOWERS)
        assertEquals(0, postFor(carol).commentCount)
        assertTrue(listed(carol).isEmpty())
        assertEquals(1, postFor(bob).commentCount)
    }

    @Test
    fun aCommenterWhoUnfollowsIsNoLongerShown() {
        aliceProposes()
        follow(bob, alice)
        follow(carol, alice)
        share(tier = Visibility.FOLLOWERS)
        comment(bob, "3kc1", Visibility.FOLLOWERS)
        remove(bob, Nsid.GRAPH_FOLLOW, "3kfbe")
        assertEquals(0, postFor(carol).commentCount)
        assertTrue(listed(carol).isEmpty())
    }

    @Test
    fun aReplyIsCappedByItsThreadsRoot() {
        aliceProposes()
        follow(bob, alice)
        follow(alice, bob)
        follow(carol, alice)
        share()
        comment(bob, "3kc1", Visibility.MUTUALS, audience = Visibility.MUTUALS)
        // A dishonest client's Public reply to a Mutuals comment never reaches past the root's audience.
        comment(dave, "3kc2", Visibility.PUBLIC, audience = null, reply = commentUri(bob, "3kc1") to commentUri(bob, "3kc1"))
        commit(dave, Nsid.ACTOR_PROFILE, "self", """{"displayName":"d"}""")
        assertTrue(listed(carol).isEmpty(), "carol is a follower, not a mutual")
        assertEquals(0, postFor(carol).commentCount)
        assertEquals(listOf(commentUri(bob, "3kc1"), commentUri(dave, "3kc2")), listed(alice).map { it.first })
    }

    @Test
    fun theCountMatchesTheListAcrossPages() {
        aliceProposes()
        follow(bob, alice)
        follow(carol, alice)
        follow(alice, bob)
        share()
        comment(bob, "3kc1", Visibility.PUBLIC)
        comment(carol, "3kc2", Visibility.PUBLIC)
        comment(bob, "3kc3", Visibility.MUTUALS, audience = Visibility.MUTUALS)
        comment(carol, "3kc4", Visibility.PUBLIC, reply = commentUri(bob, "3kc1") to commentUri(bob, "3kc1"))
        comment(bob, "3kc5", Visibility.PUBLIC, reply = commentUri(bob, "3kc1") to commentUri(carol, "3kc4"))
        comment(carol, "3kc6", Visibility.PUBLIC, reply = commentUri(bob, "3kc1") to commentUri(bob, "3kc5"))
        comment(bob, "3kc7", Visibility.PUBLIC, reply = commentUri(bob, "3kc1") to commentUri(carol, "3kc6"))
        hide(alice, commentUri(carol, "3kc2"), Visibility.PUBLIC)
        for (viewer in listOf(alice, bob, carol, dave, null)) {
            val shown = listed(viewer)
            assertEquals(shown.count { !it.second }.toLong(), postFor(viewer).commentCount, "viewer $viewer")
        }
        assertEquals(5, postFor(carol).commentCount, "carol isn't a mutual, and her hidden comment isn't counted")
    }

    // ---------------------------------------------------------------- Hides

    @Test
    fun theAuthorsHideLeavesTheCountAndMarksTheComment() {
        aliceProposes()
        follow(bob, alice)
        follow(carol, alice)
        share(tier = Visibility.FOLLOWERS)
        comment(bob, "3kc1", Visibility.FOLLOWERS)
        hide(alice, commentUri(bob, "3kc1"), Visibility.FOLLOWERS)
        assertEquals(0, postFor(carol).commentCount)
        assertTrue(listed(carol).isEmpty())
        assertEquals(listOf(commentUri(bob, "3kc1") to true), listed(alice))
        assertEquals(listOf(commentUri(bob, "3kc1") to true), listed(bob))
    }

    @Test
    fun aStrangersHideChangesNothing() {
        aliceProposes()
        share()
        comment(bob, "3kc1", Visibility.PUBLIC)
        hide(carol, commentUri(bob, "3kc1"), Visibility.PUBLIC)
        assertEquals(1, postFor(null).commentCount)
        assertEquals(listOf(commentUri(bob, "3kc1") to false), listed(alice))
    }

    // ---------------------------------------------------------------- Blocks, mutes, the post going

    @Test
    fun blocksAndMutesHideCommentersOnPosts() {
        aliceProposes()
        share()
        comment(bob, "3kc1", Visibility.PUBLIC)
        comment(carol, "3kc2", Visibility.PUBLIC)
        block(alice, bob)
        assertEquals(listOf(commentUri(carol, "3kc2")), listed(null).map { it.first }, "a block with the author hides bob for everyone")
        spaceWrite(
            space(AthleteSpace.INTERACTIONS, dave),
            Nsid.GRAPH_MUTE,
            carol,
            """{"subject":"$carol","createdAt":"2026-10-21T09:00:00Z"}""",
        )
        commit(dave, Nsid.ACTOR_PROFILE, "self", """{"displayName":"d"}""")
        assertTrue(listed(dave).isEmpty())
        assertEquals(0, postFor(dave).commentCount)
    }

    @Test
    fun withdrawingTheProposalTakesTheInteractionsWithIt() {
        aliceProposes()
        follow(bob, alice)
        share()
        like(bob, "3kl1", Visibility.PUBLIC)
        comment(bob, "3kc1", Visibility.PUBLIC)
        commit(alice, Nsid.FEED_SEGMENT_NAME_PROPOSAL, "3kp", null)
        for (viewer in listOf(alice, bob, null)) assertNotFoundEverywhere(viewer)
    }

    @Test
    fun aTakenDownPostIsNotFound() {
        aliceProposes()
        share()
        comment(bob, "3kc1", Visibility.PUBLIC)
        label(postUri(), "!takedown")
        assertNotFoundEverywhere(bob)
    }

    @Test
    fun getPostServesTheAuthorAndAdmittedViewersOnly() {
        aliceProposes()
        follow(bob, alice)
        share(tier = Visibility.FOLLOWERS)
        assertEquals(Visibility.FOLLOWERS, postFor(alice).visibility)
        assertEquals(postUri(), postFor(bob).uri.value)
        assertNotFoundEverywhere(carol)
        assertNotFoundEverywhere(null)
    }

    @Test
    fun anythingButAnActivityOrPostIsNotFound() {
        aliceProposes()
        notFound { views.likes(Did(bob), GetLikes.Params(AtUri(proposalUri()))) }
        notFound { views.comments(Did(bob), GetComments.Params(AtUri(proposalUri()))) }
    }
}
