package io.github.solcott.activitytracker.app.gear

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Creates a gear collection ([rkey] null) or edits the athlete's collection at [rkey]: its name, notes and members in order. */
@CircuitSerializable(SessionScope::class) data class EditGearCollectionScreen(val rkey: String? = null) : Screen

@Immutable
data class EditGearCollectionState(
    /** The collection and the athlete's items as first read. */
    val content: ContentState<Unit>,
    val isNew: Boolean,
    val name: TextFieldState,
    val notes: TextFieldState,
    /** The members in the athlete's order. */
    val members: ImmutableList<GearRow> = persistentListOf(),
    /** The athlete's other items, which "Add items" offers, by name. */
    val others: ImmutableList<GearRow> = persistentListOf(),
    /** Whether the collection holds as many items as it can. */
    val full: Boolean = false,
    /** The athlete tried to save without a name. */
    val nameMissing: Boolean = false,
    val saving: Boolean = false,
    val error: UiError? = null,
    val eventSink: (EditGearCollectionEvent) -> Unit,
) : CircuitUiState

sealed interface EditGearCollectionEvent {
    data object Back : EditGearCollectionEvent

    data object Retry : EditGearCollectionEvent

    /** Adds the items at [rkeys] at the end, in the order given, while there's room. */
    data class Add(val rkeys: ImmutableList<String>) : EditGearCollectionEvent

    data class Remove(val rkey: String) : EditGearCollectionEvent

    /** Moves the member at [from] to [to], both indexes into [EditGearCollectionState.members]. */
    data class Move(val from: Int, val to: Int) : EditGearCollectionEvent

    data object Save : EditGearCollectionEvent
}
