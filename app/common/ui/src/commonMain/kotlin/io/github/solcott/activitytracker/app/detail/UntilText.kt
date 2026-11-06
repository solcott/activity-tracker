package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.ui.LocalClock
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import kotlin.time.Instant
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Something lasting until [until], as [today] ("… until 3:40 PM") or, when it ends after midnight in the viewer's zone, [tomorrow] ("…
 * until tomorrow at 3:40 PM"), each given the clock time in the viewer's 12- or 24-hour style ([timeFormat]).
 */
@Composable
fun untilText(until: Instant, today: StringResource, tomorrow: StringResource): String {
    val zone = LocalTimeZone.current
    val end = until.toLocalDateTime(zone)
    val time = end.format(timeFormat())
    return stringResource(if (end.date > LocalClock.current.now().toLocalDateTime(zone).date) tomorrow else today, time)
}
