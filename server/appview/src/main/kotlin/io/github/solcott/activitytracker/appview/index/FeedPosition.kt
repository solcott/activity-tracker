package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import kotlin.time.Duration.Companion.days
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

/**
 * Where an activity sits on the timeline (feed/timeline-order), from its own record only, so a field shared in a narrower tier never moves
 * it: [sortAt] is the earlier of [postedAt] (its `createdAt`, never later than [now]) and [GRACE_MS] after [endedAt]. An activity posted
 * more than that long after it ended is [backfilled], which is what a batch's import card collapses (feed/import-cards).
 */
internal class FeedPosition(val postedAt: Long, val endedAt: Long) {
    val sortAt: Long = minOf(postedAt, endedAt + GRACE_MS)
    val backfilled: Boolean = endedAt + GRACE_MS < postedAt

    companion object {
        /** How long after an activity ends it may still be posted to the top of the timeline. */
        val GRACE_MS: Long = 5.days.inWholeMilliseconds

        fun of(record: ActivityRecord, now: Long): FeedPosition {
            val startedAt = record.startedAt.toEpochMilliseconds()
            val elapsedMs = record.elapsedTime.coerceAtLeast(0).coerceAtMost(MAX_ELAPSED_S) * MS_PER_S
            return FeedPosition(postedAt = minOf(record.createdAt.toEpochMilliseconds(), now), endedAt = startedAt + elapsedMs)
        }

        /** [ActivityRecord.importBatch] when it is a TID, else null: an activity with a malformed one belongs to no batch. */
        fun batchOf(record: ActivityRecord): String? = record.importBatch?.takeIf(Tid::isValid)

        private const val MS_PER_S = 1000L

        /** A year: bounds an untrusted elapsed time so the sum can't overflow. */
        private val MAX_ELAPSED_S = 365.days.inWholeSeconds
    }
}

/** [record]'s feed position as of [now], and its batch, on the activity row being written. */
internal fun UpdateBuilder<*>.setPosition(record: ActivityRecord, now: Long) {
    val position = FeedPosition.of(record, now)
    this[Activities.sortAt] = position.sortAt
    this[Activities.postedAt] = position.postedAt
    this[Activities.endedAt] = position.endedAt
    this[Activities.importBatch] = FeedPosition.batchOf(record)
}
