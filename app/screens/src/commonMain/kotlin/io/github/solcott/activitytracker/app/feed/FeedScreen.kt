package io.github.solcott.activitytracker.app.feed

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import kotlinx.collections.immutable.ImmutableList

@CircuitSerializable(SessionScope::class) data object FeedScreen : Screen

@Immutable
data class FeedState(
    val activities: ImmutableList<ActivityView>,
    val refreshing: Boolean,
    val error: UiError?,
    val pendingWrites: Int,
    /** The signed-in athlete, so the UI can leave kudos off their own activities. */
    val viewerDid: String,
    /** Whether GPS recording is available here (phones only); otherwise only manual entry is offered. */
    val canRecord: Boolean,
    val eventSink: (FeedEvent) -> Unit,
) : CircuitUiState

sealed interface FeedEvent {
    data object Refresh : FeedEvent

    data class Open(val activity: ActivityView) : FeedEvent

    data class OpenAuthor(val activity: ActivityView) : FeedEvent

    data class ToggleKudos(val activity: ActivityView) : FeedEvent

    data object Record : FeedEvent

    data object AddManual : FeedEvent

    data object Import : FeedEvent

    data object OpenMyProfile : FeedEvent
}
