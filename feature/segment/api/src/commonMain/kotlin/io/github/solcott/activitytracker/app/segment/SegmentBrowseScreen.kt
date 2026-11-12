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
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.textfield.TextFieldState
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.PagedState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Segments around a place on a map and in a list, and segments found by name (segments/segment-browse). The Search tab shows it as its
 * Segments mode. Web has no map, so it browses by name only.
 */
@CircuitSerializable(SessionScope::class) data object SegmentBrowseScreen : Screen

/** Where a name search looks: only segments crossing the map's area, or everywhere. */
enum class NameScope {
    ThisArea,
    Everywhere,
}

/** A run in the area's list: the segment, and how far its line comes to the map's centre, in metres. */
@Immutable data class BrowsedSegment(val segment: SegmentView, val metersAway: Int)

/**
 * An area's segments, loaded: its [runs] nearest the map's centre first, its [lifts] (drawn on the map only), and whether it holds more
 * than were read ([capped]).
 */
@Immutable
data class AreaRuns(
    val runs: ImmutableList<BrowsedSegment> = persistentListOf(),
    val lifts: ImmutableList<MapSegmentLine> = persistentListOf(),
    val capped: Boolean = false,
)

/** How browsing starts before the athlete moves the map. */
enum class BrowseStart {
    /** Finding where to open: asking for or waiting on a location, or reading the latest activity. */
    Locating,

    /** The map was moved where it should open, or the athlete moved it. */
    Placed,

    /** No location and no activity with a map: the screen asks the athlete to move the map or search. */
    Nowhere,
}

@Immutable
data class SegmentBrowseState(
    /** The search box. A name search runs once typing pauses; blank browses the area. */
    val query: TextFieldState,
    /** Only segments of this `SegmentSportGroup`; null for every group. */
    val sport: String? = null,
    /** Where a name search looks; only offered while [hasArea]. */
    val scope: NameScope = NameScope.ThisArea,
    /** Whether a map reported an area. Never on web, so there a name search always looks everywhere. */
    val hasArea: Boolean = false,
    /** The map shows more than segments are searched over, so nothing is listed until the athlete zooms in. */
    val tooWide: Boolean = false,
    /** How browsing started; [BrowseStart.Locating] until the map has somewhere to be. */
    val start: BrowseStart = BrowseStart.Locating,
    /** The area's segments (ui/content-loading); nothing loads while there's no area, it's [tooWide], or a name is searched. */
    val area: ContentState<AreaRuns> = ContentState(AreaRuns()),
    /** Segments named like [query], best match first; nothing loaded while the query is blank. */
    val results: PagedState<SegmentView, Unit>,
    /** The lines the map draws: the area's runs and lifts, or the name results. */
    val mapLines: ImmutableList<MapSegmentLine> = persistentListOf(),
    /** The segment tapped on the map, marked in the list. */
    val selected: AtUri? = null,
    /** Where the map should move; a new [MapCameraRequest.id] moves it once. */
    val camera: MapCameraRequest? = null,
    /**
     * The platform has a location and its permission hasn't been asked for yet: the UI asks, then sends
     * [SegmentBrowseEvent.PermissionResult].
     */
    val askLocation: Boolean = false,
    val eventSink: (SegmentBrowseEvent) -> Unit = {},
) : CircuitUiState {
    /** Whether a name is being searched rather than the area browsed. */
    val searchingNames: Boolean
        get() = query.text.isNotBlank()

    /** The searched name as the results' messages quote it. */
    val nameQuery: String
        get() = query.text.trim().toString()

    /** Whether This area and Everywhere are offered: while a name is searched over a map's area. */
    val offersScope: Boolean
        get() = hasArea && searchingNames

    /**
     * Whether no name match offers Search everywhere: when This area was searched. Zoomed out too far, This area already searched
     * everywhere, so there's nowhere wider to offer.
     */
    val offersSearchEverywhere: Boolean
        get() = hasArea && scope == NameScope.ThisArea && !tooWide

    /** What the list shows, on a platform with a map ([mapSupported]) or without one (web). */
    fun listShows(mapSupported: Boolean): BrowseList =
        when {
            searchingNames -> BrowseList.Names
            !mapSupported -> BrowseList.WebPrompt
            start == BrowseStart.Locating -> BrowseList.Locating
            start == BrowseStart.Nowhere -> BrowseList.Nowhere
            tooWide -> BrowseList.TooWide
            else -> BrowseList.Area
        }

    /** The area's runs, nearest first: a copy Swift reads as an array, empty or not. */
    val areaRuns: List<BrowsedSegment>
        get() = area.data.runs.toList()

    /** Whether the area holds more runs than were read. */
    val areaCapped: Boolean
        get() = area.data.capped

    /** The sport chips' groups after All, in order. */
    val sports: List<String>
        get() = SegmentSportGroup.all

    /** The map's lines in drawing order with the selected run highlighted, and what a tap selects. */
    val segmentsMap: SegmentsMap
        get() = SegmentsMap.of(mapLines, selected?.value)
}

sealed interface SegmentBrowseEvent {
    data object Back : SegmentBrowseEvent

    /** The map stopped moving and shows [viewport]. */
    data class ViewportChanged(val viewport: MapViewport) : SegmentBrowseEvent

    /** The answer to the location permission request [SegmentBrowseState.askLocation] asked for. */
    data class PermissionResult(val granted: Boolean) : SegmentBrowseEvent

    /** A tap on a segment's line on the map, or null for a tap away from every line. */
    data class Select(val uri: AtUri?) : SegmentBrowseEvent

    /** Opens the segment's page. */
    data class Open(val uri: AtUri) : SegmentBrowseEvent

    data class ScopeChanged(val scope: NameScope) : SegmentBrowseEvent

    /** Only segments of [sport], a `SegmentSportGroup` value, or every group when null. */
    data class SportChanged(val sport: String?) : SegmentBrowseEvent

    /** Loads the next page of name results; ignored while a page is loading, after a failed page, or when there are no more. */
    data object LoadMore : SegmentBrowseEvent

    /** Loads again what failed: the area, the name search, or the page of results that failed. */
    data object Retry : SegmentBrowseEvent

    /** Searches the same name everywhere after nothing matched in this area. */
    data object SearchEverywhere : SegmentBrowseEvent
}
