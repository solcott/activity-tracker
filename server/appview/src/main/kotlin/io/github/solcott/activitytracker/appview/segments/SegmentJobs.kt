package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import io.github.solcott.activitytracker.appview.db.Segments
import kotlinx.coroutines.channels.Channel
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.longLiteral
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert

/**
 * The [SegmentMatcher]'s durable queue ([SegmentMatchJobs]). The indexer enqueues in the transaction of the change that calls for a match,
 * (each call must run in one), and each enqueue wakes the matcher. A job is keyed by its kind and subject, so enqueueing one already
 * waiting just makes it due now.
 */
class SegmentJobs(
    /** Whether terrain tiles are configured, so new and re-lined segments are given terrain elevation (segments/segment-elevation). */
    private val terrain: Boolean = false
) {
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
        val queued =
            SegmentMatchJobs.select(SegmentMatchJobs.kind).where {
                (SegmentMatchJobs.kind eq ELEVATION) and (SegmentMatchJobs.subjectUri eq Segments.uri)
            }
        val segments =
            Segments.select(stringLiteral(ELEVATION), Segments.uri, longLiteral(now), intLiteral(0)).where { where and notExists(queued) }
        val count =
            SegmentMatchJobs.insert(
                segments,
                listOf(SegmentMatchJobs.kind, SegmentMatchJobs.subjectUri, SegmentMatchJobs.enqueuedAt, SegmentMatchJobs.attempts),
            ) ?: 0
        if (count > 0) wakeups.trySend(Unit)
        return count
    }

    /** Count each new or changed staged candidate's riders among the usable segment tracks that pass near it, in one batched statement. */
    fun enqueueCandidates(candidateIds: Collection<String>, now: Long) {
        if (candidateIds.isEmpty()) return
        SegmentMatchJobs.batchUpsert(candidateIds, shouldReturnGeneratedValues = false) { id ->
            this[SegmentMatchJobs.kind] = CANDIDATE
            this[SegmentMatchJobs.subjectUri] = id
            this[SegmentMatchJobs.enqueuedAt] = now
            this[SegmentMatchJobs.attempts] = 0
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
    }
}
