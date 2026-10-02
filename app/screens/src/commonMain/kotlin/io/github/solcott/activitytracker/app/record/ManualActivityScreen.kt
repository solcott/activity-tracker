package io.github.solcott.activitytracker.app.record

import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.data.di.SessionScope

/** Log an activity without GPS (also handy for testing on web and simulators). */
@CircuitSerializable(SessionScope::class) data object ManualActivityScreen : Screen

data class ManualActivityState(
    val sport: String,
    val title: String,
    val distanceKm: String,
    val durationMinutes: String,
    val saving: Boolean,
    val error: String?,
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

    data object Save : ManualActivityEvent

    data object Back : ManualActivityEvent
}
