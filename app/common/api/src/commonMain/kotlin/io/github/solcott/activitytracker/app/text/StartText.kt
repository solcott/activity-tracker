package io.github.solcott.activitytracker.app.text

import io.github.solcott.activitytracker.app.detail.FigureValue
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * How an activity's start reads (feed/activity-start-time, dev/shared-display-text), on the activity's own clock: "Today", "Yesterday" or
 * its date, and " at " its time when the viewer was served it. Today is the current date on that same clock. With no stored offset it's the
 * viewer's [zone], but a date-only start was cut at UTC midnight, so it's read in UTC to keep its day. The time of day is an [AppText.Time]
 * in the activity's offset, which each UI formats on the device's clock.
 */
object StartText {
    fun of(start: FigureValue.StartTime, now: Instant, zone: TimeZone): AppText {
        val offset = start.utcOffsetMinutes
        val clock = offset?.let { UtcOffset(seconds = it * SECONDS_PER_MINUTE).asTimeZone() } ?: if (start.dateOnly) TimeZone.UTC else zone
        val at = Instant.fromEpochSeconds(start.epochSeconds)
        val day = DayText.day(at.toLocalDateTime(clock).date, today = now.toLocalDateTime(clock).date)
        return if (start.dateOnly) day else AppText.named("page_start_value", day, AppText.Time(start.epochSeconds, offset))
    }

    /** [record]'s start as [of] reads it. */
    fun of(record: ActivityRecord, now: Instant, zone: TimeZone): AppText = of(startValue(record), now, zone)

    /**
     * The start a viewer was served: only its day when the record holds its local midnight, as it does when the time is hidden from them.
     */
    fun startValue(record: ActivityRecord): FigureValue.StartTime =
        FigureValue.StartTime(
            record.startedAt.epochSeconds,
            record.utcOffset,
            dateOnly = record.startedAt == DetailCategory.localMidnight(record.startedAt, record.utcOffset),
        )

    private const val SECONDS_PER_MINUTE = 60
}
