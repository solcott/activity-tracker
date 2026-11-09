package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.DiveSites
import io.github.solcott.activitytracker.appview.db.Posts
import io.github.solcott.activitytracker.appview.db.SegmentNameProposals
import io.github.solcott.activitytracker.appview.db.SegmentNameVotes
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.segments.ProposalState
import io.github.solcott.activitytracker.appview.segments.Tally
import io.github.solcott.activitytracker.appview.segments.isDiveSiteUri
import io.github.solcott.activitytracker.appview.segments.riddenBy
import io.github.solcott.activitytracker.appview.segments.tallies
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.DiveSiteViewBasic
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostProposalStatus
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.QuotedPost
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentNameViewerVote
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import io.github.solcott.activitytracker.lexicon.app.SegmentViewBasic
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

// The read side of posts (feed/posts): which a viewer may see, and their views with the proposal each shares as it stands now.

/** The author of the post [uri]; `NotFound` when [viewer] may not see it ([postRows]), as if it didn't exist. */
internal fun JdbcTransaction.requireVisiblePost(uri: String, viewer: Did?): String =
    Posts.select(Posts.authorDid).where { (Posts.uri eq uri) and postRows(viewer) }.singleOrNull()?.get(Posts.authorDid)
        ?: throw postNotFound()

internal fun postNotFound() = notFound("Post not found")

/** A page of [rows] (already [postRows]) newest first by feed position, keyset on (sort_at, uri) with cursor `"<sort>::<uri>"`. */
internal fun JdbcTransaction.postPage(rows: Op<Boolean>, limit: Int, cursor: String?): List<ResultRow> =
    Posts.selectAll()
        .where { rows and keysetBefore(Posts.sortAt, Posts.uri, cursor) }
        .orderBy(Posts.sortAt to SortOrder.DESC, Posts.uri to SortOrder.DESC)
        .limit(limit)
        .toList()

/**
 * Views of post [rows] as [viewer] sees them, in order. A post whose proposal or the segment or site it names has gone since the rows were
 * read is left out. Open proposals carry live totals and the viewer's vote on their current version; closed ones their final totals. A
 * quote carries its quoted post one level deep ([quotedFor]).
 */
internal fun JdbcTransaction.postViews(rows: List<ResultRow>, viewer: Did?, avatars: AvatarUrls): List<PostView> {
    if (rows.isEmpty()) return emptyList()
    val quoted = quotedFor(rows, viewer)
    val proposals = proposalEmbeds(rows + quoted.values, viewer)
    val authors = basicProfiles((rows + quoted.values).map { it[Posts.authorDid] }, avatars)
    val labels = labelViews(rows.associate { it[Posts.uri] to it[Posts.cid] })
    val counts = postCounts(rows.map { it[Posts.uri] }, viewer)
    return rows.mapNotNull { row ->
        val quotes = row[Posts.quotedUri]
        val embed =
            if (quotes != null) {
                val shown = quoted[row[Posts.uri]]?.let { quotedView(it, authors, proposals) }
                PostEmbedView.Quote(shown ?: QuotedPost.Unavailable(AtUri(quotes)))
            } else {
                proposals(row) ?: return@mapNotNull null
            }
        val author = authors[row[Posts.authorDid]] ?: return@mapNotNull null
        PostView(
            uri = AtUri(row[Posts.uri]),
            cid = Cid(row[Posts.cid]),
            author = author,
            embed = embed,
            createdAt = Instant.fromEpochMilliseconds(row[Posts.sortAt]),
            indexedAt = Instant.fromEpochMilliseconds(row[Posts.indexedAt]),
            text = row[Posts.text],
            visibility = row[Posts.visibility].takeIf { it != Visibility.PUBLIC },
            labels = labels[row[Posts.uri]],
            likeCount = counts.likes(row[Posts.uri]),
            commentCount = counts.comments(row[Posts.uri]),
            repostCount = counts.reposts(row[Posts.uri]),
            quoteCount = counts.quotes(row[Posts.uri]),
            viewer = counts.viewer(row[Posts.uri]),
        )
    }
}

/**
 * The proposal embed of each of the posts [rows] that shares one, as [viewer] sees it: null for a quote, and for a post whose proposal or
 * the segment or site it names has gone since the rows were read.
 */
private fun JdbcTransaction.proposalEmbeds(rows: List<ResultRow>, viewer: Did?): (ResultRow) -> PostEmbedView.Proposal? {
    val proposals =
        SegmentNameProposals.selectAll()
            .where { SegmentNameProposals.uri inList rows.mapNotNull { it[Posts.proposalUri] }.distinct() }
            .associateBy { it[SegmentNameProposals.uri] }
    val bySubject = proposals.values.groupBy { it[SegmentNameProposals.segmentUri] }
    val openTallies =
        bySubject
            .flatMap { (subject, its) ->
                tallies(subject, its.filter { it[SegmentNameProposals.state] == ProposalState.OPEN }.map { it[SegmentNameProposals.uri] })
                    .entries
            }
            .associate { it.key to it.value }
    val voters = viewer?.let { riddenBy(it.value, bySubject.keys) }.orEmpty()
    val own =
        viewer
            ?.let { me ->
                SegmentNameVotes.selectAll()
                    .where { (SegmentNameVotes.voterDid eq me.value) and (SegmentNameVotes.proposalUri inList proposals.keys) }
                    .orderBy(SegmentNameVotes.indexedAt to SortOrder.ASC, SegmentNameVotes.uri to SortOrder.ASC)
                    .associateBy { it[SegmentNameVotes.proposalUri] }
            }
            .orEmpty()
    val segments = segmentsAmong(bySubject.keys)
    val sites = sitesAmong(bySubject.keys)
    return { row ->
        proposals[row[Posts.proposalUri]]?.let { proposal ->
            val subject = proposal[SegmentNameProposals.segmentUri]
            val segment = segments[subject]
            val site = sites[subject]
            if (segment == null && site == null) null
            else
                proposalEmbed(proposal, openTallies, own, segment, site).let {
                    // The proposer's own vote never counts (segments/segment-names), so they get no buttons.
                    it.copy(canVote = it.status == PostProposalStatus.OPEN && subject in voters && viewer?.value != row[Posts.authorDid])
                }
        }
    }
}

/** Brief views of the segments among [subjects], by URI. */
private fun segmentsAmong(subjects: Collection<String>): Map<String, SegmentViewBasic> =
    Segments.selectAll()
        .where { Segments.uri inList subjects.filterNot(::isDiveSiteUri) }
        .associate {
            it[Segments.uri] to
                SegmentViewBasic(
                    AtUri(it[Segments.uri]),
                    it[Segments.currentName],
                    it[Segments.sport],
                    it[Segments.distanceM],
                    status = it[Segments.status],
                    kind = it[Segments.kind].takeIf { k -> k != SegmentKind.RUN },
                )
        }

/** Brief views of the dive sites among [subjects], by URI. */
private fun sitesAmong(subjects: Collection<String>): Map<String, DiveSiteViewBasic> =
    DiveSites.selectAll()
        .where { DiveSites.uri inList subjects.filter(::isDiveSiteUri) }
        .associate { it[DiveSites.uri] to DiveSiteViewBasic(AtUri(it[DiveSites.uri]), it[DiveSites.currentName], it[DiveSites.status]) }

/**
 * The embed of a post sharing [proposal]: live totals ([openTallies]) while it's open, its final ones once closed, and the viewer's vote
 * ([own]) only on its current version. Voting is left to the caller.
 */
private fun proposalEmbed(
    proposal: ResultRow,
    openTallies: Map<String, Tally>,
    own: Map<String, ResultRow>,
    segment: SegmentViewBasic?,
    site: DiveSiteViewBasic?,
): PostEmbedView.Proposal {
    val uri = proposal[SegmentNameProposals.uri]
    val cid = proposal[SegmentNameProposals.cid]
    val state = proposal[SegmentNameProposals.state]
    val tally =
        if (state == ProposalState.OPEN) openTallies[uri] ?: Tally.NONE
        else Tally(proposal[SegmentNameProposals.finalUp] ?: 0, proposal[SegmentNameProposals.finalDown] ?: 0)
    val vote = own[uri]?.takeIf { it[SegmentNameVotes.proposalCid] == cid }
    return PostEmbedView.Proposal(
        proposal = StrongRef(AtUri(uri), Cid(cid)),
        name = proposal[SegmentNameProposals.name],
        status = statusOf(state),
        openedAt = Instant.fromEpochMilliseconds(proposal[SegmentNameProposals.openedAt]),
        up = tally.up,
        down = tally.down,
        segment = segment,
        diveSite = site,
        viewerVote =
            vote?.let {
                SegmentNameViewerVote(
                    uri = AtUri(it[SegmentNameVotes.uri]),
                    direction = if (it[SegmentNameVotes.up]) SegmentNameVoteDirection.UP else SegmentNameVoteDirection.DOWN,
                )
            },
    )
}

private fun statusOf(state: String): String =
    when (state) {
        ProposalState.OPEN -> PostProposalStatus.OPEN
        ProposalState.ADOPTED -> PostProposalStatus.ADOPTED
        ProposalState.REJECTED -> PostProposalStatus.REJECTED
        ProposalState.EXPIRED -> PostProposalStatus.EXPIRED
        else -> PostProposalStatus.SUPERSEDED
    }
