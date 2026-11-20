package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.SegmentNameProposals
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.LIVE_STATUSES
import io.github.solcott.activitytracker.appview.index.recheckDuplicatesOf
import io.github.solcott.activitytracker.appview.index.rederiveGroups
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import kotlin.time.Clock
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update

/** What a rematch did. */
sealed interface Rematch {
    data object Queued : Rematch

    data object NotFound : Rematch

    data class NotLive(val status: String) : Rematch
}

/** A segment's status before and after a one-segment status pass. */
data class StatusChange(val before: String, val after: String)

/** A one-segment naming pass's effect: the state each proposal that changed state moved to, by proposal URI. */
data class NamingChange(val changed: Map<String, String>)

/**
 * The segment steps of the status pass (segments/segment-records) and of the naming pass (segments/segment-names), for every segment or
 * just some: the hourly passes [SegmentMatcher] runs and the admin site's one-segment passes (admin/segment-tools) are this code, so they
 * can't drift. Like the hourly passes, they take no indexer lock: their updates are guarded (`status = proposed`, [noFixedStatusPin]), so a
 * concurrent pass or upsert at worst repeats the work.
 */
@Suppress("LongParameterList") // The passes' collaborators and configuration.
class SegmentPasses(
    private val db: Database,
    private val jobs: SegmentJobs,
    private val clock: Clock = Clock.System,
    private val activeAthletes: Int = SegmentMatcher.DEFAULT_ACTIVE_ATHLETES,
    names: SegmentNameRules = SegmentNameRules(),
    /** The segment importer's account, whose segments are live as `active` (segments/segment-records). */
    private val importer: Did? = null,
) {
    private val naming = SegmentNaming(db, clock, names)

    /** The status pass's segment step over every segment, or only [only], in one transaction. */
    suspend fun updateStatuses(only: Collection<String>? = null) {
        val now = clock.now().toEpochMilliseconds()
        db.tx { updateSegmentStatuses(now, only) }
    }

    /** The status pass for the segment [uri] alone, with its status before and after; null when it isn't indexed. */
    suspend fun updateStatusOf(uri: String): StatusChange? {
        val now = clock.now().toEpochMilliseconds()
        return db.tx {
            val before = statusOf(uri) ?: return@tx null
            updateSegmentStatuses(now, listOf(uri))
            StatusChange(before, checkNotNull(statusOf(uri)))
        }
    }

    /**
     * Promotes every proposed segment (of [only], when given) that already has enough athletes, then archives those still proposed
     * [SegmentMatcher.ARCHIVE_AFTER] after they were first indexed and checks again the segments that duplicated them. Archived segments
     * keep their efforts. Fixed status pins are left alone.
     */
    internal fun JdbcTransaction.updateSegmentStatuses(now: Long, only: Collection<String>?) {
        if (only != null && only.isEmpty()) return
        fun narrowed(op: Op<Boolean>) = if (only == null) op else op and (Segments.uri inList only)
        promoteSegments(
            Segments.select(Segments.uri).where { narrowed(Segments.status eq SegmentStatus.PROPOSED) }.map { it[Segments.uri] },
            activeAthletes,
        )
        val stale =
            Segments.select(Segments.uri)
                .where {
                    narrowed(
                        (Segments.status eq SegmentStatus.PROPOSED) and
                            (Segments.firstIndexedAt less now - SegmentMatcher.ARCHIVE_AFTER.inWholeMilliseconds) and
                            noFixedStatusPin(),
                    )
                }
                .map { it[Segments.uri] }
        if (stale.isNotEmpty()) {
            Segments.update({ Segments.uri inList stale }) { it[status] = SegmentStatus.ARCHIVED }
            recheckDuplicatesOf(stale, jobs, now, importer)
            rederiveGroups(stale)
        }
    }

    /** The naming pass over every segment and dive site with open proposals, or only [only] (whose current name it then recomputes). */
    suspend fun updateNames(only: String? = null) = naming.update(only)

    /** The naming pass for [uri] alone, with the proposals whose state it changed. */
    suspend fun updateNameOf(uri: String): NamingChange {
        val before = proposalStates(uri)
        updateNames(uri)
        val after = proposalStates(uri)
        return NamingChange(after.filter { (proposal, state) -> before[proposal] != state })
    }

    /**
     * Recomputes [uri]'s effort-derived elevation now and queues its terrain sampling, which [SegmentJobs.enqueueElevation] does only with
     * terrain tiles; false when it isn't indexed.
     */
    suspend fun recomputeElevation(uri: String): Boolean {
        val now = clock.now().toEpochMilliseconds()
        return db.tx {
            if (statusOf(uri) == null) return@tx false
            recomputeEffortElevation(listOf(uri))
            jobs.enqueueElevation(uri, now)
            true
        }
    }

    /**
     * Queues a `segment` job matching [uri] against the usable tracks near it, only while it's live; its status otherwise, null when gone.
     */
    suspend fun rematch(uri: String): Rematch {
        val now = clock.now().toEpochMilliseconds()
        return db.tx {
            val status = statusOf(uri) ?: return@tx Rematch.NotFound
            if (status !in LIVE_STATUSES) return@tx Rematch.NotLive(status)
            jobs.enqueueSegment(uri, now)
            Rematch.Queued
        }
    }

    private suspend fun proposalStates(segment: String): Map<String, String> = db.tx {
        SegmentNameProposals.select(SegmentNameProposals.uri, SegmentNameProposals.state)
            .where { SegmentNameProposals.segmentUri eq segment }
            .associate { it[SegmentNameProposals.uri] to it[SegmentNameProposals.state] }
    }

    private fun JdbcTransaction.statusOf(uri: String): String? =
        Segments.select(Segments.status).where { Segments.uri eq uri }.firstOrNull()?.get(Segments.status)
}

/**
 * Makes each of [uris] that is proposed active once efforts on it come from [activeAthletes] distinct athletes. A segment an operator
 * pinned active or archived (segments/segment-pins) already holds that status, never `proposed`, so [promoteBy]'s guard leaves it alone.
 */
internal fun JdbcTransaction.promoteSegments(uris: Collection<String>, activeAthletes: Int) =
    promoteBy(
        uris,
        Segments.uri,
        Segments.status,
        SegmentEfforts.segmentUri,
        SegmentEfforts.authorDid,
        activeAthletes,
    )

/**
 * Makes each of [uris] whose row (by [uri]) is proposed in [status] active once the rows naming it in [usedBy] come from [activeAthletes]
 * distinct [author]s: the shared promotion of segments by their efforts and dive sites by their visits.
 */
@Suppress("LongParameterList") // The table's columns, named so segments and dive sites share it.
internal fun JdbcTransaction.promoteBy(
    uris: Collection<String>,
    uri: Column<String>,
    status: Column<String>,
    usedBy: Column<String>,
    author: Column<String>,
    activeAthletes: Int,
) {
    for (each in uris) {
        val athletes = author.table.select(author).where { usedBy eq each }.withDistinct().count()
        if (athletes < activeAthletes) continue
        status.table.update({ (uri eq each) and (status eq SegmentStatus.PROPOSED) }) { it[status] = SegmentStatus.ACTIVE }
    }
}
