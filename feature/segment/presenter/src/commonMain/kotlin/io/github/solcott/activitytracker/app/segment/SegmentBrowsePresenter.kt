package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.MapCameraRequest
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.MapViewport
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.SearchSegmentNames
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.dataresult.mapData
import io.github.solcott.uistate.OnKeyChange
import io.github.solcott.uistate.circuit.produceRetainedContentState
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/** How long typing must pause before a name search runs, so each keystroke isn't a request. */
internal val NAME_SEARCH_DEBOUNCE = 300.milliseconds

/** How long browsing waits for a first location fix before opening at the latest activity instead. */
internal val LOCATION_TIMEOUT = 5.seconds

@CircuitInject(SegmentBrowseScreen::class, SessionScope::class)
@Composable
fun SegmentBrowsePresenter(
    navigator: Navigator,
    segments: SegmentRepository,
    feed: FeedRepository,
    tracker: LocationTracker,
): SegmentBrowseState {
    // Saveable, so the query (and with it the results) is still there on coming back from a segment.
    val query = rememberTextFieldState()
    val trimmed = query.text.trim().toString()
    val browse = rememberRetained { BrowseControls(initialPermission(tracker)) }

    // Where to open, once: the athlete's location, else where their latest activity with a map started, else nowhere.
    LaunchedEffect(browse.permission) { browse.locate(tracker, feed) }

    // The area's segments, while no name is searched and the map is close enough. A new area keeps the last one's lines until it answers;
    // a new sport group drops them (ui/content-loading).
    val areaRequest = browse.viewport?.takeIf { trimmed.isEmpty() && !it.tooWide && browse.start != BrowseStart.Locating }
    val currentAreaRequest by rememberUpdatedState(areaRequest)
    val area =
        key(browse.sport) {
            val sport = browse.sport
            snapshotFlow { currentAreaRequest }
                .filterNotNull()
                .produceRetainedContentState(AreaRuns(), sport, browse.areaRetry) { request ->
                    flow { emit(segments.areaSegments(request.toSearchBox(sport)).mapData { areaRunsOf(it.segments, request, it.capped) }) }
                }
        }

    // Name results: a new query keeps the last results until it answers; a new scope or sport group drops them. The area and centre are
    // read when the first page loads, so moving the map doesn't restart a search, and kept for the later pages, whose cursor is an offset
    // into that first page's query.
    val currentViewport by rememberUpdatedState(browse.viewport)
    val scope = browse.scope
    val sport = browse.sport
    val results =
        key(scope, sport) {
            rememberRetainedPaging(
                trimmed,
                scope,
                sport,
                idOf = SegmentView::uri,
                onKeyChange = OnKeyChange.KeepItems,
                enabled = trimmed.isNotEmpty(),
            ) { cursor ->
                flow {
                    if (cursor == null) {
                        delay(NAME_SEARCH_DEBOUNCE)
                        browse.nameViewport = currentViewport
                    }
                    emit(segments.namesPage(nameParams(trimmed, scope, sport, browse.nameViewport, cursor)))
                }
            }
        }

    val mapLines =
        if (trimmed.isNotEmpty()) {
            results.state.items.mapNotNull { it.mapSegmentLine() }
        } else {
            area.data.runs.mapNotNull { it.segment.mapSegmentLine() } + area.data.lifts
        }

    return SegmentBrowseState(
        query = query,
        sport = sport,
        scope = scope,
        hasArea = browse.viewport != null,
        tooWide = browse.viewport?.tooWide == true,
        start = browse.start,
        area = area,
        results = results.state,
        mapLines = mapLines.toImmutableList(),
        selected = browse.selected,
        camera = browse.camera,
        askLocation = browse.permission == null,
    ) { event ->
        when (event) {
            SegmentBrowseEvent.Back -> navigator.pop()
            is SegmentBrowseEvent.Open -> navigator.goTo(SegmentScreen(event.uri.value))
            SegmentBrowseEvent.LoadMore -> results.loadMore()
            SegmentBrowseEvent.Retry -> if (trimmed.isNotEmpty()) results.retry() else browse.areaRetry++
            else -> browse.onEvent(event)
        }
    }
}

/** Whether location may be read: false where there's none, true once granted, null until the athlete is asked. */
private fun initialPermission(tracker: LocationTracker): Boolean? =
    when {
        !tracker.isSupported -> false
        tracker.hasPermission() -> true
        else -> null
    }

/** What segment browse keeps between compositions, and the events that only change it (segments/segment-browse). */
@Stable
internal class BrowseControls(permission: Boolean?) {
    /** Only segments of this `SegmentSportGroup`; null for every group. */
    var sport by mutableStateOf<String?>(null)
    var scope by mutableStateOf(NameScope.ThisArea)
    var viewport by mutableStateOf<MapViewport?>(null)
    var selected by mutableStateOf<AtUri?>(null)
    var camera by mutableStateOf<MapCameraRequest?>(null)
    var start by mutableStateOf(BrowseStart.Locating)
    var permission by mutableStateOf(permission)
    var areaRetry by mutableIntStateOf(0)

    /** The map's area when the current name search's first page loaded, which its later pages search too. */
    var nameViewport: MapViewport? = null

    fun onEvent(event: SegmentBrowseEvent) {
        when (event) {
            is SegmentBrowseEvent.ViewportChanged -> {
                viewport = event.viewport
                // The athlete found somewhere themselves.
                if (start == BrowseStart.Nowhere && !event.viewport.tooWide) start = BrowseStart.Placed
            }
            is SegmentBrowseEvent.PermissionResult -> permission = event.granted
            is SegmentBrowseEvent.Select -> selected = event.uri
            is SegmentBrowseEvent.ScopeChanged -> scope = event.scope
            is SegmentBrowseEvent.SportChanged -> sport = event.sport
            SegmentBrowseEvent.SearchEverywhere -> scope = NameScope.Everywhere
            else -> Unit
        }
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
        val at = here?.let { it.latitude to it.longitude } ?: feed.latestOwnStart()
        // The athlete may have moved the map meanwhile.
        if (start == BrowseStart.Locating) placeAt(at)
    }

    private fun placeAt(at: Pair<Double, Double>?) {
        if (at == null) {
            start = BrowseStart.Nowhere
        } else {
            camera = MapCameraRequest(id = (camera?.id ?: 0) + 1, lat = at.first, lng = at.second)
            start = BrowseStart.Placed
        }
    }
}

/** The viewport's box, as `feed.searchSegments` asks for it. */
private fun MapViewport.toSearchBox(sport: String?) =
    SearchSegments.Params(minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng, sport = sport)

/**
 * A name search's parameters: in This area, the map's box when it has one close enough to search; the map's centre to order by when there
 * is one. Without a map (web), everywhere and by match alone.
 */
internal fun nameParams(q: String, scope: NameScope, sport: String?, viewport: MapViewport?, cursor: String?): SearchSegmentNames.Params {
    val box = viewport?.takeIf { scope == NameScope.ThisArea && !it.tooWide }
    return SearchSegmentNames.Params(
        q = q,
        minLat = box?.minLat,
        minLng = box?.minLng,
        maxLat = box?.maxLat,
        maxLng = box?.maxLng,
        nearLat = viewport?.centerLat,
        nearLng = viewport?.centerLng,
        sport = sport,
        cursor = cursor,
    )
}

/** The area's runs, nearest the viewport's centre first, and its lifts as map lines (segments/segment-browse). */
internal fun areaRunsOf(found: List<SegmentView>, viewport: MapViewport, capped: Boolean): AreaRuns {
    val centre = GeoPoint(viewport.centerLat / E7, viewport.centerLng / E7)
    val (lifts, runs) = found.partition { SegmentKind.of(it.record.kind) == SegmentKind.LIFT }
    return AreaRuns(
        runs =
            runs
                .map { segment -> BrowsedSegment(segment, metersTo(segment, centre)) }
                .sortedWith(compareBy(BrowsedSegment::metersAway).thenBy { it.segment.currentName })
                .toImmutableList(),
        lifts = lifts.mapNotNull { it.mapSegmentLine() }.toImmutableList(),
        capped = capped,
    )
}

/** How close [segment]'s line comes to [centre], to its nearest point; unknown lines sort last. */
private fun metersTo(segment: SegmentView, centre: GeoPoint): Int =
    SegmentGeometry.decodePolyline(segment.record.line)?.minOfOrNull { SegmentGeometry.distanceMeters(it, centre) }?.roundToInt()
        ?: Int.MAX_VALUE

/** [this] segment as the browse map draws it; null when its line doesn't decode. */
internal fun SegmentView.mapSegmentLine(): MapSegmentLine? =
    mapLine(record.line)?.let { MapSegmentLine(uri.value, it, lift = SegmentKind.of(record.kind) == SegmentKind.LIFT) }

private const val E7 = 1e7
