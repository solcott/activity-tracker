package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import kotlinx.collections.immutable.ImmutableList

/**
 * Picking another of the athlete's own activities, from within 12 hours of [uri]'s start, to combine with [uri]
 * (`feed/combined-activities`). [uri] is an AT-URI string: an activity of theirs, or a combined activity, which the pick joins.
 */
@CircuitSerializable(SessionScope::class) data class CombinePickerScreen(val uri: String) : Screen

@Immutable
data class CombinePickerState(
    /** The activities that can be combined with this one, by start; null while loading. */
    val candidates: ImmutableList<ActivityView>? = null,
    /** The activity being combined while it's built and written. */
    val combining: AtUri? = null,
    val error: UiError? = null,
    val eventSink: (CombinePickerEvent) -> Unit = {},
) : CircuitUiState

sealed interface CombinePickerEvent {
    data object Back : CombinePickerEvent

    data object Retry : CombinePickerEvent

    /** Combines [uri] with the screen's activity. */
    data class Pick(val uri: AtUri) : CombinePickerEvent
}
