package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.SegmentNameProposals
import io.github.solcott.activitytracker.appview.db.SegmentNameVotes
import io.github.solcott.activitytracker.appview.segments.ProposalState
import io.github.solcott.activitytracker.appview.segments.SegmentNameRules
import io.github.solcott.activitytracker.appview.segments.mayPropose
import io.github.solcott.activitytracker.appview.segments.nameTaken
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.SegmentNameProposalRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteRecord
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

// Indexing of `feed.segmentNameProposal` (from repos) and `feed.segmentNameVote` (from interactions spaces), segments/segment-names.

/**
 * A name proposal from [author]'s repo. A new one is accepted only when its name is valid and new ([nameTaken]) and [author] [mayPropose]
 * for an indexed segment; anything else is dropped like an undecodable record, for good. A rewrite of a stored proposal is an edit: ignored
 * unless the proposal is open, it names the same segment and its name changed to a valid new one, which then takes the new version, so
 * every earlier vote stops counting, and restarts its time open.
 */
@Suppress("LongParameterList", "ReturnCount") // The record, and where it's from; each reason an edit is ignored.
internal fun JdbcTransaction.upsertNameProposal(
    json: JsonObject,
    author: Did,
    uri: AtUri,
    cid: String,
    rules: SegmentNameRules,
    now: Long,
) {
    val record = AtJson.decodeFromJsonElement(SegmentNameProposalRecord.serializer(), json)
    val name = validName(record.name)
    val segment = record.segment.value
    val stored = SegmentNameProposals.selectAll().where { SegmentNameProposals.uri eq uri.value }.firstOrNull()
    if (stored != null) {
        if (stored[SegmentNameProposals.state] != ProposalState.OPEN) return
        if (stored[SegmentNameProposals.segmentUri] != segment || stored[SegmentNameProposals.name] == name) return
        if (nameTaken(segment, name, except = uri.value)) return
        SegmentNameProposals.update({ SegmentNameProposals.uri eq uri.value }) {
            it[SegmentNameProposals.cid] = cid
            it[SegmentNameProposals.name] = name.take(MAX_NAME_LENGTH)
            it[openedAt] = now
        }
        return
    }
    require(record.segment.collection == Nsid.FEED_SEGMENT) { "a proposal for ${record.segment}" }
    require(mayPropose(segment, author.value, now, rules)) { "a proposer who may not propose for $segment" }
    require(!nameTaken(segment, name)) { "a name $segment already has or is offered" }
    SegmentNameProposals.insert {
        it[SegmentNameProposals.uri] = uri.value
        it[SegmentNameProposals.cid] = cid
        it[segmentUri] = segment
        it[proposerDid] = author.value
        it[SegmentNameProposals.name] = name.take(MAX_NAME_LENGTH)
        it[state] = ProposalState.OPEN
        it[openedAt] = now
    }
}

/**
 * Deleted proposals: an adopted one keeps its row without its proposer ([SegmentNameProposals.recordGone]), so the segment keeps the name;
 * any other goes with its votes (an open one is withdrawn).
 */
internal fun JdbcTransaction.deleteNameProposals(uris: Collection<String>) {
    if (uris.isEmpty()) return
    SegmentNameProposals.update({ (SegmentNameProposals.uri inList uris) and (SegmentNameProposals.state eq ProposalState.ADOPTED) }) {
        it[recordGone] = true
    }
    val gone =
        SegmentNameProposals.select(SegmentNameProposals.uri)
            .where { (SegmentNameProposals.uri inList uris) and not(SegmentNameProposals.state eq ProposalState.ADOPTED) }
            .map { it[SegmentNameProposals.uri] }
    if (gone.isEmpty()) return
    SegmentNameVotes.deleteWhere { proposalUri inList gone }
    SegmentNameProposals.deleteWhere { SegmentNameProposals.uri inList gone }
}

/** [author]'s proposals and votes, for a deleted account. */
internal fun JdbcTransaction.deleteSegmentNamesBy(author: Did) {
    deleteNameProposals(
        SegmentNameProposals.select(SegmentNameProposals.uri)
            .where { SegmentNameProposals.proposerDid eq author.value }
            .map { it[SegmentNameProposals.uri] }
    )
    SegmentNameVotes.deleteWhere { voterDid eq author.value }
}

/**
 * A vote from [author]'s interactions space, on a proposal at the version its subject names. Its first indexing time is kept on a replay:
 * of a voter's votes on one proposal the latest counts, and a client changes its vote by rewriting the same record. An unknown direction
 * removes the vote.
 */
internal fun JdbcTransaction.upsertNameVote(json: JsonObject, author: Did, uri: AtUri, now: Long) {
    val record = AtJson.decodeFromJsonElement(SegmentNameVoteRecord.serializer(), json)
    val up =
        when (record.direction) {
            SegmentNameVoteDirection.UP -> true
            SegmentNameVoteDirection.DOWN -> false
            else -> null
        }
    if (up == null || record.subject.uri.collection != Nsid.FEED_SEGMENT_NAME_PROPOSAL) {
        SegmentNameVotes.deleteWhere { SegmentNameVotes.uri eq uri.value }
        return
    }
    SegmentNameVotes.upsert(onUpdateExclude = listOf(SegmentNameVotes.indexedAt)) {
        it[SegmentNameVotes.uri] = uri.value
        it[voterDid] = author.value
        it[proposalUri] = record.subject.uri.value
        it[proposalCid] = record.subject.cid.value
        it[SegmentNameVotes.up] = up
        it[indexedAt] = now
    }
}

internal fun JdbcTransaction.deleteNameVote(uri: AtUri) {
    SegmentNameVotes.deleteWhere { SegmentNameVotes.uri eq uri.value }
}
