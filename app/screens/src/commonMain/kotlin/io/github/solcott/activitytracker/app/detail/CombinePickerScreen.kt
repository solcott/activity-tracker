package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Picking another of the athlete's own activities, from within 12 hours of [uri]'s start, to combine with [uri]
 * (`feed/combined-activities`). [uri] is an AT-URI string: an activity of theirs, or a combined activity, which the pick joins.
 */
@CircuitSerializable(SessionScope::class) data class CombinePickerScreen(val uri: String) : Screen

@Immutable
data class CombinePickerState(
    /** The activities that can be combined with this one, by start, loaded (ui/content-loading); empty until they first load. */
    val content: ContentState<ImmutableList<ActivityView>> = ContentState(persistentListOf()),
    /** The activity being combined while it's built and written. */
    val combining: AtUri? = null,
    /** Why combining failed; loading failures are [content]'s. */
    val error: UiError? = null,
    val eventSink: (CombinePickerEvent) -> Unit = {},
) : CircuitUiState {
    val candidates: ImmutableList<ActivityView>
        get() = content.data
}

sealed interface CombinePickerEvent {
    data object Back : CombinePickerEvent

    data object Retry : CombinePickerEvent

    /** Combines [uri] with the screen's activity. */
    data class Pick(val uri: AtUri) : CombinePickerEvent
}
