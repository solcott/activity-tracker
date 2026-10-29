package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.SegmentNameProposals
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Clock
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update

/**
 * The naming pass (segments/segment-names), run by [SegmentMatcher] after each status pass. For every segment with open proposals it adopts
 * at most one, on an active segment only: open at least [SegmentNameRules.minOpenDays], with up votes reaching [SegmentNameRules.upNeeded]
 * for the segment's riders in the last 365 days and at least two-thirds of its votes up, the best score first and then the earliest opened.
 * Adopting closes the segment's other open proposals as superseded. Otherwise a proposal is rejected at [SegmentNameRules.rejectScore] or
 * expires after [SegmentNameRules.expiryDays]. Each closed proposal keeps its final totals. It then recomputes every adopted segment's
 * current name, which also catches labels that expired.
 */
class SegmentNaming(
    private val db: Database,
    private val clock: Clock = Clock.System,
    private val rules: SegmentNameRules = SegmentNameRules(),
) {
    suspend fun update() {
        val now = clock.now().toEpochMilliseconds()
        val since = LocalDate.ofInstant(Instant.ofEpochMilli(now), ZoneOffset.UTC).minusDays(RIDER_WINDOW_DAYS - 1).toString()
        db.tx {
            SegmentNameProposals.select(SegmentNameProposals.segmentUri)
                .where { SegmentNameProposals.state eq ProposalState.OPEN }
                .withDistinct()
                .map { it[SegmentNameProposals.segmentUri] }
                .forEach { updateSegment(it, now, since) }
            recomputeAllAdoptedNames()
        }
    }

    private fun JdbcTransaction.updateSegment(segment: String, now: Long, since: String) {
        val open =
            SegmentNameProposals.select(SegmentNameProposals.uri, SegmentNameProposals.openedAt)
                .where { (SegmentNameProposals.segmentUri eq segment) and (SegmentNameProposals.state eq ProposalState.OPEN) }
                .associate { it[SegmentNameProposals.uri] to it[SegmentNameProposals.openedAt] }
        val counted =
            SegmentNameProposals.select(SegmentNameProposals.uri)
                .where {
                    (SegmentNameProposals.segmentUri eq segment) and
                        (SegmentNameProposals.state eq ProposalState.OPEN) and
                        unlabelledProposal()
                }
                .map { it[SegmentNameProposals.uri] }
                .toSet()
        val tallies = tallies(segment, counted)
        val adopted = if (isActive(segment)) adoptable(segment, open, tallies, now, since) else null
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
            SegmentNameProposals.update({ SegmentNameProposals.uri eq uri }) {
                it[SegmentNameProposals.state] = state
                it[closedAt] = now
                it[finalUp] = tally.up
                it[finalDown] = tally.down
            }
        }
        if (adopted != null) recomputeCurrentName(segment)
    }

    private fun JdbcTransaction.isActive(segment: String) =
        Segments.select(Segments.status).where { Segments.uri eq segment }.firstOrNull()?.get(Segments.status) == SegmentStatus.ACTIVE

    /** The proposal to adopt among [open] (each with when it opened), if any qualifies by [tallies]. */
    private fun JdbcTransaction.adoptable(
        segment: String,
        open: Map<String, Long>,
        tallies: Map<String, Tally>,
        now: Long,
        since: String,
    ): String? {
        val riders =
            SegmentEfforts.select(SegmentEfforts.authorDid)
                .where { (SegmentEfforts.segmentUri eq segment) and (SegmentEfforts.startedOn greaterEq since) }
                .withDistinct()
                .count()
        val needed = rules.upNeeded(riders)
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
