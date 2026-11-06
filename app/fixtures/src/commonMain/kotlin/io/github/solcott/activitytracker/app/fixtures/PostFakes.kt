package io.github.solcott.activitytracker.app.fixtures

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostProposalStatus
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.SegmentNameViewerVote
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.time.Duration.Companion.hours

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
