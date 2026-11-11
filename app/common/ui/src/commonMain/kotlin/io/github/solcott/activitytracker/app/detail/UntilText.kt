package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.text.DayText
import io.github.solcott.activitytracker.app.ui.LocalClock
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.string
import kotlin.time.Instant
import org.jetbrains.compose.resources.StringResource

/**
 * Something lasting until [until], as [today] ("… until 3:40 PM") or, when it ends after midnight in the viewer's zone, [tomorrow] ("…
 * until tomorrow at 3:40 PM"), each given the clock time in the viewer's 12- or 24-hour style. The rule is `DayText.until`'s.
 */
@Composable
fun untilText(until: Instant, today: StringResource, tomorrow: StringResource): String =
    DayText.until(until, LocalClock.current.now(), LocalTimeZone.current, today.key, tomorrow.key).string()
