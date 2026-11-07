package io.github.solcott.activitytracker.app.fixtures

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostProposalStatus
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.PostViewerState
import io.github.solcott.activitytracker.lexicon.app.QuotedPost
import io.github.solcott.activitytracker.lexicon.app.QuotedPostEmbed
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

/**
 * [author]'s quote (feed/quote-posts) of [quoted], at [visibility], with [text] and its counts: Carol quoting Bob's shared proposal by
 * default.
 */
@Suppress("LongParameterList") // What each sample varies.
fun fakeQuote(
    quoted: QuotedPost = fakePost().asQuotedPost(),
    author: Did = Fixed.CAROL,
    rkey: String = "3kquote",
    text: String = "Yes, and the lower half should be its own segment.",
    visibility: String = Visibility.FOLLOWERS,
    quoteCount: Long = 0,
): PostView =
    PostView(
        uri = AtUri.of(author, Nsid.FEED_POST, rkey),
        cid = Cid("bafyquote$rkey"),
        author = fakeProfileBasic(author),
        embed = PostEmbedView.Quote(quoted),
        createdAt = Fixed.NOW - 30.minutes,
        indexedAt = Fixed.NOW - 30.minutes,
        text = text,
        visibility = visibility.takeIf { it != Visibility.PUBLIC },
        likeCount = 2,
        commentCount = 1,
        quoteCount = quoteCount,
        viewer = PostViewerState(),
    )

/** Dave's quote of Carol's quote of Bob's post, which shows Carol's quote with a link in place of Bob's post. */
fun fakeQuoteOfQuote(): PostView =
    fakeQuote(
        quoted = fakeQuote().asQuotedPost(),
        author = Did("did:plc:dave"),
        rkey = "3kquote2",
        text = "Agreed, the lower half is a different trail.",
    )

/** Carol's quote of a post Bob deleted: "Post unavailable" in its place. */
fun fakeUnavailableQuote(): PostView =
    fakeQuote(quoted = QuotedPost.Unavailable(AtUri.of(Fixed.BOB, Nsid.FEED_POST, "3kgone")), rkey = "3kquote3", text = "This was right.")

/** This post as a quote of it shows it, one level deep. */
fun PostView.asQuotedPost(): QuotedPost.Post =
    QuotedPost.Post(
        uri = uri,
        cid = cid,
        author = author,
        createdAt = createdAt,
        text = text,
        visibility = visibility,
        embed =
            when (val shared = embed) {
                is PostEmbedView.Proposal -> shared
                is PostEmbedView.Quote -> (shared.quoted as? QuotedPost.Post)?.uri?.let(QuotedPostEmbed::Link)
                is PostEmbedView.Unknown -> null
            },
    )
