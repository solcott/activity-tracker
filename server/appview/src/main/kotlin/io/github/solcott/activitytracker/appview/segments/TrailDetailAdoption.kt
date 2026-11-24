package io.github.solcott.activitytracker.appview.segments

import kotlin.time.Clock
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * The trail detail adoption pass (segments/trail-detail-suggestions), run after the naming pass: the shared [ProposalAdoption] over
 * suggestions with [TrailDetailRules], per segment and detail. For every segment and detail with open suggestions it adopts at most one, on
 * an active run segment only. It then recomputes every segment's community values, which also catches labels that expired.
 */
class TrailDetailAdoption(db: Database, clock: Clock = Clock.System, rules: TrailDetailRules = TrailDetailRules()) {
    private val pass =
        ProposalAdoption(
            db = db,
            clock = clock,
            kind = TRAIL_SUGGESTIONS,
            rules = rules,
            mayAdopt = { segment -> suggestable(segment) },
            adopted = {},
            recompute = { only -> if (only == null) recomputeAllTrailCommunities() else recomputeTrailCommunity(only) },
        )

    /** Runs the pass over every segment with open suggestions, or only [only], whose community values it then recomputes alone. */
    suspend fun update(only: String? = null) = pass.update(only)
}
