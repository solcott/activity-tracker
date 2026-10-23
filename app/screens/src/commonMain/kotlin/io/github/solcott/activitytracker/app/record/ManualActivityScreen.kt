package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.parseDecimal

/** Log an activity without GPS (also handy for testing on web and simulators). */
@CircuitSerializable(SessionScope::class) data object ManualActivityScreen : Screen

@Immutable
data class ManualActivityState(
    val type: ActivityTypeField,
    val title: TextFieldState,
    /** In kilometers or miles, per [units]. */
    val distance: TextFieldState,
    val durationMinutes: TextFieldState,
    /** In °C or °F, per [units]. Optional. */
    val temperature: TextFieldState,
    /** Units for this form's entries; starts at the athlete's preference. */
    val units: UnitSystem,
    val saving: Boolean,
    val error: UiError?,
    /** Who may see the activity and each detail category; starts at the athlete's defaults. */
    val visibility: VisibilityFields = VisibilityFields(),
    /** The gear used; starts at the athlete's defaults for the type. */
    val gear: GearField = GearField(),
    val eventSink: (ManualActivityEvent) -> Unit,
) : CircuitUiState {
    val valid: Boolean
        get() = (parseDecimal(durationMinutes.text.toString()) ?: 0.0) > 0
}

sealed interface ManualActivityEvent {
    data class UnitsChanged(val value: UnitSystem) : ManualActivityEvent

    data object Save : ManualActivityEvent

    data object Back : ManualActivityEvent
}
