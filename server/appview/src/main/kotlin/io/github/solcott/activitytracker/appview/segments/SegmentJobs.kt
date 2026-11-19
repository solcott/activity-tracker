package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.SegmentJobFailures
import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import io.github.solcott.activitytracker.appview.db.SegmentTracks
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.heldTrack
import kotlinx.coroutines.channels.Channel
import org.jetbrains.exposed.v1.core.ColumnSet
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.longLiteral
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

/**
 * The [SegmentMatcher]'s durable queue ([SegmentMatchJobs]). The indexer enqueues in the transaction of the change that calls for a match,
 * (each call must run in one), and each enqueue wakes the matcher. A job is keyed by its kind and subject, so enqueueing one already
 * waiting just makes it due now.
 */
@Suppress("TooManyFunctions") // One enqueue per job kind, and the operator operations (admin/job-queues).
class SegmentJobs(
    /** Whether terrain tiles are configured, so new and re-lined segments are given terrain elevation (segments/segment-elevation). */
    val terrain: Boolean = false,
) {
    /** Whether terrain tiles are configured, so [enqueueElevation] queues anything. */
    val terrainConfigured: Boolean
        get() = terrain

    /** Signals the matcher that a job was enqueued. */
    internal val wakeups = Channel<Unit>(Channel.CONFLATED)

    /** Match the segment track at [trackUri] against the segments it passes, replacing its activity's efforts. */
    fun enqueueTrack(trackUri: String, now: Long) = enqueue(TRACK, trackUri, now)

    /** Match a new or changed segment against the usable segment tracks that pass near it. */
    fun enqueueSegment(segmentUri: String, now: Long) = enqueue(SEGMENT, segmentUri, now)

    /** Sample terrain elevation along a new or re-lined segment's line; nothing without terrain tiles. */
    fun enqueueElevation(segmentUri: String, now: Long) {
        if (terrain) enqueue(ELEVATION, segmentUri, now)
    }

    /**
     * Sample terrain elevation along every segment matching [where], in one statement; nothing without terrain tiles. Segments already
     * queued for it keep their place, so a restart doesn't push them back. Returns how many were queued.
     */
    fun enqueueElevations(where: Op<Boolean>, now: Long): Int {
        if (!terrain) return 0
        val segments =
            Segments.select(stringLiteral(ELEVATION), Segments.uri, longLiteral(now), intLiteral(0)).where(unqueuedElevations(where))
        return insertJobs(segments)
    }

    /** The segments matching [where] with no `elevation` job queued: what [enqueueElevations] would queue. */
    internal fun unqueuedElevations(where: Op<Boolean>): Op<Boolean> {
        val queued =
            SegmentMatchJobs.select(SegmentMatchJobs.kind).where {
                (SegmentMatchJobs.kind eq ELEVATION) and (SegmentMatchJobs.subjectUri eq Segments.uri)
            }
        return where and notExists(queued)
    }

    /**
     * Match every segment track some purpose may read now (`heldSegmentTracks`), or only those whose activity is of [sport], in one
     * statement (admin/job-queues). Tracks already queued keep their place. Returns how many were queued.
     */
    fun enqueueTracks(now: Long, sport: String? = null): Int {
        val tracks = unqueuedTracks(sport)
        return insertJobs(
            tracks.source.select(stringLiteral(TRACK), SegmentTracks.uri, longLiteral(now), intLiteral(0)).where(tracks.where),
        )
    }

    /** The held segment tracks, of [sport]'s activities when given, with no `track` job queued: what [enqueueTracks] would queue. */
    internal fun unqueuedTracks(sport: String?): Scope {
        val queued =
            SegmentMatchJobs.select(SegmentMatchJobs.kind).where {
                (SegmentMatchJobs.kind eq TRACK) and (SegmentMatchJobs.subjectUri eq SegmentTracks.uri)
            }
        val held = heldTrack() and notExists(queued)
        return if (sport == null) Scope(SegmentTracks, held)
        else
            Scope(
                SegmentTracks.join(Activities, JoinType.INNER, SegmentTracks.activityUri, Activities.uri),
                held and (Activities.sport eq sport),
            )
    }

    /** Rows of [source] matching [where]. */
    internal class Scope(val source: ColumnSet, val where: Op<Boolean>)

    private fun insertJobs(rows: Query): Int {
        val count =
            SegmentMatchJobs.insert(
                rows,
                listOf(SegmentMatchJobs.kind, SegmentMatchJobs.subjectUri, SegmentMatchJobs.enqueuedAt, SegmentMatchJobs.attempts),
            ) ?: 0
        if (count > 0) wakeups.trySend(Unit)
        return count
    }

    /**
     * Makes a retrying job due [now], keeping its attempts and last error so a further failure continues its backoff (admin/job-queues).
     * False when no such job is retrying. A run of it in progress doesn't dequeue it, since it's due at another time now.
     */
    fun retryNow(kind: String, subject: String, now: Long): Boolean {
        val updated =
            SegmentMatchJobs.update(
                {
                    (SegmentMatchJobs.kind eq kind) and
                        (SegmentMatchJobs.subjectUri eq subject) and
                        SegmentMatchJobs.firstFailedAt.isNotNull()
                },
            ) {
                it[enqueuedAt] = now
            }
        if (updated > 0) wakeups.trySend(Unit)
        return updated > 0
    }

    /**
     * Queues the job that gave up for [kind] and [subject] afresh and removes it as a failed job. False when there's no such failed job.
     */
    fun requeue(kind: String, subject: String, now: Long): Boolean {
        val deleted = SegmentJobFailures.deleteWhere { (SegmentJobFailures.kind eq kind) and (subjectUri eq subject) }
        if (deleted > 0) enqueue(kind, subject, now)
        return deleted > 0
    }

    /** Requeues the [limit] oldest failed jobs of [kind], as [requeue] does each; how many. */
    fun requeueFailed(kind: String, now: Long, limit: Int = REQUEUE_LIMIT): Int {
        val subjects =
            SegmentJobFailures.select(SegmentJobFailures.subjectUri)
                .where { SegmentJobFailures.kind eq kind }
                .orderBy(SegmentJobFailures.failedAt to SortOrder.ASC, SegmentJobFailures.subjectUri to SortOrder.ASC)
                .limit(limit)
                .map { it[SegmentJobFailures.subjectUri] }
        if (subjects.isEmpty()) return 0
        SegmentJobFailures.deleteWhere { (SegmentJobFailures.kind eq kind) and (subjectUri inList subjects) }
        SegmentMatchJobs.batchUpsert(subjects, shouldReturnGeneratedValues = false) { subject ->
            this[SegmentMatchJobs.kind] = kind
            this[SegmentMatchJobs.subjectUri] = subject
            this[SegmentMatchJobs.enqueuedAt] = now
            this[SegmentMatchJobs.attempts] = 0
            this[SegmentMatchJobs.lastError] = null
            this[SegmentMatchJobs.firstFailedAt] = null
        }
        wakeups.trySend(Unit)
        return subjects.size
    }

    /** Count each new or changed staged candidate's riders among the usable segment tracks that pass near it, in one batched statement. */
    fun enqueueCandidates(candidateIds: Collection<String>, now: Long) {
        if (candidateIds.isEmpty()) return
        SegmentMatchJobs.batchUpsert(candidateIds, shouldReturnGeneratedValues = false) { id ->
            this[SegmentMatchJobs.kind] = CANDIDATE
            this[SegmentMatchJobs.subjectUri] = id
            this[SegmentMatchJobs.enqueuedAt] = now
            this[SegmentMatchJobs.attempts] = 0
            this[SegmentMatchJobs.lastError] = null
            this[SegmentMatchJobs.firstFailedAt] = null
        }
        wakeups.trySend(Unit)
    }

    /** Recompute the visibility of an activity's efforts from its indexed record, details and streams. */
    fun enqueueTiers(activityUri: String, now: Long) = enqueue(TIERS, activityUri, now)

    /** Match a new or moved dive site against the usable dive tracks that pass near it (dive-sites/site-visits). */
    fun enqueueSite(siteUri: String, now: Long) = enqueue(SITE, siteUri, now)

    /** Pair a groupable segment track's activity with other athletes' done together with it (feed/group-activities). */
    fun enqueueGroup(trackUri: String, now: Long) = enqueue(GROUP, trackUri, now)

    private fun enqueue(kind: String, subject: String, now: Long) {
        SegmentMatchJobs.upsert {
            it[SegmentMatchJobs.kind] = kind
            it[subjectUri] = subject
            it[enqueuedAt] = now
            it[attempts] = 0
            it[lastError] = null
            it[firstFailedAt] = null
        }
        wakeups.trySend(Unit)
    }

    companion object {
        const val TRACK = "track"
        const val SEGMENT = "segment"
        const val CANDIDATE = "candidate"
        const val TIERS = "tiers"
        const val GROUP = "group"
        const val SITE = "site"
        const val ELEVATION = "elevation"

        /** Every kind, for the metrics' per-kind gauges. */
        val KINDS = listOf(TRACK, SEGMENT, CANDIDATE, TIERS, GROUP, SITE, ELEVATION)

        /** The most failed jobs [requeueFailed] requeues at once, so one request's transaction stays small. */
        const val REQUEUE_LIMIT = 500
    }
}
