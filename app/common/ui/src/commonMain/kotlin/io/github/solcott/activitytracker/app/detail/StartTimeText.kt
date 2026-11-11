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
import io.github.solcott.activitytracker.app.text.StartText
import io.github.solcott.activitytracker.app.ui.LocalClock
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.VisibilityBadge
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.app.ui.uses24HourClock
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.time_am
import io.github.solcott.activitytracker.composeApp.resources.time_pm
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import org.jetbrains.compose.resources.stringResource

/** [value] as `StartText` reads it, on the activity's own clock beside today on [LocalClock], formatted on the device's clock. */
@Composable
fun startTime(value: FigureValue.StartTime): String = StartText.of(value, LocalClock.current.now(), LocalTimeZone.current).string()

/** A page's [start] under its author, with its narrower visibility beside it on the owner's page; nothing without one. */
@Composable
fun StartLine(start: Figure?, modifier: Modifier = Modifier) {
    val value = start?.value as? FigureValue.StartTime ?: return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(startTime(value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        start.mark?.let { VisibilityBadge(it) }
    }
}

/** "07:00" on a 24-hour clock, "7:00 AM" on a 12-hour one, as the viewer's device reads times. */
@Composable
fun timeFormat(): DateTimeFormat<LocalDateTime> {
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
