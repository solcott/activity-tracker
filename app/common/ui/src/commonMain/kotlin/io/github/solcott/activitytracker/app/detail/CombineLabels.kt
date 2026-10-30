package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.combine_cue_break
import io.github.solcott.activitytracker.composeApp.resources.combine_cue_same_activity
import io.github.solcott.activitytracker.composeApp.resources.combine_cue_shuttle
import io.github.solcott.activitytracker.composeApp.resources.combine_cue_switched_devices
import io.github.solcott.activitytracker.composeApp.resources.combine_row
import io.github.solcott.activitytracker.data.activity.CombineCue
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.activityType
import org.jetbrains.compose.resources.stringResource

/** What a combine suggestion looks like, as a question. */
@Composable
fun combineCueLabel(cue: CombineCue): String =
    stringResource(
        when (cue) {
            CombineCue.SameActivity -> Res.string.combine_cue_same_activity
            CombineCue.SwitchedDevices -> Res.string.combine_cue_switched_devices
            CombineCue.ShuttleOrLift -> Res.string.combine_cue_shuttle
            CombineCue.Break -> Res.string.combine_cue_break
        }
    )

/** [activity] as a suggestion names it: its title (or type), then when it started and how long it lasted. */
@Composable
fun suggestedActivityLabel(activity: ActivityView): String {
    val record = activity.record
    val start = startTime(FigureValue.StartTime(record.startedAt.epochSeconds, record.utcOffset, dateOnly = false))
    val name = record.title ?: activityTypeLabel(record.activityType)
    return "$name (${stringResource(Res.string.combine_row, start, Format.duration(record.elapsedTime))})"
}
