package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.uistate.ContentState

/**
 * The line of the segment at [uri] on a full-screen map the viewer can pan and zoom, with its data credit (`segments/segment-map-viewer`).
 */
@CircuitSerializable(SessionScope::class) data class SegmentMapScreen(val uri: String) : Screen

@Immutable
data class SegmentMapState(
    /** The segment's map, name and credit, loaded (ui/content-loading); null until it first loads. */
    val content: ContentState<SegmentMapContent?> = ContentState(null),
    val eventSink: (SegmentMapEvent) -> Unit = {},
) : CircuitUiState

@Immutable
data class SegmentMapContent(
    /** The segment's line at precision 5, as the segment screen draws it; null when it doesn't decode. */
    val polyline: String?,
    /** The segment's name. */
    val name: String,
    /** Credit for the data its line came from, overlaid on the map; null for an athlete's segment. */
    val credit: SegmentCredit?,
)

sealed interface SegmentMapEvent {
    data object Back : SegmentMapEvent

    /** Loads the segment again after it failed to. */
    data object Retry : SegmentMapEvent
}
