package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.TrailDetailSuggestions
import io.github.solcott.activitytracker.appview.segments.TRAIL_SUGGESTIONS
import io.github.solcott.activitytracker.appview.segments.openProposalRows
import io.github.solcott.activitytracker.appview.segments.suggestionVotes
import io.github.solcott.activitytracker.appview.segments.tally
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.lexicon.app.TrailDetailSuggestionView
import io.github.solcott.activitytracker.lexicon.app.TrailDetailViewerVote
import io.github.solcott.activitytracker.lexicon.app.TrailDetailVoteDirection
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

/** The most open suggestions `getSegment` lists, as its lexicon caps them. */
private const val MAX_SUGGESTION_ROWS = 200

/**
 * [segment]'s open trail detail suggestions the viewer may see (segments/trail-detail-suggestions), oldest first ([ProposalTable.shownTo]):
 * not labelled and by a served account (their own stay under `!hide`), and not by an athlete the viewer blocks, mutes or is blocked by.
 * Totals come from [suggestionVotes]; who voted is never served, except the viewer's own counted vote.
 */
internal fun JdbcTransaction.trailSuggestions(viewer: Did?, segment: String, avatars: AvatarUrls): List<TrailDetailSuggestionView> {
    val rows = openProposalRows(TRAIL_SUGGESTIONS, segment, TRAIL_SUGGESTIONS.shownTo(viewer), MAX_SUGGESTION_ROWS)
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
        val tally = counted.tally()
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
