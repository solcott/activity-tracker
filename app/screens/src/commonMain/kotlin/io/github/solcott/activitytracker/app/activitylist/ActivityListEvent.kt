package io.github.solcott.activitytracker.app.activitylist

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.app.ActivityView

/** A tap on an activity in a list, shared by every screen that lists activities. Each host wraps it in its own event. */
@Immutable
sealed interface ActivityListEvent {
    data class Open(val activity: ActivityView) : ActivityListEvent

    data class OpenAuthor(val activity: ActivityView) : ActivityListEvent

    /** Opens [activity]'s media full screen at [index]. */
    data class OpenMedia(val activity: ActivityView, val index: Int) : ActivityListEvent

    data class ToggleKudos(val activity: ActivityView) : ActivityListEvent
}
