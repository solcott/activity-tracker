package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope

/**
 * The athlete left Activity Tracker on another device (auth/leaving), and this one still holds work for the account. The app shell shows it
 * in place of the app; with nothing pending it signs out at once with the "left" note.
 */
@CircuitSerializable(SessionScope::class) data object LeftElsewhereScreen : Screen

@Immutable
data class LeftElsewhereState(
    val handle: String,
    /** Still reading what this device holds; with nothing, the session ends without asking. */
    val checking: Boolean = true,
    /** Changes not uploaded (waiting, uploading or refused). */
    val changes: Int = 0,
    val importSaving: Boolean = false,
    val exportPending: Boolean = false,
    val recording: Boolean = false,
    /** Discarding or rejoining: both actions are off until it's done. */
    val busy: Boolean = false,
    val error: UiError? = null,
    val eventSink: (LeftElsewhereEvent) -> Unit,
) : CircuitUiState

sealed interface LeftElsewhereEvent {
    /** Drops what this device holds for the account and signs out with the "left" note. */
    data object Discard : LeftElsewhereEvent

    /** Rejoins: the spaces are created again as writes need them, and the pending work uploads. */
    data object Rejoin : LeftElsewhereEvent
}
