package io.github.solcott.activitytracker.app.divesite

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.browse.MapBrowseState
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
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
    /** The search box, the name search's scope, the map and where browsing started, as segment browse has them too. */
    val browse: MapBrowseState,
    /** The area's sites (ui/content-loading); nothing loads while there's no area, it's too wide, or a name is searched. */
    val area: ContentState<SiteArea> = ContentState(SiteArea()),
    /** Sites named like the query, best match first; nothing loaded while the query is blank. */
    val results: PagedState<DiveSiteView, Unit>,
    val eventSink: (DiveSiteBrowseEvent) -> Unit = {},
) : CircuitUiState {
    /** The area's sites, nearest first: a copy Swift reads as an array, empty or not. */
    val areaSites: List<BrowsedSite>
        get() = area.data.sites.toList()

    /** Whether the area holds more sites than were read. */
    val areaCapped: Boolean
        get() = area.data.capped

    /** Whether [site] is the one tapped on the map, or its row on a touch screen, marked in the list. */
    fun isSelected(site: DiveSiteView): Boolean = browse.isSelected(site.uri)

    /** The event for a tap on [site]'s row on a touch screen: it selects the row, or opens it when it's selected. */
    fun tapRowEvent(site: DiveSiteView): DiveSiteBrowseEvent = DiveSiteBrowseEvent.TapRow(site.uri)

    /** The event for a click on [site]'s row with a pointer: opens it. */
    fun openEvent(site: DiveSiteView): DiveSiteBrowseEvent = DiveSiteBrowseEvent.Open(site.uri)
}

sealed interface DiveSiteBrowseEvent {
    data object Back : DiveSiteBrowseEvent

    /** Opens the site's page: a click on its row with a pointer. */
    data class Open(val uri: AtUri) : DiveSiteBrowseEvent

    /**
     * A tap on a site's row on a touch screen: opens its page when it's selected, else selects it, moving the map to show it when it's out
     * of view.
     */
    data class TapRow(val uri: AtUri) : DiveSiteBrowseEvent

    /** Loads the next page of name results; ignored while a page is loading, after a failed page, or when there are no more. */
    data object LoadMore : DiveSiteBrowseEvent

    /** Loads again what failed: the area, the name search, or the page of results that failed. */
    data object Retry : DiveSiteBrowseEvent
}
