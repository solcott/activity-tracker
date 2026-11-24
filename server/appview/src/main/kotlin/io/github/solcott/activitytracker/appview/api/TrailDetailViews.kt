package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.TrailDetailSuggestions
import io.github.solcott.activitytracker.appview.segments.ProposalState
import io.github.solcott.activitytracker.appview.segments.Tally
import io.github.solcott.activitytracker.appview.segments.listedSuggestion
import io.github.solcott.activitytracker.appview.segments.suggestionVotes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.lexicon.app.TrailDetailSuggestionView
import io.github.solcott.activitytracker.lexicon.app.TrailDetailViewerVote
import io.github.solcott.activitytracker.lexicon.app.TrailDetailVoteDirection
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.selectAll

/** The most open suggestions `getSegment` lists, as its lexicon caps them. */
private const val MAX_SUGGESTION_ROWS = 200

/**
 * [segment]'s open trail detail suggestions the viewer may see (segments/trail-detail-suggestions), oldest first: not labelled and by a
 * served account ([listedSuggestion]: their own stay under `!hide`), and not by an athlete the viewer blocks, mutes or is blocked by.
 * Totals come from [suggestionVotes]; who voted is never served, except the viewer's own counted vote.
 */
internal fun JdbcTransaction.trailSuggestions(viewer: Did?, segment: String, avatars: AvatarUrls): List<TrailDetailSuggestionView> {
    val rows =
        TrailDetailSuggestions.selectAll()
            .where {
                (TrailDetailSuggestions.segmentUri eq segment) and
                    (TrailDetailSuggestions.state eq ProposalState.OPEN) and
                    listedSuggestion(viewer) and
                    notBlocked(viewer, TrailDetailSuggestions.authorDid) and
                    notMuted(viewer, TrailDetailSuggestions.authorDid)
            }
            .orderBy(TrailDetailSuggestions.openedAt to SortOrder.ASC, TrailDetailSuggestions.uri to SortOrder.ASC)
            .limit(MAX_SUGGESTION_ROWS)
            .toList()
    if (rows.isEmpty()) return emptyList()
    val uris = rows.map { it[TrailDetailSuggestions.uri] }
    val votes = suggestionVotes(segment, uris)
    val profiles = basicProfiles(rows.map { it[TrailDetailSuggestions.authorDid] }.distinct(), avatars)
    return rows.mapNotNull {
        val uri = it[TrailDetailSuggestions.uri]
        val author = it[TrailDetailSuggestions.authorDid]
        val value =
            runCatching { AtJson.decodeFromString(TrailAttributes.serializer(), it[TrailDetailSuggestions.value]) }.getOrNull()
                ?: return@mapNotNull null
        val counted = votes[uri].orEmpty()
        val tally = Tally(up = counted.values.count { v -> v.up }, down = counted.values.count { v -> !v.up })
        TrailDetailSuggestionView(
            uri = AtUri(uri),
            cid = Cid(it[TrailDetailSuggestions.cid]),
            detail = it[TrailDetailSuggestions.detail],
            value = value,
            author = profiles[author] ?: unknownProfile(author),
            openedAt = Instant.fromEpochMilliseconds(it[TrailDetailSuggestions.openedAt]),
            up = tally.up,
            down = tally.down,
            viewerVote =
                viewer
                    ?.let { me -> counted[me.value] }
                    ?.let { (voteUri, up) ->
                        TrailDetailViewerVote(AtUri(voteUri), if (up) TrailDetailVoteDirection.UP else TrailDetailVoteDirection.DOWN)
                    },
        )
    }
}
