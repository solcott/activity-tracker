package io.github.solcott.activitytracker.app.edit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityType

/** Edits the signed-in athlete's activity at [uri]: its title, description, dive site and photos. */
@CircuitSerializable(SessionScope::class) data class EditActivityScreen(val uri: String) : Screen

@Immutable
data class EditActivityState(
    /** Null while the activity loads. */
    val type: ActivityType?,
    val title: TextFieldState,
    /** Null while the activity loads. */
    val details: ActivityDetailsFields?,
    val saving: Boolean,
    val error: UiError?,
    /** The athlete went back with unsaved changes; ask before dropping them. */
    val confirmDiscard: Boolean,
    val eventSink: (EditActivityEvent) -> Unit,
) : CircuitUiState

sealed interface EditActivityEvent {
    data object Save : EditActivityEvent

    /** Leaves, or asks first ([EditActivityState.confirmDiscard]) when there are unsaved changes. */
    data object Back : EditActivityEvent

    data object Discard : EditActivityEvent

    data object KeepEditing : EditActivityEvent

    /** Retries a failed load. */
    data object Retry : EditActivityEvent
}
