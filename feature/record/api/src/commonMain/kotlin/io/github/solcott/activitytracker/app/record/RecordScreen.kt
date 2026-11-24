package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.edit.ActivityTextLimits
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.location.RecordingState
import io.github.solcott.activitytracker.textfield.TextFieldState
import kotlin.time.Duration

@CircuitSerializable(SessionScope::class) data object RecordScreen : Screen

@Immutable
data class RecordState(
    val status: RecordingState.Status,
    val elapsed: Duration,
    val distanceMeters: Double,
    val movingSeconds: Long,
    val elevationGainMeters: Double,
    /** An unfinished recording offered back after the app stopped: the athlete resumes, finishes or discards it. */
    val recovered: Boolean = false,
    /** The recording's last change couldn't be saved on the device; recording goes on and tries again. */
    val saveFailed: Boolean = false,
    val type: ActivityTypeField,
    val title: TextFieldState,
    /** What the athlete writes about it; blank saves none. */
    val description: TextFieldState,
    val hasPermission: Boolean,
    val saving: Boolean,
    val error: UiError?,
    /** Who may see the activity and each detail category; starts at the athlete's defaults. */
    val visibility: VisibilityFields = VisibilityFields(),
    /** The gear used; starts at the athlete's defaults for the type. */
    val gear: GearField = GearField(),
    /**
     * The title and description fit their limits ([ActivityTextLimits]). The presenter fills it from them as they change, so a new state
     * follows typing over or back under a limit (a native Save redraws only on one).
     */
    val textsFit: Boolean = ActivityTextLimits.fits(title.text.toString(), description.text.toString()),
    val eventSink: (RecordEvent) -> Unit,
) : CircuitUiState {
    /** Whether [action]'s control can be used: none while saving, and Finish & save only while the title and description fit. */
    fun enabled(action: RecordAction): Boolean = !saving && (action != RecordAction.Finish || textsFit)

    /** [description] for Swift, whose export leaves out a member named `description` (dev/ios-swift-interop). */
    val about: TextFieldState
        get() = description
}

sealed interface RecordEvent {
    data class PermissionResult(val granted: Boolean) : RecordEvent

    data object Start : RecordEvent

    data object Pause : RecordEvent

    data object Resume : RecordEvent

    data object Finish : RecordEvent

    data object Discard : RecordEvent

    data object Back : RecordEvent
}
