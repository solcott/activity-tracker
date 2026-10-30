package io.github.solcott.activitytracker.app.map

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.ShownRoute
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.uistate.ContentState

/** The route of the activity at [uri] on a full-screen map the viewer can pan and zoom (`feed/route-map-viewer`). */
@CircuitSerializable(SessionScope::class) data class RouteMapScreen(val uri: String) : Screen

@Immutable
data class RouteMapState(
    /** The activity's map and title, loaded (ui/content-loading); null until it first loads. */
    val content: ContentState<RouteMapContent?> = ContentState(null),
    val eventSink: (RouteMapEvent) -> Unit = {},
) : CircuitUiState

@Immutable
data class RouteMapContent(
    /** What the map draws, as the activity's detail page draws it for this viewer; null when it has no route. */
    val route: ShownRoute?,
    /** The activity's title, when it has one. */
    val title: String?,
    /** The activity's type, for a title when it has none. */
    val activityType: ActivityType,
)

sealed interface RouteMapEvent {
    data object Back : RouteMapEvent

    /** Loads the activity again after it failed to. */
    data object Retry : RouteMapEvent
}
