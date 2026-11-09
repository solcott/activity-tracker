package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.PostQuoteChains
import io.github.solcott.activitytracker.appview.db.Posts
import io.github.solcott.activitytracker.appview.db.SegmentNameProposals
import io.github.solcott.activitytracker.appview.segments.listedProposalTo
import io.github.solcott.activitytracker.lexicon.Did
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.jdbc.select

// Which posts a reader may see (feed/posts, feed/quote-posts): the one rule every read of posts goes through.

/**
 * The post rows [viewer] may see: at a tier that admits them with no block either way ([visibleTo]) and not hidden from them by a label,
 * and either sharing a proposal that is indexed, whose record still exists, and that a list may show them ([listedProposal]), or quoting a
 * post along a chain that passes for them ([quoteChainPasses]); never a quote too far from its first post. Every post query reads through
 * this, so a post never outlives its proposal and a quote never reaches past the posts it quotes.
 */
internal fun postRows(viewer: Did?): Op<Boolean> =
    postRowsAs(viewer?.let { stringParam(it.value) }) { visibility, author, uri, cid -> visibleTo(viewer, visibility, author, uri, cid) }

/**
 * [postRows] for the athlete in [reader], a column of the outer query such as a quote's author, by the same rules: one query asks for every
 * row of a page what its own reader may see, as `visibleTo` does for a like's giver.
 */
internal fun postRowsFor(reader: Column<String>): Op<Boolean> =
    postRowsAs(reader) { visibility, author, uri, cid -> visibleTo(reader, visibility, author, uri, cid) }

/** Whether a post reader's tier admits them to the row with these columns ([visibleTo]), whichever way the reader is given. */
internal fun interface PostTier {
    fun admits(visibility: Column<String>, author: Column<String>, uri: Column<String>, cid: Column<String>): Op<Boolean>
}

/** [postRows] for [reader] (null signed out), whose tier [tier] checks. */
private fun postRowsAs(reader: Expression<String>?, tier: PostTier): Op<Boolean> =
    tier.admits(Posts.visibility, Posts.authorDid, Posts.uri, Posts.cid) and
        unhiddenTo(reader, Posts.authorDid, Posts.uri) and
        ((Posts.proposalUri.isNotNull() and liveProposal(Posts.proposalUri, Posts.authorDid, reader)) or
            (Posts.quotedUri.isNotNull() and quoteChainPasses(reader, tier))) and
        not(
            exists(
                PostQuoteChains.select(PostQuoteChains.postUri).where {
                    (PostQuoteChains.postUri eq Posts.uri) and (PostQuoteChains.depth eq PostQuoteChains.MAX_DEPTH)
                }
            )
        )

/** The post's [proposal] is indexed, by its [author], its record still exists, and a list may show it to [reader] ([listedProposal]). */
private fun liveProposal(proposal: Column<String?>, author: Column<String>, reader: Expression<String>?): Op<Boolean> =
    exists(
        SegmentNameProposals.select(SegmentNameProposals.uri).where {
            (SegmentNameProposals.uri eq proposal) and
                (SegmentNameProposals.proposerDid eq author) and
                (SegmentNameProposals.recordGone eq false) and
                listedProposalTo(reader)
        }
    )
