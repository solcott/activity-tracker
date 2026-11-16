package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.MapViewport
import io.github.solcott.activitytracker.app.browse.MapBrowse
import io.github.solcott.activitytracker.app.browse.NAME_SEARCH_DEBOUNCE
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.app.SearchSegmentNames
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.textfield.rememberTextFieldState
import io.github.solcott.dataresult.mapData
import io.github.solcott.uistate.OnKeyChange
import io.github.solcott.uistate.Page
import io.github.solcott.uistate.circuit.produceRetainedContentState
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import kotlin.math.roundToInt
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow

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
    val browse = rememberRetained { MapBrowse(MapBrowse.initialPermission(tracker)) }
    // Only segments of this `SegmentSportGroup`; null for every group.
    var sport by rememberRetained { mutableStateOf<String?>(null) }

    // Where to open, once: the athlete's location, else where their latest activity with a map started, else nowhere.
    LaunchedEffect(browse.permission) { browse.locate(tracker, feed) }

    // The area's segments, while no name is searched and the map is close enough. A new area keeps the last one's lines until it answers;
    // a new sport group drops them (ui/content-loading).
    val areaRequest = browse.areaRequest(searching = trimmed.isNotEmpty())
    val currentAreaRequest by rememberUpdatedState(areaRequest)
    val area =
        key(sport) {
            snapshotFlow { currentAreaRequest }
                .filterNotNull()
                .produceRetainedContentState(AreaRuns(), sport, browse.areaRetry) { request ->
                    flow {
                        emit(
                            segments.areaSegments(request.toSearchBox(sport)).mapData {
                                areaRunsOf(it.segments, request, it.capped, sport)
                            },
                        )
                    }
                }
        }

    // Name results: a new query keeps the last results until it answers; a new scope or sport group drops them. The area and centre are
    // read when the first page loads, so moving the map doesn't restart a search, and kept for the later pages, whose cursor is an offset
    // into that first page's query.
    val currentViewport by rememberUpdatedState(browse.viewport)
    val scope = browse.scope
    val results =
        key(scope, sport) {
            rememberRetainedPaging(
                trimmed,
                scope,
                sport,
                idOf = SegmentRoute::key,
                onKeyChange = OnKeyChange.KeepItems,
                enabled = trimmed.isNotEmpty(),
            ) { cursor ->
                flow {
                    if (cursor == null) {
                        delay(NAME_SEARCH_DEBOUNCE)
                        browse.nameViewport = currentViewport
                    }
                    val params = nameParams(trimmed, browse.nameBox(browse.nameViewport), sport, browse.nameViewport, cursor)
                    // The AppView pages by route, so no route spans two pages (segments/segment-routes).
                    emit(segments.namesPage(params).mapData { page -> Page(SegmentRoute.of(page.items, sport), page.meta, page.next) })
                }
            }
        }

    val searching = trimmed.isNotEmpty()
    val mapLines =
        remember(searching, results.state.items, area.data) {
            if (searching) {
                    results.state.items.mapNotNull { it.shown.mapSegmentLine() }
                } else {
                    area.data.runs.mapNotNull { it.segment.mapSegmentLine() } + area.data.lifts
                }
                .toImmutableList()
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
        mapLines = mapLines,
        selected = browse.selected,
        camera = browse.camera,
        askLocation = browse.permission == null,
    ) { event ->
        when (event) {
            SegmentBrowseEvent.Back -> navigator.pop()
            is SegmentBrowseEvent.Open -> navigator.goTo(SegmentScreen(event.uri.value))
            is SegmentBrowseEvent.TapRow ->
                if (event.uri == browse.selected) {
                    navigator.goTo(SegmentScreen(event.uri.value))
                } else {
                    // Only a name result can lie off the map: every run an area lists crosses it.
                    val row = if (searching) results.state.items.find { it.shown.uri == event.uri }?.shown else null
                    browse.selectRow(event.uri, row?.let { SegmentGeometry.decodePolyline(it.record.line) })
                }
            SegmentBrowseEvent.LoadMore -> results.loadMore()
            SegmentBrowseEvent.Retry -> if (trimmed.isNotEmpty()) results.retry() else browse.areaRetry++
            is SegmentBrowseEvent.ViewportChanged -> browse.viewportChanged(event.viewport)
            is SegmentBrowseEvent.PermissionResult -> browse.permission = event.granted
            is SegmentBrowseEvent.Select -> browse.selected = event.uri
            is SegmentBrowseEvent.ScopeChanged -> browse.scope = event.scope
            is SegmentBrowseEvent.SportChanged -> sport = event.sport
            SegmentBrowseEvent.SearchEverywhere -> browse.scope = NameScope.Everywhere
        }
    }
}

/** The viewport's box, as `feed.searchSegments` asks for it. */
private fun MapViewport.toSearchBox(sport: String?) =
    SearchSegments.Params(minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng, sport = sport)

/**
 * A name search's parameters: the [box] it covers (This area's, or none for everywhere), and the map's centre to order by when there is
 * one. Without a map (web), everywhere and by match alone.
 */
internal fun nameParams(q: String, box: MapViewport?, sport: String?, viewport: MapViewport?, cursor: String?): SearchSegmentNames.Params =
    SearchSegmentNames.Params(
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

/**
 * The area's runs as one row per route (segments/segment-routes), each as near the viewport's centre as its nearest run, nearest first, and
 * its lifts as map lines (segments/segment-browse). A route is shown as its first run of [sport] when one is chosen.
 */
internal fun areaRunsOf(found: List<SegmentView>, viewport: MapViewport, capped: Boolean, sport: String? = null): AreaRuns {
    val centre = GeoPoint(viewport.centerLat / E7, viewport.centerLng / E7)
    val (lifts, runs) = found.partition { SegmentKind.of(it.record.kind) == SegmentKind.LIFT }
    val away = runs.associate { it.uri to metersTo(it, centre) }
    return AreaRuns(
        runs =
            runs
                .sortedWith(compareBy<SegmentView> { away.getValue(it.uri) }.thenBy { it.currentName })
                .let { SegmentRoute.of(it, sport) }
                .map { route -> BrowsedSegment(route, route.members.minOf { away.getValue(it.uri) }) }
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
