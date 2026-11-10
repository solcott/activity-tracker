package io.github.solcott.activitytracker.app.gear

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** The athlete's gear collection at [rkey]: its members in order, retired ones marked. */
@CircuitSerializable(SessionScope::class) data class GearCollectionScreen(val rkey: String) : Screen

/** A collection as its page shows it: [members] in the athlete's order, those of deleted items left out. */
@Immutable
data class GearCollectionDetail(val name: String = "", val notes: String? = null, val members: ImmutableList<GearRow> = persistentListOf())

@Immutable
data class GearCollectionState(
    val content: ContentState<GearCollectionDetail>,
    /** Asking whether to delete the collection. */
    val confirmingDelete: Boolean = false,
    val deleting: Boolean = false,
    val error: UiError? = null,
    val eventSink: (GearCollectionEvent) -> Unit,
) : CircuitUiState {
    /** The page's title: the collection's name, or "Collections" until it is known. */
    val title: AppText
        get() = content.data.name.ifEmpty { null }?.let(AppText::written) ?: AppText.named("gear_collections")

    /** Whether Edit and Delete show: once the collection's name is known. */
    val showsActions: Boolean
        get() = content.data.name.isNotEmpty()

    /** Whether Edit and Delete can be used: not while the collection is being deleted. */
    val actionsEnabled: Boolean
        get() = !deleting
}

sealed interface GearCollectionEvent {
    data object Back : GearCollectionEvent

    data object Retry : GearCollectionEvent

    /** Opens the member item at [rkey]. */
    data class Open(val rkey: String) : GearCollectionEvent

    data object Edit : GearCollectionEvent

    /** Asks to delete ([GearCollectionState.confirmingDelete]). */
    data object Delete : GearCollectionEvent

    /** Deletes the collection, leaving its items and every activity's gear as they are. */
    data object ConfirmDelete : GearCollectionEvent

    data object DismissDelete : GearCollectionEvent
}
