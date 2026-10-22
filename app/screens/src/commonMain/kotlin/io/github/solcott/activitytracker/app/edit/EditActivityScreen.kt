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
import io.github.solcott.uistate.ContentState

/**
 * Edits the signed-in athlete's activity at [uri]: its title, description, dive site and photos, who sees it, and whether segment
 * leaderboards rank it.
 */
@CircuitSerializable(SessionScope::class) data class EditActivityScreen(val uri: String) : Screen

@Immutable
data class EditActivityState(
    /** The activity the form is filled from: loading or failed until it is, then loaded. */
    val content: ContentState<Unit>,
    /** Null while the activity loads. */
    val type: ActivityType?,
    val title: TextFieldState,
    /** Null while the activity loads. */
    val details: ActivityDetailsFields?,
    val saving: Boolean,
    /** Why a save failed. */
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
    /**
     * Whether the activity is excluded from segment leaderboards (segments/segment-leaderboards), as edited; null when that isn't offered:
     * it has no segment track, or its choice can't be read now.
     */
    val leaderboardExclusion: Boolean? = null,
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

    /** Saves after the warning that narrowing a Public activity can't recall published copies. */
    data object ConfirmNarrowing : EditActivityEvent

    /** Back to editing without saving, from that warning. */
    data object CancelNarrowing : EditActivityEvent

    /** Saves after the warning that the core charts will become as visible as the activity ([EditActivityState.confirmCoreCharts]). */
    data object ConfirmCoreCharts : EditActivityEvent

    /** Back to editing without saving, from that warning. */
    data object CancelCoreCharts : EditActivityEvent

    /** Excludes the activity from segment leaderboards, or ranks it again; saved with the rest. */
    data class LeaderboardExclusionChanged(val excluded: Boolean) : EditActivityEvent
}
