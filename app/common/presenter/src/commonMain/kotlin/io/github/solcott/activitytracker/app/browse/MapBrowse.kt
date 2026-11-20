package io.github.solcott.activitytracker.app.browse

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.MapViewport
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.PlaceLocator
import io.github.solcott.activitytracker.location.e7ToDegrees
import io.github.solcott.activitytracker.textfield.TextFieldState
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** How long typing must pause before a browse screen's name search runs, so each keystroke isn't a request. */
val NAME_SEARCH_DEBOUNCE = 300.milliseconds

/**
 * How long after opening a location may still arrive and move the map (segments/segment-browse), once browsing opened at the latest
 * activity or nowhere because the location didn't answer within its locator's hold.
 */
val LATE_LOCATION_LIMIT = 30.seconds

/**
 * How near, in degrees, a reported centre must be to where the map was sent to count as the map arriving there, not the athlete moving it.
 */
private const val ARRIVED_DEGREES = 0.001

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

    /**
     * Browsing opened at a fallback (the latest activity, or nowhere) and the athlete hasn't moved the map since, so a late location may
     * still move it. [fallbackAt] is where the map was sent, null for nowhere; [arrived] is whether the map has reported being there.
     */
    private var provisional = false
    private var fallbackAt: GeoPoint? = null
    private var arrived = false

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
        if (provisional) followFallback(shown)
        // The athlete found somewhere themselves.
        if (start == BrowseStart.Nowhere && !shown.tooWide) start = BrowseStart.Placed
    }

    /** Whether [shown] is the athlete moving a map that opened at a fallback, which keeps a late location from moving it. */
    private fun followFallback(shown: MapViewport) {
        val at = fallbackAt
        when {
            // Opened nowhere: the athlete moved it once they zoomed in somewhere.
            at == null -> if (!shown.tooWide) provisional = false
            shown.centres(at) -> arrived = true
            // A viewport elsewhere before the map got there is the map's own start, not the athlete.
            arrived -> provisional = false
        }
    }

    /**
     * Finds where to open, once permission is known: the [locator]'s location when it answers within its hold, else [fallback] (where the
     * latest activity with a map started), else nowhere. A location that answers later, within [LATE_LOCATION_LIMIT], still moves the map
     * while the athlete hasn't moved it.
     */
    suspend fun locate(locator: PlaceLocator, fallback: suspend () -> GeoPoint?): Unit = coroutineScope {
        val granted = permission ?: return@coroutineScope
        if (start != BrowseStart.Locating) return@coroutineScope
        if (!granted) {
            placeAt(fallback(), provisional = false)
            return@coroutineScope
        }
        val fix = async { withTimeoutOrNull(LATE_LOCATION_LIMIT) { locator.here() } }
        val early = withTimeoutOrNull(locator.hold) { fix.await() }
        if (early != null) {
            // The athlete may have moved the map meanwhile.
            if (start == BrowseStart.Locating) placeAt(early, provisional = false)
            return@coroutineScope
        }
        if (start == BrowseStart.Locating) placeAt(fallback(), provisional = true)
        val late = fix.await() ?: return@coroutineScope
        if (provisional) {
            provisional = false
            centreOn(late)
            start = BrowseStart.Placed
        }
    }

    private fun placeAt(at: GeoPoint?, provisional: Boolean) {
        this.provisional = provisional
        fallbackAt = at
        arrived = false
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

    private companion object {
        /** Whether this viewport is centred on [at], as the map reports a camera sent there. */
        fun MapViewport.centres(at: GeoPoint): Boolean =
            abs(centerLat.e7ToDegrees() - at.lat) < ARRIVED_DEGREES && abs(centerLng.e7ToDegrees() - at.lng) < ARRIVED_DEGREES
    }
}
