package io.github.solcott.activitytracker.app.feed

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.activity.RejectedChange
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.ImmutableList

/** The athlete's changes their server refused (data/outbox), each with Retry and Discard. Opened from the feed's "couldn't publish" row. */
@CircuitSerializable(SessionScope::class) data object UnsentChangesScreen : Screen

@Immutable
data class UnsentChangesState(
    /** The refused changes, oldest first; null until read. */
    val changes: ImmutableList<RejectedChange>?,
    /** The change the athlete asked to discard, while they confirm. */
    val confirmingDiscard: RejectedChange? = null,
    /** The rkeys of changes being sent again or discarded. */
    val busy: ImmutableList<String>,
    /** Why the last retry or discard failed. */
    val error: UiError? = null,
    val eventSink: (UnsentChangesEvent) -> Unit = {},
) : CircuitUiState

sealed interface UnsentChangesEvent {
    data object Back : UnsentChangesEvent

    /** Sends the change with [rkey] again. */
    data class Retry(val rkey: String) : UnsentChangesEvent

    /** Asks to confirm discarding the change with [rkey]. */
    data class Discard(val rkey: String) : UnsentChangesEvent

    /**
     * Discards the change with [rkey] after the athlete confirmed: its writes are dropped from the device. It names the change because the
     * dialog dismisses ([CancelDiscard]) before it confirms.
     */
    data class ConfirmDiscard(val rkey: String) : UnsentChangesEvent

    data object CancelDiscard : UnsentChangesEvent
}
