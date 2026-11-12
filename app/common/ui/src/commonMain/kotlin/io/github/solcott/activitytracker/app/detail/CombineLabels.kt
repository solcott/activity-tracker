package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.ui.LocalClock
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.data.activity.CombineCue
import io.github.solcott.activitytracker.lexicon.app.ActivityView

/** What a combine suggestion looks like, as a question. */
@Composable fun combineCueLabel(cue: CombineCue): String = RecordingTexts.combineCue(cue).string()

/**
 * [activity] as a suggestion names it, as `RecordingTexts.suggestedActivity` chooses: its title (or type), when it started and its length.
 */
@Composable
fun suggestedActivityLabel(activity: ActivityView): String =
    RecordingTexts.suggestedActivity(activity, LocalClock.current.now(), LocalTimeZone.current).string()
