package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitSystem

/** The signed-in athlete's preferences, and sign-out. */
@CircuitSerializable(SessionScope::class) data object SettingsScreen : Screen

@Immutable
data class SettingsState(
    val units: UnitSystem,
    val saving: Boolean,
    val error: UiError?,
    val eventSink: (SettingsEvent) -> Unit,
) : CircuitUiState

sealed interface SettingsEvent {
    data object Back : SettingsEvent

    data class UnitsChanged(val units: UnitSystem) : SettingsEvent

    data object OpenZones : SettingsEvent

    data object SignOut : SettingsEvent
}
