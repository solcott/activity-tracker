package io.github.solcott.activitytracker.app.gear

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** The devices the athlete marked as not gear (gear/gear-sensors), which imports don't offer; each can be un-ignored. */
@CircuitSerializable(SessionScope::class) data object IgnoredSensorsScreen : Screen

@Immutable
data class IgnoredSensorsState(
    /** The ignored devices, as [LinkedSensor]s (maker, model, serial tail), in the order they were ignored. */
    val content: ContentState<ImmutableList<LinkedSensor>> = ContentState(persistentListOf()),
    /** Why the last un-ignore failed. */
    val error: UiError? = null,
    val eventSink: (IgnoredSensorsEvent) -> Unit = {},
) : CircuitUiState

sealed interface IgnoredSensorsEvent {
    data object Back : IgnoredSensorsEvent

    data object Retry : IgnoredSensorsEvent

    /** Stops ignoring the device with [id]: the next import naming it offers it again. */
    data class Unignore(val id: String) : IgnoredSensorsEvent
}
