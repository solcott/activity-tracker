package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitSystem

/** Log an activity without GPS (also handy for testing on web and simulators). */
@CircuitSerializable(SessionScope::class) data object ManualActivityScreen : Screen

@Immutable
data class ManualActivityState(
    val sport: String,
    val title: String,
    /** In kilometers or miles, per [units]. */
    val distance: String,
    val durationMinutes: String,
    /** In °C or °F, per [units]. Optional. */
    val temperature: String,
    /** Units for this form's entries; starts at the athlete's preference. */
    val units: UnitSystem,
    val saving: Boolean,
    val error: UiError?,
    val eventSink: (ManualActivityEvent) -> Unit,
) : CircuitUiState {
    val valid: Boolean
        get() = (durationMinutes.toDoubleOrNull() ?: 0.0) > 0
}

sealed interface ManualActivityEvent {
    data class SportChanged(val value: String) : ManualActivityEvent

    data class TitleChanged(val value: String) : ManualActivityEvent

    data class DistanceChanged(val value: String) : ManualActivityEvent

    data class DurationChanged(val value: String) : ManualActivityEvent

    data class TemperatureChanged(val value: String) : ManualActivityEvent

    data class UnitsChanged(val value: UnitSystem) : ManualActivityEvent

    data object Save : ManualActivityEvent

    data object Back : ManualActivityEvent
}
