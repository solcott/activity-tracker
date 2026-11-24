package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.db.TrailDetailSuggestions
import io.github.solcott.activitytracker.appview.db.tx
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Clock
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update

/**
 * The trail detail adoption pass (segments/trail-detail-suggestions), run after the naming pass. For every segment and detail with open
 * suggestions it adopts at most one, on an active run segment only: open at least [TrailDetailRules.minOpenDays], with up votes reaching
 * [TrailDetailRules.upNeeded] for the segment's riders in the last 365 days and at least two-thirds of its votes up, the best score first
 * and then the earliest opened. Adopting closes the detail's other open suggestions as superseded. Otherwise a suggestion is rejected at
 * [TrailDetailRules.rejectScore] or expires after [TrailDetailRules.expiryDays]. Each closed suggestion keeps its final totals. It then
 * recomputes every segment's community values, which also catches labels that expired.
 */
class TrailDetailAdoption(
    private val db: Database,
    private val clock: Clock = Clock.System,
    private val rules: TrailDetailRules = TrailDetailRules(),
) {
    /** Runs the pass over every segment with open suggestions, or only [only], whose community values it then recomputes alone. */
    suspend fun update(only: String? = null) {
        val now = clock.now().toEpochMilliseconds()
        val since = LocalDate.ofInstant(Instant.ofEpochMilli(now), ZoneOffset.UTC).minusDays(RIDER_WINDOW_DAYS - 1).toString()
        db.tx {
            TrailDetailSuggestions.select(TrailDetailSuggestions.segmentUri, TrailDetailSuggestions.detail)
                .where {
                    val open = TrailDetailSuggestions.state eq ProposalState.OPEN
                    if (only == null) open else open and (TrailDetailSuggestions.segmentUri eq only)
                }
                .withDistinct()
                .map { it[TrailDetailSuggestions.segmentUri] to it[TrailDetailSuggestions.detail] }
                .forEach { (segment, detail) -> updateDetail(segment, detail, now, since) }
            if (only == null) recomputeAllTrailCommunities() else recomputeTrailCommunity(only)
        }
    }

    private fun JdbcTransaction.updateDetail(segment: String, detail: String, now: Long, since: String) {
        val open =
            TrailDetailSuggestions.select(TrailDetailSuggestions.uri, TrailDetailSuggestions.openedAt)
                .where {
                    (TrailDetailSuggestions.segmentUri eq segment) and
                        (TrailDetailSuggestions.detail eq detail) and
                        (TrailDetailSuggestions.state eq ProposalState.OPEN)
                }
                .associate { it[TrailDetailSuggestions.uri] to it[TrailDetailSuggestions.openedAt] }
        val counted =
            TrailDetailSuggestions.select(TrailDetailSuggestions.uri)
                .where {
                    (TrailDetailSuggestions.segmentUri eq segment) and
                        (TrailDetailSuggestions.detail eq detail) and
                        (TrailDetailSuggestions.state eq ProposalState.OPEN) and
                        unlabelledSuggestion()
                }
                .map { it[TrailDetailSuggestions.uri] }
                .toSet()
        val tallies = suggestionTallies(segment, counted)
        val adopted = if (suggestable(segment)) adoptable(segment, open, tallies, now, since) else null
        for ((uri, openedAt) in open) {
            val tally = tallies[uri] ?: Tally.NONE
            val state =
                when {
                    uri == adopted -> ProposalState.ADOPTED
                    adopted != null -> ProposalState.SUPERSEDED
                    uri in counted && tally.score <= rules.rejectScore -> ProposalState.REJECTED
                    now - openedAt >= rules.expiryDays * DAY_MS -> ProposalState.EXPIRED
                    else -> continue
                }
            TrailDetailSuggestions.update({ TrailDetailSuggestions.uri eq uri }) {
                it[TrailDetailSuggestions.state] = state
                it[closedAt] = now
                it[finalUp] = tally.up
                it[finalDown] = tally.down
            }
        }
    }

    /** The suggestion to adopt among [open] (each with when it opened), if any qualifies by [tallies]. */
    private fun JdbcTransaction.adoptable(
        segment: String,
        open: Map<String, Long>,
        tallies: Map<String, Tally>,
        now: Long,
        since: String,
    ): String? {
        val needed = rules.upNeeded(recentLocals(NamedPlace.Segment(segment), since))
        return tallies
            .filter { (uri, tally) ->
                val openedAt = open[uri] ?: return@filter false
                now - openedAt >= rules.minOpenDays * DAY_MS && tally.up >= needed && THIRDS * tally.up >= 2 * (tally.up + tally.down)
            }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Tally>> { it.value.score }.thenBy { open[it.key] }.thenBy { it.key })
            .firstOrNull()
            ?.key
    }

    private companion object {
        const val RIDER_WINDOW_DAYS = 365L
        const val THIRDS = 3
    }
}
