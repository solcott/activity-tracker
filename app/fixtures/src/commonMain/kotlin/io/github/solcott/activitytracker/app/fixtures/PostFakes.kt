package io.github.solcott.activitytracker.app.fixtures

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostProposalStatus
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.PostViewerState
import io.github.solcott.activitytracker.lexicon.app.ReasonRepost
import io.github.solcott.activitytracker.lexicon.app.RepostedPostView
import io.github.solcott.activitytracker.lexicon.app.SegmentNameViewerVote
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * [author]'s post sharing their proposal of [name] for the sample segment (feed/posts), at [visibility], [status], with [up] and [down]
 * votes, and the viewer's [vote] when they [canVote].
 */
@Suppress("LongParameterList") // What each sample varies.
fun fakePost(
    author: Did = Fixed.BOB,
    rkey: String = "3kpost",
    name: String = "Wiggly Woods",
    text: String? = "Everyone at the trailhead already calls it this.",
    visibility: String = Visibility.FOLLOWERS,
    status: String = PostProposalStatus.OPEN,
    up: Int = 4,
    down: Int = 1,
    canVote: Boolean = true,
    vote: String? = null,
): PostView {
    val proposal = AtUri.of(author, Nsid.FEED_SEGMENT_NAME_PROPOSAL, rkey)
    return PostView(
        uri = AtUri.of(author, Nsid.FEED_POST, rkey),
        cid = Cid("bafypost$rkey"),
        author = fakeProfileBasic(author),
        embed =
            PostEmbedView.Proposal(
                proposal = StrongRef(proposal, Cid("bafyproposal$rkey")),
                name = name,
                status = status,
                openedAt = Fixed.NOW - 26.hours,
                up = up,
                down = down,
                segment = fakeSegmentBasic(),
                viewerVote = vote?.let { SegmentNameViewerVote(AtUri.of(Fixed.ALICE, Nsid.FEED_SEGMENT_NAME_VOTE, "3kvote"), it) },
                canVote = canVote && status == PostProposalStatus.OPEN,
            ),
        createdAt = Fixed.NOW - 1.hours,
        indexedAt = Fixed.NOW - 1.hours,
        text = text,
        visibility = visibility.takeIf { it != Visibility.PUBLIC },
    )
}

/** An adopted proposal's post, as the timeline shows it once it's closed: its final totals and no vote. */
fun fakeAdoptedPost(): PostView =
    fakePost(
        rkey = "3kadopted",
        name = "Rooty Run",
        text = null,
        visibility = Visibility.PUBLIC,
        status = PostProposalStatus.ADOPTED,
        up = 12,
        down = 2,
    )

/** The viewer voted up on Bob's post. */
fun fakeVotedPost(): PostView = fakePost(vote = SegmentNameVoteDirection.UP, up = 5)

/**
 * Carol's public post in the viewer's timeline through Bob's repost (feed/reposts), with one other athlete they follow having reposted it
 * too, and the viewer's own repost when [reposted].
 */
fun fakeRepostedPost(reposted: Boolean = false): RepostedPostView {
    val post = fakePost(author = Fixed.CAROL, rkey = "3kcarol", name = "Ledge Line", visibility = Visibility.PUBLIC, canVote = false)
    return RepostedPostView(
        post =
            post.copy(
                likeCount = 3,
                commentCount = 1,
                repostCount = if (reposted) 3 else 2,
                viewer = PostViewerState(repost = AtUri.of(Fixed.ALICE, Nsid.FEED_REPOST, "3krepost").takeIf { reposted }),
            ),
        reason = ReasonRepost(fakeProfileBasic(Fixed.BOB), Fixed.NOW - 20.minutes, others = 1),
    )
}
