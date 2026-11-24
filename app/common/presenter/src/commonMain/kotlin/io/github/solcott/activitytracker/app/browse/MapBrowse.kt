package io.github.solcott.activitytracker.app.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.MapViewport
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.PlaceLocator
import io.github.solcott.activitytracker.location.e7ToDegrees
import io.github.solcott.activitytracker.textfield.TextFieldState
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.OnKeyChange
import io.github.solcott.uistate.Page
import io.github.solcott.uistate.circuit.Paging
import io.github.solcott.uistate.circuit.produceRetainedContentState
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
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

    /** The map's own first view while browsing is still locating, so a later view centred elsewhere is the athlete moving it. */
    private var locatingView: MapViewport? = null

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
        if (start == BrowseStart.Locating) followWhileLocating(shown)
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
     * Whether [shown], while browsing is still locating, is the athlete moving the map: its first view is the map's own start, and a later
     * one centred elsewhere ends locating where they put it, so neither a location nor the fallback moves it afterwards.
     */
    private fun followWhileLocating(shown: MapViewport) {
        val first = locatingView
        if (first == null) {
            locatingView = shown
        } else if (!shown.centres(GeoPoint(first.centerLat.e7ToDegrees(), first.centerLng.e7ToDegrees()))) {
            start = if (shown.tooWide) BrowseStart.Nowhere else BrowseStart.Placed
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
            placeAtFallback(fallback, provisional = false)
            return@coroutineScope
        }
        val fix = async { withTimeoutOrNull(LATE_LOCATION_LIMIT) { locator.here() } }
        val early = withTimeoutOrNull(locator.hold) { fix.await() }
        if (early != null) {
            // The athlete may have moved the map meanwhile.
            if (start == BrowseStart.Locating) placeAt(early, provisional = false)
            return@coroutineScope
        }
        placeAtFallback(fallback, provisional = true)
        val late = fix.await() ?: return@coroutineScope
        if (provisional) {
            provisional = false
            centreOn(late)
            start = BrowseStart.Placed
        }
    }

    /** Opens at [fallback]'s answer, unless the athlete moved the map while it was being read. */
    private suspend fun placeAtFallback(fallback: suspend () -> GeoPoint?, provisional: Boolean) {
        if (start != BrowseStart.Locating) return
        val at = fallback()
        if (start == BrowseStart.Locating) placeAt(at, provisional)
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

/**
 * The area's content for a browse screen: [load]ed for the map's [MapBrowse.areaRequest] while no name is searched ([searching]), the map
 * is close enough and browsing has started. A new area keeps the last one's content until it answers; a change of [keys] (or a Retry,
 * [MapBrowse.areaRetry]) restarts it (ui/content-loading). A presenter wanting a new key to drop the old content wraps this in `key(…)`.
 */
@Composable
fun <T> MapBrowse.areaContent(
    initial: T,
    searching: Boolean,
    vararg keys: Any?,
    load: suspend (MapViewport) -> Outcome<T>,
): ContentState<T> {
    val currentAreaRequest by rememberUpdatedState(areaRequest(searching))
    return snapshotFlow { currentAreaRequest }
        .filterNotNull()
        .produceRetainedContentState(initial, *keys, areaRetry) { request -> flow { emit(load(request)) } }
}

/**
 * A browse screen's name results for [query], while it isn't blank: a new query keeps the last results until it answers, a new
 * [MapBrowse.scope] or one of [keys] restarts them. The first page waits for typing to pause ([NAME_SEARCH_DEBOUNCE]) and then reads the
 * map's area and centre ([MapBrowse.nameViewport]), so moving the map doesn't restart a search; later pages, whose cursor is an offset into
 * the first page's query, search the same. [load] gets the box searched ([MapBrowse.nameBox], null for everywhere), the viewport to order
 * by, and the cursor.
 */
@Composable
fun <T, M> MapBrowse.nameResults(
    query: String,
    vararg keys: Any?,
    idOf: (T) -> Any?,
    load: suspend (box: MapViewport?, viewport: MapViewport?, cursor: String?) -> Outcome<Page<T, M>>,
): Paging<T, M> {
    val currentViewport by rememberUpdatedState(viewport)
    return rememberRetainedPaging(query, scope, *keys, idOf = idOf, onKeyChange = OnKeyChange.KeepItems, enabled = query.isNotEmpty()) {
        cursor ->
        flow {
            if (cursor == null) {
                delay(NAME_SEARCH_DEBOUNCE)
                nameViewport = currentViewport
            }
            emit(load(nameBox(nameViewport), nameViewport, cursor))
        }
    }
}
