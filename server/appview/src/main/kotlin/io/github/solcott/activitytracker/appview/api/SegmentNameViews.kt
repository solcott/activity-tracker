package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.SegmentNameProposals
import io.github.solcott.activitytracker.appview.db.SegmentNameVotes
import io.github.solcott.activitytracker.appview.segments.ProposalState
import io.github.solcott.activitytracker.appview.segments.Tally
import io.github.solcott.activitytracker.appview.segments.tallies
import io.github.solcott.activitytracker.appview.segments.unlabelledProposal
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentNameAdoption
import io.github.solcott.activitytracker.lexicon.app.SegmentNameProposalView
import io.github.solcott.activitytracker.lexicon.app.SegmentNameViewerVote
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.selectAll

// The naming parts of `getSegment` (segments/segment-names): a segment's name history and its open proposals as a viewer may see them.

/** The most adopted names or open proposals one `getSegment` lists, as the lexicon allows. */
private const val MAX_NAME_ROWS = 100

/**
 * [segment]'s adopted names in effect, newest first, each with its final totals and its proposer, unless the proposal record is gone or the
 * viewer and the proposer block each other.
 */
internal fun JdbcTransaction.nameHistory(viewer: Did?, segment: String, avatars: AvatarUrls): List<SegmentNameAdoption> {
    val rows =
        SegmentNameProposals.selectAll()
            .where {
                (SegmentNameProposals.segmentUri eq segment) and
                    (SegmentNameProposals.state eq ProposalState.ADOPTED) and
                    unlabelledProposal()
            }
            .orderBy(SegmentNameProposals.closedAt to SortOrder.DESC, SegmentNameProposals.uri to SortOrder.DESC)
            .limit(MAX_NAME_ROWS)
            .toList()
    val shown =
        rows
            .filter { !it[SegmentNameProposals.recordGone] }
            .map { it[SegmentNameProposals.proposerDid] }
            .distinct()
            .filter { viewer == null || viewer.value == it || !isBlocked(viewer.value, it) }
    val profiles = basicProfiles(shown, avatars)
    return rows.map {
        val proposer = it[SegmentNameProposals.proposerDid]
        SegmentNameAdoption(
            name = it[SegmentNameProposals.name],
            adoptedAt = Instant.fromEpochMilliseconds(it[SegmentNameProposals.closedAt] ?: it[SegmentNameProposals.openedAt]),
            up = it[SegmentNameProposals.finalUp] ?: 0,
            down = it[SegmentNameProposals.finalDown] ?: 0,
            proposer = if (proposer in shown) profiles[proposer] ?: unknownProfile(proposer) else null,
        )
    }
}

/**
 * [segment]'s open proposals the viewer may see, oldest first: not labelled, by a served account, and not by an athlete the viewer blocks,
 * mutes or is blocked by. Totals come from [tallies]; who voted is never served, except the viewer's own vote on the version listed.
 */
internal fun JdbcTransaction.nameProposals(viewer: Did?, segment: String, avatars: AvatarUrls): List<SegmentNameProposalView> {
    val rows =
        SegmentNameProposals.selectAll()
            .where {
                (SegmentNameProposals.segmentUri eq segment) and
                    (SegmentNameProposals.state eq ProposalState.OPEN) and
                    unlabelledProposal() and
                    served(viewer, SegmentNameProposals.proposerDid) and
                    notBlocked(viewer, SegmentNameProposals.proposerDid) and
                    notMuted(viewer, SegmentNameProposals.proposerDid)
            }
            .orderBy(SegmentNameProposals.openedAt to SortOrder.ASC, SegmentNameProposals.uri to SortOrder.ASC)
            .limit(MAX_NAME_ROWS)
            .toList()
    if (rows.isEmpty()) return emptyList()
    val uris = rows.map { it[SegmentNameProposals.uri] }
    val tallies = tallies(segment, uris)
    val profiles = basicProfiles(rows.map { it[SegmentNameProposals.proposerDid] }.distinct(), avatars)
    val own =
        viewer
            ?.let { me ->
                SegmentNameVotes.selectAll()
                    .where { (SegmentNameVotes.voterDid eq me.value) and (SegmentNameVotes.proposalUri inList uris) }
                    .orderBy(SegmentNameVotes.indexedAt to SortOrder.ASC, SegmentNameVotes.uri to SortOrder.ASC)
                    .associateBy { it[SegmentNameVotes.proposalUri] }
            }
            .orEmpty()
    return rows.map {
        val uri = it[SegmentNameProposals.uri]
        val cid = it[SegmentNameProposals.cid]
        val proposer = it[SegmentNameProposals.proposerDid]
        val tally = tallies[uri] ?: Tally.NONE
        SegmentNameProposalView(
            uri = AtUri(uri),
            cid = Cid(cid),
            name = it[SegmentNameProposals.name],
            proposer = profiles[proposer] ?: unknownProfile(proposer),
            openedAt = Instant.fromEpochMilliseconds(it[SegmentNameProposals.openedAt]),
            up = tally.up,
            down = tally.down,
            viewerVote =
                own[uri]
                    ?.takeIf { vote -> vote[SegmentNameVotes.proposalCid] == cid }
                    ?.let { vote ->
                        SegmentNameViewerVote(
                            uri = AtUri(vote[SegmentNameVotes.uri]),
                            direction = if (vote[SegmentNameVotes.up]) SegmentNameVoteDirection.UP else SegmentNameVoteDirection.DOWN,
                        )
                    },
        )
    }
}

private fun unknownProfile(did: String) = ProfileViewBasic(Did(did), Handle(UNKNOWN_HANDLE))
