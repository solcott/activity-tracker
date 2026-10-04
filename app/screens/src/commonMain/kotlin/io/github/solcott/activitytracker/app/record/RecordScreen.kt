package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.location.RecordingState
import kotlin.time.Duration

@CircuitSerializable(SessionScope::class) data object RecordScreen : Screen

@Immutable
data class RecordState(
    val status: RecordingState.Status,
    val elapsed: Duration,
    val distanceMeters: Double,
    val movingSeconds: Long,
    val elevationGainMeters: Double,
    val sport: String,
    val title: String,
    val hasPermission: Boolean,
    val saving: Boolean,
    val error: UiError?,
    val eventSink: (RecordEvent) -> Unit,
) : CircuitUiState

sealed interface RecordEvent {
    data class SportChanged(val sport: String) : RecordEvent

    data class TitleChanged(val title: String) : RecordEvent

    data class PermissionResult(val granted: Boolean) : RecordEvent

    data object Start : RecordEvent

    data object Pause : RecordEvent

    data object Resume : RecordEvent

    data object Finish : RecordEvent

    data object Discard : RecordEvent

    data object Back : RecordEvent
}
