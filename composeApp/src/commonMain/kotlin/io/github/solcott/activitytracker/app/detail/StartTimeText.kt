package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.solcott.activitytracker.app.settings.monthName
import io.github.solcott.activitytracker.app.ui.uses24HourClock
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.page_start_date
import io.github.solcott.activitytracker.composeApp.resources.page_start_value
import io.github.solcott.activitytracker.composeApp.resources.time_am
import io.github.solcott.activitytracker.composeApp.resources.time_pm
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/** On the activity's own clock: its date, and its time when the viewer was served it. */
@Composable
internal fun startTime(value: FigureValue.StartTime): String {
    val minutes = value.utcOffsetMinutes ?: 0
    val offset = UtcOffset(hours = minutes / MINUTES_PER_HOUR, minutes = minutes % MINUTES_PER_HOUR)
    val local = Instant.fromEpochSeconds(value.epochSeconds).toLocalDateTime(offset.asTimeZone())
    val date = stringResource(Res.string.page_start_date, monthName(local.month.ordinal + 1), local.day, local.year)
    return if (value.dateOnly) date else stringResource(Res.string.page_start_value, date, local.format(timeFormat()))
}

/** "07:00" on a 24-hour clock, "7:00 AM" on a 12-hour one, as the viewer's device reads times. */
@Composable
private fun timeFormat(): DateTimeFormat<LocalDateTime> {
    val twentyFour = uses24HourClock()
    val am = stringResource(Res.string.time_am)
    val pm = stringResource(Res.string.time_pm)
    return remember(twentyFour, am, pm) {
        LocalDateTime.Format {
            if (twentyFour) hour() else amPmHour(Padding.NONE)
            char(':')
            minute()
            if (!twentyFour) {
                char(' ')
                amPmMarker(am, pm)
            }
        }
    }
}

private const val MINUTES_PER_HOUR = 60
