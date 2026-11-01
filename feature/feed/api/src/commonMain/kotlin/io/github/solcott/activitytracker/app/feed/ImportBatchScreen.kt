package io.github.solcott.activitytracker.app.feed

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.activitylist.ActivityListEvent
import io.github.solcott.activitytracker.app.moderation.Revealed
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.uistate.PagedState

/**
 * The activities of one batch import (feed/import-cards), opened from its card on the timeline: [actor]'s (a DID) activities saved with
 * `importBatch` [batch] that the viewer may see, newest started first. [name] is the athlete as the card showed them, for the title.
 */
@CircuitSerializable(SessionScope::class) data class ImportBatchScreen(val actor: String, val batch: String, val name: String) : Screen

@Immutable
data class ImportBatchState(
    val name: String,
    /** The batch's activities loaded so far, and how loading them stands (ui/content-loading). */
    val activities: PagedState<ActivityView, Unit>,
    /** The signed-in athlete, so the UI can leave likes off their own activities. */
    val viewerDid: String,
    /** Labeled photos the viewer revealed on this screen. */
    val revealed: Revealed = Revealed(),
    /** An action that failed (a like); load failures are [activities]'. */
    val error: UiError? = null,
    val eventSink: (ImportBatchEvent) -> Unit,
) : CircuitUiState

sealed interface ImportBatchEvent {
    data object Back : ImportBatchEvent

    /** Loads the next page; ignored while a page is loading, after a failed page, or when there are no more. */
    data object LoadMore : ImportBatchEvent

    /** Loads again what failed to load, and only that: the first page, or the page that failed. */
    data object Retry : ImportBatchEvent

    /** A tap on one of [ImportBatchState.activities]. */
    data class Activity(val event: ActivityListEvent) : ImportBatchEvent
}
