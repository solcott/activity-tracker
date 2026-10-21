package io.github.solcott.activitytracker.app.feed

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.activitylist.ActivityListEvent
import io.github.solcott.activitytracker.app.feedfilter.FeedFilter
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterScreen
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import kotlinx.collections.immutable.ImmutableList

@CircuitSerializable(SessionScope::class) data object FeedScreen : Screen

/** Whose activities the feed shows, picked from its title. Clubs will make this a sealed type, with the presenter listing the viewer's. */
enum class FeedScope {
    /** Athletes the viewer follows, plus the viewer's own. */
    Following,

    /** Only the viewer's own. */
    Mine,
}

@Immutable
data class FeedState(
    val activities: ImmutableList<ActivityView>,
    val refreshing: Boolean,
    val error: UiError?,
    val pendingWrites: Int,
    /** The signed-in athlete, so the UI can leave likes off their own activities. */
    val viewerDid: String,
    /** Whether GPS recording is available here (phones only); otherwise only manual entry is offered. */
    val canRecord: Boolean,
    val scope: FeedScope,
    /** The scopes the title's menu offers. */
    val scopes: ImmutableList<FeedScope>,
    /** What [activities] are narrowed to, as [filterScreen] last reported it. */
    val filter: FeedFilter,
    /** The filter control, kept across changes so it keeps its state, and replaced only by [FeedEvent.ClearFilters]. */
    val filterScreen: FeedFilterScreen,
    /** Whether to ask, once, whether to turn segment matching on ([FeedEvent.AnswerSegments]). */
    val askSegments: Boolean = false,
    val eventSink: (FeedEvent) -> Unit,
) : CircuitUiState

sealed interface FeedEvent {
    data object Refresh : FeedEvent

    data class SetScope(val scope: FeedScope) : FeedEvent

    /** The filter control changed the filter. */
    data class FilterChanged(val filter: FeedFilter) : FeedEvent

    /** Drops the type and date filters from outside the control (the empty feed's Clear), keeping the scope. */
    data object ClearFilters : FeedEvent

    /** A tap on one of [FeedState.activities]. */
    data class Activity(val event: ActivityListEvent) : FeedEvent

    data object Record : FeedEvent

    data object AddManual : FeedEvent

    data object Import : FeedEvent

    /**
     * The athlete's answer to [FeedState.askSegments]: turn segment matching [on], or leave it off. Either way, they're not asked again.
     */
    data class AnswerSegments(val on: Boolean) : FeedEvent
}
