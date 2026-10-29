package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.LocalClock
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.VisibilityBadge
import io.github.solcott.activitytracker.app.ui.uses24HourClock
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.month_long_1
import io.github.solcott.activitytracker.composeApp.resources.month_long_10
import io.github.solcott.activitytracker.composeApp.resources.month_long_11
import io.github.solcott.activitytracker.composeApp.resources.month_long_12
import io.github.solcott.activitytracker.composeApp.resources.month_long_2
import io.github.solcott.activitytracker.composeApp.resources.month_long_3
import io.github.solcott.activitytracker.composeApp.resources.month_long_4
import io.github.solcott.activitytracker.composeApp.resources.month_long_5
import io.github.solcott.activitytracker.composeApp.resources.month_long_6
import io.github.solcott.activitytracker.composeApp.resources.month_long_7
import io.github.solcott.activitytracker.composeApp.resources.month_long_8
import io.github.solcott.activitytracker.composeApp.resources.month_long_9
import io.github.solcott.activitytracker.composeApp.resources.page_start_date
import io.github.solcott.activitytracker.composeApp.resources.page_start_value
import io.github.solcott.activitytracker.composeApp.resources.start_today
import io.github.solcott.activitytracker.composeApp.resources.start_yesterday
import io.github.solcott.activitytracker.composeApp.resources.time_am
import io.github.solcott.activitytracker.composeApp.resources.time_pm
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * On the activity's own clock: "Today", "Yesterday" or its date, and " at " its time when the viewer was served it
 * (feed/activity-start-time). Today is the current date on that same clock. With no stored offset it's the viewer's zone, but a date-only
 * start was cut at UTC midnight, so it's read in UTC to keep its day.
 */
@Composable
internal fun startTime(value: FigureValue.StartTime): String {
    val zone =
        value.utcOffsetMinutes?.let { UtcOffset(hours = it / MINUTES_PER_HOUR, minutes = it % MINUTES_PER_HOUR).asTimeZone() }
            ?: if (value.dateOnly) TimeZone.UTC else LocalTimeZone.current
    val local = Instant.fromEpochSeconds(value.epochSeconds).toLocalDateTime(zone)
    val today = LocalClock.current.now().toLocalDateTime(zone).date
    val day =
        when (local.date) {
            today -> stringResource(Res.string.start_today)
            today.minus(DatePeriod(days = 1)) -> stringResource(Res.string.start_yesterday)
            else -> stringResource(Res.string.page_start_date, stringResource(MONTHS[local.month.ordinal]), local.day, local.year)
        }
    return if (value.dateOnly) day else stringResource(Res.string.page_start_value, day, local.format(timeFormat()))
}

/** A page's [start] under its author, with its narrower visibility beside it on the owner's page; nothing without one. */
@Composable
internal fun StartLine(start: Figure?, modifier: Modifier = Modifier) {
    val value = start?.value as? FigureValue.StartTime ?: return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(startTime(value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        start.mark?.let { VisibilityBadge(it) }
    }
}

/** The start a viewer was served: only its day when the record holds its local midnight, as it does when the time is hidden from them. */
internal fun ActivityRecord.startTimeValue(): FigureValue.StartTime =
    FigureValue.StartTime(startedAt.epochSeconds, utcOffset, dateOnly = startedAt == DetailCategory.localMidnight(startedAt, utcOffset))

/** "07:00" on a 24-hour clock, "7:00 AM" on a 12-hour one, as the viewer's device reads times. */
@Composable
internal fun timeFormat(): DateTimeFormat<LocalDateTime> {
    val twentyFour = uses24HourClock()
    val am = stringResource(Res.string.time_am)
    val pm = stringResource(Res.string.time_pm)
    // Resources load asynchronously on the web, so the markers can be empty for a frame; a format can't have an empty one.
    val twelve = !twentyFour && am.isNotEmpty() && pm.isNotEmpty()
    return remember(twelve, am, pm) {
        LocalDateTime.Format {
            if (twelve) amPmHour(Padding.NONE) else hour()
            char(':')
            minute()
            if (twelve) {
                char(' ')
                amPmMarker(am, pm)
            }
        }
    }
}

private const val MINUTES_PER_HOUR = 60

/** Full month names, January first: a start's date is written out ("October 26, 2026"). */
private val MONTHS =
    listOf(
        Res.string.month_long_1,
        Res.string.month_long_2,
        Res.string.month_long_3,
        Res.string.month_long_4,
        Res.string.month_long_5,
        Res.string.month_long_6,
        Res.string.month_long_7,
        Res.string.month_long_8,
        Res.string.month_long_9,
        Res.string.month_long_10,
        Res.string.month_long_11,
        Res.string.month_long_12,
    )
