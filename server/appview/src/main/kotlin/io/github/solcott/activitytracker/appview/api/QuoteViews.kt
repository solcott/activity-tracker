package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.PostQuoteChains
import io.github.solcott.activitytracker.appview.db.Posts
import io.github.solcott.activitytracker.appview.db.SegmentNameProposals
import io.github.solcott.activitytracker.appview.segments.unlabelledProposal
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.QuotedPost
import io.github.solcott.activitytracker.lexicon.app.QuotedPostEmbed
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.Alias
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

// The read side of quote posts (feed/quote-posts): whose chain lets a viewer see a quote, and the quoted post a quote shows.

/**
 * A quote's chain (feed/quote-posts) passes for [reader] (null signed out, whose tier [tier] checks) unless some post in it is served to
 * anyone ([postServed]) but not to them, while every post before it in the chain is served: a post served to no one ends the chain, so its
 * quotes show it as unavailable instead.
 */
internal fun quoteChainPasses(reader: Expression<String>?, tier: PostTier): Op<Boolean> {
    val chain = PostQuoteChains.alias("quote_chain")
    val ancestor = Posts.alias("quote_ancestor")
    val before = PostQuoteChains.alias("quote_chain_before")
    val earlier = Posts.alias("quote_ancestor_before")
    val brokenBefore =
        exists(
            before.select(before[PostQuoteChains.postUri]).where {
                (before[PostQuoteChains.postUri] eq Posts.uri) and
                    (before[PostQuoteChains.depth] less chain[PostQuoteChains.depth]) and
                    not(
                        exists(
                            earlier.select(earlier[Posts.uri]).where {
                                (earlier[Posts.uri] eq before[PostQuoteChains.ancestorUri]) and postServed(earlier)
                            },
                        ),
                    )
            },
        )
    val hiding =
        chain
            .join(ancestor, JoinType.INNER, chain[PostQuoteChains.ancestorUri], ancestor[Posts.uri])
            .select(chain[PostQuoteChains.postUri])
            .where {
                (chain[PostQuoteChains.postUri] eq Posts.uri) and
                    postServed(ancestor) and
                    not(
                        tier.admits(ancestor[Posts.visibility], ancestor[Posts.authorDid], ancestor[Posts.uri], ancestor[Posts.cid]) and
                            unhiddenTo(reader, ancestor[Posts.authorDid], ancestor[Posts.uri]),
                    ) and
                    not(brokenBefore)
            }
    return not(exists(hiding))
}

/**
 * The post in [posts] is served to anyone: its account is served, it carries no `!takedown` or `!hide`, nor does its account, and, when it
 * shares a proposal, that proposal is live and unlabelled. Who its tier admits isn't asked: a post nobody else may see still counts as
 * served, which keeps its quotes hidden rather than showing it unavailable.
 */
internal fun postServed(posts: Alias<Posts>): Op<Boolean> {
    val author = posts[Posts.authorDid]
    val uri = posts[Posts.uri]
    val proposal = posts[Posts.proposalUri]
    return served(null as Did?, author, uri, posts[Posts.cid]) and
        unhidden(null, author, uri) and
        (proposal.isNull() or
            exists(
                SegmentNameProposals.select(SegmentNameProposals.uri).where {
                    (SegmentNameProposals.uri eq proposal) and
                        (SegmentNameProposals.proposerDid eq author) and
                        (SegmentNameProposals.recordGone eq false) and
                        unlabelledProposal()
                },
            ))
}

/**
 * The quoted post's row for each quote among [rows], by the quote's URI, kept only when both [viewer] and the quote's author may see it
 * ([postRows], [postRowsFor]), in one query for the page. A quote without one shows its quoted post as unavailable (served to no one, or
 * the quoter lost access); the viewer is only shown a quote whose live quoted post they may see, so that's all it can mean.
 */
internal fun JdbcTransaction.quotedFor(rows: List<ResultRow>, viewer: Did?): Map<String, ResultRow> {
    val quotes = rows.filter { it[Posts.quotedUri] != null }.map { it[Posts.uri] }.distinct()
    if (quotes.isEmpty()) return emptyMap()
    val quoting = Posts.alias("quoting")
    val quote = quoting[Posts.uri]
    return Posts.join(quoting, JoinType.INNER, Posts.uri, quoting[Posts.quotedUri])
        .select(Posts.columns + quote)
        .where { (quote inList quotes) and postRows(viewer) and postRowsFor(quoting[Posts.authorDid]) }
        .associateBy { it[quote] }
}

/** The quoted post [row] as a quote shows it: its proposal, or a link when it's itself a quote; unavailable when its author has no view. */
internal fun quotedView(
    row: ResultRow,
    authors: Map<String, ProfileViewBasic>,
    proposals: (ResultRow) -> PostEmbedView.Proposal?,
): QuotedPost {
    val author = authors[row[Posts.authorDid]]
    val embed = row[Posts.quotedUri]?.let { QuotedPostEmbed.Link(AtUri(it)) } ?: proposals(row)
    return if (author == null || embed == null) {
        QuotedPost.Unavailable(AtUri(row[Posts.uri]))
    } else {
        QuotedPost.Post(
            uri = AtUri(row[Posts.uri]),
            cid = Cid(row[Posts.cid]),
            author = author,
            createdAt = Instant.fromEpochMilliseconds(row[Posts.sortAt]),
            text = row[Posts.text],
            visibility = row[Posts.visibility].takeIf { it != Visibility.PUBLIC },
            embed = embed,
        )
    }
}
