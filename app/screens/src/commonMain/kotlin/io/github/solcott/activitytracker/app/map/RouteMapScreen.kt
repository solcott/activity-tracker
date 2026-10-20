package io.github.solcott.activitytracker.app.map

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.ShownRoute
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityType

/** The route of the activity at [uri] on a full-screen map the viewer can pan and zoom (`feed/route-map-viewer`). */
@CircuitSerializable(SessionScope::class) data class RouteMapScreen(val uri: String) : Screen

@Immutable
data class RouteMapState(
    /** What the map draws, as the activity's detail page draws it for this viewer; null while loading, or when it has no route. */
    val route: ShownRoute?,
    /** The activity's title, when it has one. */
    val title: String?,
    /** The activity's type, for a title when it has none; null while loading. */
    val activityType: ActivityType?,
    val isLoading: Boolean,
    val error: UiError?,
    val eventSink: (RouteMapEvent) -> Unit,
) : CircuitUiState

sealed interface RouteMapEvent {
    data object Back : RouteMapEvent
}
