package io.github.solcott.activitytracker.app.divesite

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.MapCameraRequest
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.MapViewport
import io.github.solcott.activitytracker.app.SegmentsMap
import io.github.solcott.activitytracker.app.segment.BrowseList
import io.github.solcott.activitytracker.app.segment.BrowseStart
import io.github.solcott.activitytracker.app.segment.NameScope
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.textfield.TextFieldState
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.PagedState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Dive sites around a place on a map and in a list, and sites found by name (dive-sites/site-browse). The Search tab shows it as its Dive
 * sites mode. Web has no map, so it finds sites by name only.
 */
@CircuitSerializable(SessionScope::class) data object DiveSiteBrowseScreen : Screen

/** A site in the area's list: the site, and how far its centre is from the map's centre, in metres. */
@Immutable data class BrowsedSite(val site: DiveSiteView, val metersAway: Int)

/** An area's sites, loaded: nearest the map's centre first, and whether it holds more than were read ([capped]). */
@Immutable data class SiteArea(val sites: ImmutableList<BrowsedSite> = persistentListOf(), val capped: Boolean = false)

@Immutable
data class DiveSiteBrowseState(
    /** The search box. A name search runs once typing pauses; blank browses the area. */
    val query: TextFieldState,
    /** Where a name search looks; only offered while [hasArea]. */
    val scope: NameScope = NameScope.ThisArea,
    /** Whether a map reported an area. Never on web, so there a name search always looks everywhere. */
    val hasArea: Boolean = false,
    /** The map shows more than sites are searched over, so nothing is listed until the athlete zooms in. */
    val tooWide: Boolean = false,
    /** How browsing started; [BrowseStart.Locating] until the map has somewhere to be. */
    val start: BrowseStart = BrowseStart.Locating,
    /** The area's sites (ui/content-loading); nothing loads while there's no area, it's [tooWide], or a name is searched. */
    val area: ContentState<SiteArea> = ContentState(SiteArea()),
    /** Sites named like [query], best match first; nothing loaded while the query is blank. */
    val results: PagedState<DiveSiteView, Unit>,
    /** The outlines the map draws: the area's sites, or the name results. */
    val mapLines: ImmutableList<MapSegmentLine> = persistentListOf(),
    /** The site tapped on the map, marked in the list. */
    val selected: AtUri? = null,
    /** Where the map should move; a new [MapCameraRequest.id] moves it once. */
    val camera: MapCameraRequest? = null,
    /**
     * The platform has a location and its permission hasn't been asked for yet: the UI asks, then sends
     * [DiveSiteBrowseEvent.PermissionResult].
     */
    val askLocation: Boolean = false,
    val eventSink: (DiveSiteBrowseEvent) -> Unit = {},
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

    /** Whether no name match offers Search everywhere: when This area was searched over an area close enough to search. */
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

    /** The area's sites, nearest first: a copy Swift reads as an array, empty or not. */
    val areaSites: List<BrowsedSite>
        get() = area.data.sites.toList()

    /** Whether the area holds more sites than were read. */
    val areaCapped: Boolean
        get() = area.data.capped

    /** The event for a tap on the map that found the site [uri] (an outline's URI as the map holds it), or none. */
    fun selectEvent(uri: String?): DiveSiteBrowseEvent = DiveSiteBrowseEvent.Select(uri?.let(::AtUri))

    /** Whether [site] is the one tapped on the map, marked in the list. */
    fun isSelected(site: DiveSiteView): Boolean = site.uri == selected

    /** The map's outlines in drawing order with the selected site highlighted, and what a tap selects. */
    val segmentsMap: SegmentsMap
        get() = SegmentsMap.of(mapLines, selected?.value)
}

sealed interface DiveSiteBrowseEvent {
    data object Back : DiveSiteBrowseEvent

    /** The map stopped moving and shows [viewport]. */
    data class ViewportChanged(val viewport: MapViewport) : DiveSiteBrowseEvent

    /** The answer to the location permission request [DiveSiteBrowseState.askLocation] asked for. */
    data class PermissionResult(val granted: Boolean) : DiveSiteBrowseEvent

    /** A tap on a site's outline on the map, or null for a tap away from every outline. */
    data class Select(val uri: AtUri?) : DiveSiteBrowseEvent

    /** Opens the site's page. */
    data class Open(val uri: AtUri) : DiveSiteBrowseEvent

    data class ScopeChanged(val scope: NameScope) : DiveSiteBrowseEvent

    /** Loads the next page of name results; ignored while a page is loading, after a failed page, or when there are no more. */
    data object LoadMore : DiveSiteBrowseEvent

    /** Loads again what failed: the area, the name search, or the page of results that failed. */
    data object Retry : DiveSiteBrowseEvent

    /** Searches the same name everywhere after nothing matched in this area. */
    data object SearchEverywhere : DiveSiteBrowseEvent
}
