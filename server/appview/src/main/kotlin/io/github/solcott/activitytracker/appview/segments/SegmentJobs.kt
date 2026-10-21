package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import kotlinx.coroutines.channels.Channel
import org.jetbrains.exposed.v1.jdbc.upsert

/**
 * The [SegmentMatcher]'s durable queue ([SegmentMatchJobs]). The indexer enqueues in the transaction of the change that calls for a match,
 * (each call must run in one), and each enqueue wakes the matcher. A job is keyed by its kind and subject, so enqueueing one already
 * waiting just makes it due now.
 */
class SegmentJobs {
    /** Signals the matcher that a job was enqueued. */
    internal val wakeups = Channel<Unit>(Channel.CONFLATED)

    /** Match the segment track at [trackUri] against the segments it passes, replacing its activity's efforts. */
    fun enqueueTrack(trackUri: String, now: Long) = enqueue(TRACK, trackUri, now)

    /** Match a new or changed segment against the usable segment tracks that pass near it. */
    fun enqueueSegment(segmentUri: String, now: Long) = enqueue(SEGMENT, segmentUri, now)

    /** Recompute the visibility of an activity's efforts from its indexed record, details and streams. */
    fun enqueueTiers(activityUri: String, now: Long) = enqueue(TIERS, activityUri, now)

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
        const val TIERS = "tiers"
    }
}
