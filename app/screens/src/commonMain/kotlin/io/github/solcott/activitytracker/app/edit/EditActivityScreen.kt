package io.github.solcott.activitytracker.app.edit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityType

/** Edits the signed-in athlete's activity at [uri]: its title, description, dive site and photos. */
@CircuitSerializable(SessionScope::class) data class EditActivityScreen(val uri: String) : Screen

@Immutable
data class EditActivityState(
    /** Null while the activity loads. */
    val type: ActivityType?,
    val title: TextFieldState,
    /** Null while the activity loads. */
    val details: ActivityDetailsFields?,
    val saving: Boolean,
    val error: UiError?,
    /** The athlete went back with unsaved changes; ask before dropping them. */
    val confirmDiscard: Boolean,
    /** Who may see the activity and each detail category it has data for; null while it loads. */
    val visibility: VisibilityFields? = null,
    /** Saving would narrow a Public activity: copies already published (on the firehose, in other apps) can't be recalled, so ask first. */
    val confirmNarrowing: Boolean = false,
    /**
     * Saving an activity from before detail categories would make its core charts (elevation, distance) as visible as the activity (a
     * `Visibility` value), wider than its charts are now: ask first. Null when it wouldn't.
     */
    val confirmCoreCharts: String? = null,
    val eventSink: (EditActivityEvent) -> Unit,
) : CircuitUiState

sealed interface EditActivityEvent {
    data object Save : EditActivityEvent

    /** Leaves, or asks first ([EditActivityState.confirmDiscard]) when there are unsaved changes. */
    data object Back : EditActivityEvent

    data object Discard : EditActivityEvent

    data object KeepEditing : EditActivityEvent

    /** Retries a failed load. */
    data object Retry : EditActivityEvent

    /** Who may see the activity: a `Visibility` value. Narrows wider categories with it. */
    data class VisibilityChanged(val visibility: String) : EditActivityEvent

    /** Who may see one detail category (a `DetailCategory` token): a `Visibility` value, no wider than the activity's. */
    data class CategoryVisibilityChanged(val category: String, val visibility: String) : EditActivityEvent

    /** Saves after the warning that narrowing a Public activity can't recall published copies. */
    data object ConfirmNarrowing : EditActivityEvent

    /** Back to editing without saving, from that warning. */
    data object CancelNarrowing : EditActivityEvent

    /** Saves after the warning that the core charts will become as visible as the activity ([EditActivityState.confirmCoreCharts]). */
    data object ConfirmCoreCharts : EditActivityEvent

    /** Back to editing without saving, from that warning. */
    data object CancelCoreCharts : EditActivityEvent
}
