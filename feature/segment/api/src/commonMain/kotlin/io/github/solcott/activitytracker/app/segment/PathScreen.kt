package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.MapCameraRequest
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.MapViewport
import io.github.solcott.activitytracker.app.SegmentsMap
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.PathView
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The Trail page (segments/path-page): a trail or road, its line, and every segment on it the athlete may find, one row per segment group.
 * [sport], a `SegmentSportGroup` value, is the browse filter it was opened with.
 */
@CircuitSerializable(SessionScope::class) data class PathScreen(val uri: String, val sport: String? = null) : Screen

/** The Trail page's content: the path, its segment groups, longest first, and its data's [credit]. */
@Immutable
data class PathContent(
    val path: PathView? = null,
    val groups: ImmutableList<SegmentGroupRow> = persistentListOf(),
    val credit: SegmentCredit? = null,
)

@Immutable
data class PathState(
    /** The path and its segment groups (ui/content-loading). */
    val content: ContentState<PathContent>,
    /** Only segments of this `SegmentSportGroup`, or every group when null. */
    val sport: String? = null,
    /** The lines the map draws: the path's, muted under them, and each segment group's shown segment. */
    val mapLines: ImmutableList<MapSegmentLine> = persistentListOf(),
    /** The segment group tapped on the map or in the list, by its shown segment, marked in the list. */
    val selected: AtUri? = null,
    /** Where the map should move: the whole path when loaded, or a selected row's line; a new [MapCameraRequest.id] moves it once. */
    val camera: MapCameraRequest? = null,
    val eventSink: (PathEvent) -> Unit = {},
) : CircuitUiState {
    /** The path, once loaded. */
    val path: PathView?
        get() = content.data.path

    /** Who to credit for the path's line, as a segment page credits its line. */
    val credit: SegmentCredit?
        get() = content.data.credit

    /** The segment groups listed: of [sport] when one is chosen. Swift reads it as an array, empty or not. */
    val groups: List<SegmentGroupRow>
        get() = content.data.groups.toList()

    /** Whether "Show all sports" is offered: while the list is narrowed to one sport group. */
    val offersAllSports: Boolean
        get() = sport != null

    /** Whether there's nothing to list once loaded. */
    val empty: Boolean
        get() = content.data.path != null && content.data.groups.isEmpty()

    /** The map's lines with the selected group highlighted. */
    val segmentsMap: SegmentsMap
        get() = SegmentsMap.of(mapLines, selected?.value)

    /** [segmentsMap] with [hovered], a row or line under the pointer (a UI's own state), highlighted too. */
    fun segmentsMap(hovered: String?): SegmentsMap = SegmentsMap.of(mapLines, selected?.value, hovered)

    /** Whether [group]'s row is the one selected. */
    fun isSelected(group: SegmentGroupRow): Boolean = group.shown.uri == selected

    /** The event for a tap on the map that found [uri] (a line's URI as the map holds it), or none. */
    fun selectEvent(uri: String?): PathEvent = PathEvent.Select(uri?.let(::AtUri))

    /** The event for a tap on [group]'s row on a touch screen: it selects the row, or opens it when it's selected. */
    fun tapRowEvent(group: SegmentGroupRow): PathEvent = PathEvent.TapRow(group.shown.uri)

    /** The event for a click on [group]'s row with a pointer: opens it. */
    fun openEvent(group: SegmentGroupRow): PathEvent = PathEvent.Open(group.shown.uri)
}

sealed interface PathEvent {
    data object Back : PathEvent

    data object Retry : PathEvent

    /** The map stopped moving and shows [viewport]. */
    data class ViewportChanged(val viewport: MapViewport) : PathEvent

    /** A tap on a segment's line on the map, or null for a tap away from every line. */
    data class Select(val uri: AtUri?) : PathEvent

    /** A tap on a row on a touch screen: opens it when selected, else selects it, moving the map to show its line when off screen. */
    data class TapRow(val uri: AtUri) : PathEvent

    /** Opens a segment group's page: a click on its row, or a tap on the selected one. */
    data class Open(val uri: AtUri) : PathEvent

    /** Lists every sport group's segments. */
    data object ShowAllSports : PathEvent
}
