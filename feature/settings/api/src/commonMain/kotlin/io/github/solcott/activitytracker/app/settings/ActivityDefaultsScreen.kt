package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.data.di.SessionScope

/** Who new activities and each detail category are shown to unless the athlete chooses otherwise: a page of Settings. */
@CircuitSerializable(SessionScope::class) data object ActivityDefaultsScreen : SettingsPage

@Immutable
data class ActivityDefaultsState(
    /** The defaults, whose pickers send to their own sink; null while they load. */
    val defaults: VisibilityFields?,
    val saving: Boolean,
    val error: UiError?,
    val eventSink: (ActivityDefaultsEvent) -> Unit,
) : CircuitUiState

sealed interface ActivityDefaultsEvent {
    data object Back : ActivityDefaultsEvent
}
