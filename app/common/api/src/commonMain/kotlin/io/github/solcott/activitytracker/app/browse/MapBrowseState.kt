package io.github.solcott.activitytracker.app.browse

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.MapCameraRequest
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.MapViewport
import io.github.solcott.activitytracker.app.SegmentsMap
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.textfield.TextFieldState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Where a name search looks: only what crosses the map's area, or everywhere. */
enum class NameScope {
    ThisArea,
    Everywhere,
}

/** How browsing starts before the athlete moves the map. */
enum class BrowseStart {
    /** Finding where to open: asking for or waiting on a location, or reading the latest activity. */
    Locating,

    /** The map was moved where it should open, or the athlete moved it. */
    Placed,

    /** No location and no activity with a map: the screen asks the athlete to move the map or search. */
    Nowhere,
}

/** What a map browse screen's list shows: name results, why there is nothing in the area yet, or the area's items. */
enum class BrowseList {
    /** A name is searched: its results. */
    Names,

    /** No map on this platform (web): a prompt to search by name. */
    WebPrompt,

    /** Finding where to open. */
    Locating,

    /** No location and no activity with a map: move the map or search. */
    Nowhere,

    /** The map shows more than is searched over. */
    TooWide,

    /** The area's items. */
    Area,
}

/**
 * What a map browse screen (segments/segment-browse, dive-sites/site-browse) shows of its map and name search, whatever it lists: the
 * search box, the name search's scope, the map's area and lines, where browsing started, the item tapped on the map and where the map
 * should move. A sub-state of the screen's state with its own sink, which the presenter's `MapBrowse` fills.
 */
@Immutable
data class MapBrowseState(
    /** The search box. A name search runs once typing pauses; blank browses the area. */
    val query: TextFieldState,
    /** Where a name search looks; only offered while [hasArea]. */
    val scope: NameScope = NameScope.ThisArea,
    /** Whether a map reported an area. Never on web, so there a name search always looks everywhere. */
    val hasArea: Boolean = false,
    /** The map shows more than is searched over, so nothing is listed until the athlete zooms in. */
    val tooWide: Boolean = false,
    /** How browsing started; [BrowseStart.Locating] until the map has somewhere to be. */
    val start: BrowseStart = BrowseStart.Locating,
    /** The lines the map draws: the area's items, or the name results. */
    val mapLines: ImmutableList<MapSegmentLine> = persistentListOf(),
    /** The item tapped on the map, or its row on a touch screen, marked in the list. */
    val selected: AtUri? = null,
    /** Where the map should move (a place to open at, or a selected row's line to frame); a new [MapCameraRequest.id] moves it once. */
    val camera: MapCameraRequest? = null,
    /**
     * The platform has a location and its permission hasn't been asked for yet: the UI asks, then sends [MapBrowseEvent.PermissionResult].
     */
    val askLocation: Boolean = false,
    val eventSink: (MapBrowseEvent) -> Unit = {},
) {
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

    /** Whether [uri] is the item tapped on the map (or its row on a touch screen), marked in the list. */
    fun isSelected(uri: AtUri): Boolean = uri == selected

    /** The event for a tap on the map that found the item [uri] (a line's URI as the map holds it), or none. */
    fun selectEvent(uri: String?): MapBrowseEvent = MapBrowseEvent.Select(uri?.let(::AtUri))

    /** The map's lines in drawing order with the selected one highlighted, and what a tap selects. */
    val segmentsMap: SegmentsMap
        get() = SegmentsMap.of(mapLines, selected?.value)

    /** [segmentsMap] with [hovered], the line under the pointer in a row or on the map (a UI's own state), highlighted too. */
    fun segmentsMap(hovered: String?): SegmentsMap = SegmentsMap.of(mapLines, selected?.value, hovered)
}

/** What a map browse screen's map and search controls change, which `MapBrowse` handles alone. */
sealed interface MapBrowseEvent {
    /** The map stopped moving and shows [viewport]. */
    data class ViewportChanged(val viewport: MapViewport) : MapBrowseEvent

    /** The answer to the location permission request [MapBrowseState.askLocation] asked for. */
    data class PermissionResult(val granted: Boolean) : MapBrowseEvent

    /** A tap on an item's line on the map, or null for a tap away from every line. */
    data class Select(val uri: AtUri?) : MapBrowseEvent

    data class ScopeChanged(val scope: NameScope) : MapBrowseEvent

    /** Searches the same name everywhere after nothing matched in this area. */
    data object SearchEverywhere : MapBrowseEvent
}
