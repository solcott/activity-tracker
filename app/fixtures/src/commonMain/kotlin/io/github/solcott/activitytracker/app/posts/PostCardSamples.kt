package io.github.solcott.activitytracker.app.posts

import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.fakeAdoptedPost
import io.github.solcott.activitytracker.app.fixtures.fakePost
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.fixtures.fakeQuote
import io.github.solcott.activitytracker.app.fixtures.fakeQuoteOfQuote
import io.github.solcott.activitytracker.app.fixtures.fakeRepostedPost
import io.github.solcott.activitytracker.app.fixtures.fakeUnavailableQuote
import io.github.solcott.activitytracker.app.fixtures.fakeVotedPost
import io.github.solcott.activitytracker.data.feed.PENDING_POST_CID
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.PostViewerState
import io.github.solcott.activitytracker.lexicon.app.RepostedPostView
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import kotlinx.serialization.json.JsonObject

/** One post card (feed/posts) in each case it draws differently, as [viewer] (Alice) sees it. */
object PostCardSamples {
    val viewer: String
        get() = Fixed.ALICE.value

    /** Bob's Followers proposal, liked by Alice, with counts. */
    val proposal: PostView
        get() = fakePost().copy(likeCount = 3, commentCount = 2, repostCount = 1, quoteCount = 2, viewer = PostViewerState(like = likeUri))

    /** Alice voted down on Bob's proposal. */
    val votedDown: PostView
        get() = fakePost(vote = SegmentNameVoteDirection.DOWN, down = 2)

    /** Alice's own Followers proposal: no Like or Repost, no menu. */
    val own: PostView
        get() = fakePost(author = Fixed.ALICE, rkey = "3kown", canVote = false).copy(viewer = PostViewerState())

    /** Alice's own post sent this session, not yet listed: no Quote. */
    val pending: PostView
        get() = own.copy(cid = Cid(PENDING_POST_CID))

    /** Carol's quote of Bob's proposal. */
    val quote: PostView
        get() = fakeQuote()

    /** Alice's own quote, which offers Delete. */
    val ownQuote: PostView
        get() = fakeQuote(author = Fixed.ALICE, rkey = "3kownquote", quoteCount = 1)

    /** An embed this version can't show. */
    val unknown: PostView
        get() = fakePost(rkey = "3kunknown").copy(embed = PostEmbedView.Unknown(JsonObject(emptyMap())))

    /** Every card shown on its own, by the sample's name. */
    val all: Map<String, PostView>
        get() =
            mapOf(
                "proposal" to proposal,
                "voted" to fakeVotedPost(),
                "votedDown" to votedDown,
                "adopted" to fakeAdoptedPost(),
                "own" to own,
                "pending" to pending,
                "quote" to quote,
                "quoteOfQuote" to fakeQuoteOfQuote(),
                "unavailableQuote" to fakeUnavailableQuote(),
                "ownQuote" to ownQuote,
                "unknown" to unknown,
            )

    /** Cards in a timeline through a repost (feed/reposts): Bob's and one other's, and Alice's own. */
    val reposts: Map<String, RepostedPostView>
        get() {
            val bobs = fakeRepostedPost()
            val alone = bobs.copy(reason = bobs.reason.copy(others = 0))
            val alices =
                fakeRepostedPost(reposted = true).let { it.copy(reason = it.reason.copy(by = fakeProfileBasic(Fixed.ALICE), others = 0)) }
            return mapOf("repostedByOthers" to bobs, "repostedByOne" to alone, "repostedByYou" to alices)
        }

    private val likeUri = AtUri.of(Fixed.ALICE, Nsid.FEED_LIKE, "3klike")
}
