package io.github.solcott.activitytracker.app.browse

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.MapViewport
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.activitytracker.textfield.TextFieldState
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** How long typing must pause before a browse screen's name search runs, so each keystroke isn't a request. */
val NAME_SEARCH_DEBOUNCE = 300.milliseconds

/** How long browsing waits for a first location fix before opening at the latest activity instead. */
val LOCATION_TIMEOUT = 5.seconds

/**
 * What a map browse screen keeps between compositions (segments/segment-browse, dive-sites/site-browse): the map's area and where it was
 * sent and the item tapped on the map ([MapSelection]), how browsing started, the name search's scope, and the area's retry key. Moving the
 * map doesn't restart a name search, which read its area when it began ([nameViewport]). A presenter holds one in `rememberRetained`,
 * starts it with [locate], and publishes it as its state's [MapBrowseState] ([state]), whose [MapBrowseEvent]s it handles alone
 * ([onEvent]).
 */
@Stable
class MapBrowse(permission: Boolean?) : MapSelection() {
    var scope by mutableStateOf(NameScope.ThisArea)

    var start by mutableStateOf(BrowseStart.Locating)
        private set

    /** Whether location may be read: false where there's none, true once granted, null until the athlete is asked. */
    var permission by mutableStateOf(permission)
    var areaRetry by mutableIntStateOf(0)

    /** The map's area when the current name search's first page loaded, which its later pages search too. */
    var nameViewport: MapViewport? = null

    /** What the screen's state shows of the map and search: [query] and the [mapLines] the presenter derived, and this holder's own. */
    fun state(query: TextFieldState, mapLines: ImmutableList<MapSegmentLine>): MapBrowseState =
        MapBrowseState(
            query = query,
            scope = scope,
            hasArea = viewport != null,
            tooWide = viewport?.tooWide == true,
            start = start,
            mapLines = mapLines,
            selected = selected,
            camera = camera,
            askLocation = permission == null,
            eventSink = ::onEvent,
        )

    fun onEvent(event: MapBrowseEvent) {
        when (event) {
            is MapBrowseEvent.ViewportChanged -> viewportChanged(event.viewport)
            is MapBrowseEvent.PermissionResult -> permission = event.granted
            is MapBrowseEvent.Select -> selected = event.uri
            is MapBrowseEvent.ScopeChanged -> scope = event.scope
            MapBrowseEvent.SearchEverywhere -> scope = NameScope.Everywhere
        }
    }

    /**
     * A tap on the row [uri] on a touch screen: [open]s it when it's selected, else selects it ([selectRow]) with its line or place
     * [points], when it can lie off the map.
     */
    fun tapRow(uri: AtUri, points: () -> List<GeoPoint>?, open: () -> Unit) {
        if (uri == selected) open() else selectRow(uri, points())
    }

    /** The map stopped and shows [shown]. */
    override fun viewportChanged(shown: MapViewport) {
        super.viewportChanged(shown)
        // The athlete found somewhere themselves.
        if (start == BrowseStart.Nowhere && !shown.tooWide) start = BrowseStart.Placed
    }

    /**
     * Finds where to open, once permission is known: a first location fix within [LOCATION_TIMEOUT] when granted, else where the latest
     * activity with a map started, else nowhere.
     */
    suspend fun locate(tracker: LocationTracker, feed: FeedRepository) {
        val granted = permission ?: return
        if (start != BrowseStart.Locating) return
        val here =
            if (granted) withTimeoutOrNull(LOCATION_TIMEOUT) { runSuspendCatching { tracker.updates().first() }.getOrNull() } else null
        val at = here?.let { GeoPoint(it.latitude, it.longitude) } ?: feed.latestOwnStart()
        // The athlete may have moved the map meanwhile.
        if (start == BrowseStart.Locating) placeAt(at)
    }

    private fun placeAt(at: GeoPoint?) {
        if (at == null) {
            start = BrowseStart.Nowhere
        } else {
            centreOn(at)
            start = BrowseStart.Placed
        }
    }

    /** The viewport an area browse loads: while no name is searched ([searching]), the map is close enough, and browsing has started. */
    fun areaRequest(searching: Boolean): MapViewport? = viewport?.takeIf { !searching && !it.tooWide && start != BrowseStart.Locating }

    /** The box a name search in [scope] covers: the map's in This area when it's close enough to search, else everywhere (null). */
    fun nameBox(viewport: MapViewport?): MapViewport? = viewport?.takeIf { scope == NameScope.ThisArea && !it.tooWide }

    companion object {
        /** Whether [tracker]'s location may be read, as a new [MapBrowse] starts: false where there's none, null until asked. */
        fun initialPermission(tracker: LocationTracker): Boolean? =
            when {
                !tracker.isSupported -> false
                tracker.hasPermission() -> true
                else -> null
            }
    }
}
