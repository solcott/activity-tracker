package io.github.solcott.activitytracker.appview.segments

import kotlin.time.Clock
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * The naming pass (segments/segment-names), run by [SegmentMatcher] after each status pass: the shared [ProposalAdoption] over name
 * proposals with [SegmentNameRules]. For every segment with open proposals it adopts at most one, on an active segment with no pinned name
 * only; adopting recomputes the segment's current name. It then recomputes every adopted segment's current name, which also catches labels
 * that expired.
 */
class SegmentNaming(db: Database, clock: Clock = Clock.System, rules: SegmentNameRules = SegmentNameRules()) {
    private val pass =
        ProposalAdoption(
            db = db,
            clock = clock,
            kind = NAME_PROPOSALS,
            rules = rules,
            // A pinned name holds the segment's name, so nothing is adopted there while it's pinned (segments/segment-pins).
            mayAdopt = { segment -> nameable(NamedPlace.of(segment)) && !hasNamePin(segment) },
            adopted = { segment -> recomputeCurrentName(segment) },
            recompute = { only -> if (only == null) recomputeAllAdoptedNames() else recomputeCurrentName(only) },
        )

    /**
     * Runs the pass over every segment and dive site with open proposals, or only [only]: then it recomputes [only]'s current name instead
     * of every adopted one.
     */
    suspend fun update(only: String? = null) = pass.update(only)
}
