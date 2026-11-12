package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.parseDecimal
import io.github.solcott.activitytracker.textfield.TextFieldState

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
    /**
     * A duration above zero is entered. The presenter fills it from [durationMinutes] as it changes, so a new state follows typing (a
     * native Save redraws only on one).
     */
    val durationEntered: Boolean = (parseDecimal(durationMinutes.text.toString()) ?: 0.0) > 0,
    val eventSink: (ManualActivityEvent) -> Unit,
) : CircuitUiState {
    val valid: Boolean
        get() = durationEntered
}

/** What manual entry says (dev/shared-display-text), chosen here for the shared and the native form. */
object ManualActivityTexts {
    /** The distance field's label, in kilometers or miles. */
    fun distanceLabel(units: UnitSystem): AppText =
        when (units) {
            UnitSystem.Metric -> AppText.named("manual_distance_label_km")
            UnitSystem.Imperial -> AppText.named("manual_distance_label_mi")
        }

    /** The temperature field's label, in °C or °F. */
    fun temperatureLabel(units: UnitSystem): AppText =
        when (units) {
            UnitSystem.Metric -> AppText.named("manual_temperature_label_c")
            UnitSystem.Imperial -> AppText.named("manual_temperature_label_f")
        }
}

sealed interface ManualActivityEvent {
    data class UnitsChanged(val value: UnitSystem) : ManualActivityEvent

    data object Save : ManualActivityEvent

    data object Back : ManualActivityEvent
}
