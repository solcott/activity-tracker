package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.TrailDetailSuggestions
import io.github.solcott.activitytracker.appview.db.TrailDetailVotes
import io.github.solcott.activitytracker.appview.segments.ProposalState
import io.github.solcott.activitytracker.appview.segments.TRAIL_SUGGESTIONS
import io.github.solcott.activitytracker.appview.segments.TrailDetailRules
import io.github.solcott.activitytracker.appview.segments.deleteProposals
import io.github.solcott.activitytracker.appview.segments.deleteProposalsBy
import io.github.solcott.activitytracker.appview.segments.maySuggest
import io.github.solcott.activitytracker.appview.segments.normalizedValue
import io.github.solcott.activitytracker.appview.segments.valueTaken
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.TrailDetail
import io.github.solcott.activitytracker.lexicon.app.TrailDetailSuggestionRecord
import io.github.solcott.activitytracker.lexicon.app.TrailDetailVoteDirection
import io.github.solcott.activitytracker.lexicon.app.TrailDetailVoteRecord
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

// Indexing of `feed.trailDetailSuggestion` (from repos) and `feed.trailDetailVote` (from interactions spaces),
// segments/trail-detail-suggestions.

/**
 * A suggestion from [author]'s repo. A new one is accepted only when it names an indexed segment, its value is one a rider may suggest for
 * its detail ([TrailDetail.isSuggestion]) and new ([valueTaken]), and [author] [maySuggest]; anything else is dropped like an undecodable
 * record, for good. A rewrite of a stored suggestion is an edit: ignored unless it is open, names the same segment and detail, and its
 * value changed to a valid new one, which then takes the new version, so every earlier vote stops counting, and restarts its time open.
 */
@Suppress("LongParameterList", "ReturnCount") // The record, and where it's from; each reason an edit is ignored.
internal fun JdbcTransaction.upsertTrailSuggestion(
    json: JsonObject,
    author: Did,
    uri: AtUri,
    cid: String,
    rules: TrailDetailRules,
    importer: Did?,
    now: Long,
) {
    val record = AtJson.decodeFromJsonElement(TrailDetailSuggestionRecord.serializer(), json)
    val segment = record.segment.value
    val detail = record.detail
    require(TrailDetail.isSuggestion(detail, record.value)) { "a value no rider may suggest for $detail" }
    val value = checkNotNull(normalizedValue(detail, record.value))
    val stored = TrailDetailSuggestions.selectAll().where { TrailDetailSuggestions.uri eq uri.value }.firstOrNull()
    if (stored != null) {
        if (stored[TrailDetailSuggestions.state] != ProposalState.OPEN) return
        if (stored[TrailDetailSuggestions.segmentUri] != segment || stored[TrailDetailSuggestions.detail] != detail) return
        if (stored[TrailDetailSuggestions.value] == value || valueTaken(segment, detail, value, importer, except = uri.value)) return
        TrailDetailSuggestions.update({ TrailDetailSuggestions.uri eq uri.value }) {
            it[TrailDetailSuggestions.cid] = cid
            it[TrailDetailSuggestions.value] = value
            it[openedAt] = now
        }
        return
    }
    require(record.segment.collection == Nsid.FEED_SEGMENT) { "a suggestion for ${record.segment}" }
    require(maySuggest(segment, detail, author.value, now, rules)) { "an author who may not suggest $detail for $segment" }
    require(!valueTaken(segment, detail, value, importer)) { "a value $segment already has or is offered" }
    TrailDetailSuggestions.insert {
        it[TrailDetailSuggestions.uri] = uri.value
        it[TrailDetailSuggestions.cid] = cid
        it[segmentUri] = segment
        it[authorDid] = author.value
        it[TrailDetailSuggestions.detail] = detail
        it[TrailDetailSuggestions.value] = value
        it[state] = ProposalState.OPEN
        it[openedAt] = now
    }
}

/**
 * Deleted suggestions: an adopted one keeps its row ([TrailDetailSuggestions.recordGone]), so the segment keeps the value; any other goes
 * with its votes (an open one is withdrawn).
 */
internal fun JdbcTransaction.deleteTrailSuggestions(uris: Collection<String>) = deleteProposals(TRAIL_SUGGESTIONS, uris)

/** [author]'s suggestions, votes and confirmations (every row in the votes table is theirs as voter), for a deleted account. */
internal fun JdbcTransaction.deleteTrailDetailsBy(author: Did) = deleteProposalsBy(TRAIL_SUGGESTIONS, author.value)

/**
 * A vote or confirmation from [author]'s interactions space. A vote names a suggestion at a version and is up or down; a confirmation names
 * a segment, a detail and its value, stored normalized, and is still or changed. Its first indexing time is kept on a replay: of a voter's
 * votes on one subject the latest counts, and a client changes its vote by rewriting the same record. Anything else removes the vote.
 */
internal fun JdbcTransaction.upsertTrailVote(json: JsonObject, author: Did, uri: AtUri, now: Long) {
    val record = AtJson.decodeFromJsonElement(TrailDetailVoteRecord.serializer(), json)
    val subject = record.subject
    val onSuggestion =
        subject != null &&
            subject.uri.collection == Nsid.FEED_TRAIL_DETAIL_SUGGESTION &&
            record.direction in setOf(TrailDetailVoteDirection.UP, TrailDetailVoteDirection.DOWN)
    val confirmed = record.value?.takeIf { subject == null }?.let { value -> record.detail?.let { normalizedValue(it, value) } }
    val confirmation =
        confirmed != null &&
            record.segment?.collection == Nsid.FEED_SEGMENT &&
            record.detail in TrailDetail.all &&
            record.direction in setOf(TrailDetailVoteDirection.STILL, TrailDetailVoteDirection.CHANGED)
    if (!onSuggestion && !confirmation) {
        TrailDetailVotes.deleteWhere { TrailDetailVotes.uri eq uri.value }
        return
    }
    TrailDetailVotes.upsert(onUpdateExclude = listOf(TrailDetailVotes.indexedAt)) {
        it[TrailDetailVotes.uri] = uri.value
        it[voterDid] = author.value
        it[suggestionUri] = subject?.uri?.value?.takeIf { onSuggestion }
        it[suggestionCid] = subject?.cid?.value?.takeIf { onSuggestion }
        it[segmentUri] = record.segment?.value?.takeIf { confirmation }
        it[detail] = record.detail?.takeIf { confirmation }
        it[value] = confirmed?.takeIf { confirmation }
        it[direction] = record.direction
        it[indexedAt] = now
    }
}

internal fun JdbcTransaction.deleteTrailVote(uri: AtUri) {
    TrailDetailVotes.deleteWhere { TrailDetailVotes.uri eq uri.value }
}
