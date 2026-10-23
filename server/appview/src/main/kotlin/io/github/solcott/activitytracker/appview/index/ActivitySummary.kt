package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import java.time.LocalDate
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

/**
 * The core summary fields of an activity that gear usage totals, as the `activities` columns hold them. Untrusted: a negative value is
 * dropped, and an offset no zone has counts as UTC (the indexer drops it from the stored record too).
 */
internal data class ActivitySummary(
    val elapsedTime: Long?,
    val movingTime: Long?,
    val distance: Long?,
    val elevationGain: Long?,
    val elevationLoss: Long?,
    val diveBottomTime: Long?,
    /** The epoch day of the local start date. */
    val startedOn: Long,
    val startedYear: Int,
) {
    companion object {
        fun of(record: ActivityRecord): ActivitySummary {
            val offset = record.utcOffset?.takeIf { it in ActivityRecord.MIN_UTC_OFFSET..ActivityRecord.MAX_UTC_OFFSET } ?: 0
            val local = record.startedAt.toEpochMilliseconds() + offset.minutes.inWholeMilliseconds
            val day = Math.floorDiv(local, 1.days.inWholeMilliseconds)
            return ActivitySummary(
                elapsedTime = record.elapsedTime.takeIf { it >= 0 },
                movingTime = record.movingTime?.takeIf { it >= 0 },
                distance = record.distance?.takeIf { it >= 0 },
                elevationGain = record.elevationGain?.takeIf { it >= 0 },
                elevationLoss = record.elevationLoss?.takeIf { it >= 0 },
                diveBottomTime = record.dive?.bottomTime?.takeIf { it >= 0 },
                startedOn = day,
                startedYear = LocalDate.ofEpochDay(day).year,
            )
        }
    }
}

/** Writes [summary] to the [Activities] row being upserted. */
internal fun UpdateBuilder<*>.setSummary(summary: ActivitySummary) {
    this[Activities.elapsedTime] = summary.elapsedTime
    this[Activities.movingTime] = summary.movingTime
    this[Activities.distance] = summary.distance
    this[Activities.elevationGain] = summary.elevationGain
    this[Activities.elevationLoss] = summary.elevationLoss
    this[Activities.diveBottomTime] = summary.diveBottomTime
    this[Activities.startedOn] = summary.startedOn
    this[Activities.startedYear] = summary.startedYear
}
