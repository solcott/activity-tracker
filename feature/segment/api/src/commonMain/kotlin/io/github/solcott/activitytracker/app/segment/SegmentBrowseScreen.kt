package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.browse.MapBrowseState
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.PagedState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Trails and roads around a place on a map and in a list, and those found by name (segments/segment-browse), each with the segments on it;
 * a segment group on no trail or road is a row of its own. The Search tab shows it as its Trails & roads mode. Web has no map, so it
 * browses by name only.
 */
@CircuitSerializable(SessionScope::class) data object SegmentBrowseScreen : Screen

/** A row of the area's list, and how far its nearest segment in the area comes to the map's centre, in metres. */
@Immutable data class BrowsedRow(val row: BrowseRow, val metersAway: Int)

/**
 * An area's rows, loaded: its trails, roads and loose segment groups ([rows]) nearest the map's centre first, its [lifts] (drawn on the map
 * only), and whether it holds more segments than were read ([capped]).
 */
@Immutable
data class AreaRuns(
    val rows: ImmutableList<BrowsedRow> = persistentListOf(),
    val lifts: ImmutableList<MapSegmentLine> = persistentListOf(),
    val capped: Boolean = false,
)

@Immutable
data class SegmentBrowseState(
    /** The search box, the name search's scope, the map and where browsing started, as dive site browse has them too. */
    val browse: MapBrowseState,
    /** Only segments of this `SegmentSportGroup`; null for every group. */
    val sport: String? = null,
    /** The area's segments (ui/content-loading); nothing loads while there's no area, it's too wide, or a name is searched. */
    val area: ContentState<AreaRuns> = ContentState(AreaRuns()),
    /** Trails, roads and loose segment groups found by the query, best match first; nothing loaded while the query is blank. */
    val results: PagedState<BrowseRow, Unit>,
    val eventSink: (SegmentBrowseEvent) -> Unit = {},
) : CircuitUiState {
    /** The area's rows, nearest first: a copy Swift reads as an array, empty or not. */
    val areaRows: List<BrowsedRow>
        get() = area.data.rows.toList()

    /** Whether the area holds more segments than were read. */
    val areaCapped: Boolean
        get() = area.data.capped

    /** The sport chips' groups after All, in order. */
    val sports: List<String>
        get() = SegmentSportGroup.all

    /** Whether [row] is the one selected ([BrowseRow.key]), marked in the list. */
    fun isSelected(row: BrowseRow): Boolean = browse.isSelected(row.key)

    /** The event for a tap on [row] on a touch screen: it selects the row, or opens it when it's selected. */
    fun tapRowEvent(row: BrowseRow): SegmentBrowseEvent = SegmentBrowseEvent.TapRow(row.key)

    /** The event for a click on [row] with a pointer: opens its Trail page, or its segment group's segment. */
    fun openEvent(row: BrowseRow): SegmentBrowseEvent = SegmentBrowseEvent.Open(row.key)
}

sealed interface SegmentBrowseEvent {
    data object Back : SegmentBrowseEvent

    /** Opens a row ([BrowseRow.key]): a path's Trail page, or a segment's page. A click on its row with a pointer. */
    data class Open(val uri: AtUri) : SegmentBrowseEvent

    /**
     * A tap on a row on a touch screen: opens it when it's selected, else selects it, moving the map to show its line when none of it is in
     * view.
     */
    data class TapRow(val uri: AtUri) : SegmentBrowseEvent

    /** Only segments of [sport], a `SegmentSportGroup` value, or every group when null. */
    data class SportChanged(val sport: String?) : SegmentBrowseEvent

    /** Loads the next page of name results; ignored while a page is loading, after a failed page, or when there are no more. */
    data object LoadMore : SegmentBrowseEvent

    /** Loads again what failed: the area, the name search, or the page of results that failed. */
    data object Retry : SegmentBrowseEvent
}
