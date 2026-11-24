package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.MapViewport
import io.github.solcott.activitytracker.app.browse.MapBrowse
import io.github.solcott.activitytracker.app.browse.areaContent
import io.github.solcott.activitytracker.app.browse.nameResults
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.PathView
import io.github.solcott.activitytracker.lexicon.app.SearchPathNames
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.PlaceLocator
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.location.pointE7
import io.github.solcott.activitytracker.textfield.rememberTextFieldState
import io.github.solcott.dataresult.mapData
import io.github.solcott.uistate.Page
import kotlin.math.roundToInt
import kotlinx.collections.immutable.toImmutableList

@CircuitInject(SegmentBrowseScreen::class, SessionScope::class)
@Composable
fun SegmentBrowsePresenter(
    navigator: Navigator,
    segments: SegmentRepository,
    feed: FeedRepository,
    locator: PlaceLocator,
): SegmentBrowseState {
    // Saveable, so the query (and with it the results) is still there on coming back from a segment.
    val query = rememberTextFieldState()
    val trimmed = query.text.trim().toString()
    val browse = rememberRetained { MapBrowse(locator.permission()) }
    // Only segments of this `SegmentSportGroup`; null for every group.
    var sport by rememberRetained { mutableStateOf<String?>(null) }

    // Where to open, once: the athlete's location, else where their latest activity with a map started, else nowhere.
    LaunchedEffect(browse.permission) { browse.locate(locator) { feed.latestOwnStart() } }

    // The area's segments, while no name is searched and the map is close enough. A new area keeps the last one's lines until it answers;
    // a new sport group drops them (ui/content-loading).
    val area =
        key(sport) {
            browse.areaContent(AreaRuns(), searching = trimmed.isNotEmpty(), sport) { request ->
                segments.areaSegments(request.toSearchBox(sport)).mapData { areaRunsOf(it.segments, request, it.capped, sport, it.paths) }
            }
        }

    // Name results: a new query keeps the last results until it answers; a new scope or sport group drops them.
    val results =
        key(browse.scope, sport) {
            browse.nameResults(trimmed, sport, idOf = BrowseRow::key) { box, viewport, cursor ->
                // The AppView pages by result, a path or a segment group whole, so none spans two pages (segments/segment-paths).
                segments.pathNamesPage(nameParams(trimmed, box, sport, viewport, cursor)).mapData { page ->
                    Page(page.items.mapNotNull { rowOf(it, sport) }, page.meta, page.next)
                }
            }
        }

    val searching = trimmed.isNotEmpty()
    val mapLines =
        remember(searching, results.state.items, area.data) {
            if (searching) {
                    results.state.items.flatMap { it.mapSegmentLines() }
                } else {
                    area.data.rows.flatMap { it.row.mapSegmentLines() } + area.data.lifts
                }
                .toImmutableList()
        }

    return SegmentBrowseState(browse = browse.state(query, mapLines), sport = sport, area = area, results = results.state) { event ->
        when (event) {
            SegmentBrowseEvent.Back -> navigator.pop()
            is SegmentBrowseEvent.Open -> navigator.goTo(screenOf(event.uri, sport))
            is SegmentBrowseEvent.TapRow ->
                browse.tapRow(
                    event.uri,
                    points = {
                        // Only a name result can lie off the map: every row an area lists crosses it.
                        val row = if (searching) results.state.items.find { it.key == event.uri } else null
                        row?.points()
                    },
                    open = { navigator.goTo(screenOf(event.uri, sport)) },
                )
            SegmentBrowseEvent.LoadMore -> results.loadMore()
            SegmentBrowseEvent.Retry -> if (trimmed.isNotEmpty()) results.retry() else browse.areaRetry++
            is SegmentBrowseEvent.SportChanged -> sport = event.sport
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
internal fun nameParams(q: String, box: MapViewport?, sport: String?, viewport: MapViewport?, cursor: String?): SearchPathNames.Params =
    SearchPathNames.Params(
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
 * The area's rows (segments/segment-browse): a trail or road ([paths]) for each one its runs lie on, and a row per segment group on none
 * (segments/segment-groups), each as near the viewport's centre as its nearest run, nearest first; and its lifts as map lines. A segment
 * group is shown as its first run of [sport] when one is chosen.
 */
internal fun areaRunsOf(
    found: List<SegmentView>,
    viewport: MapViewport,
    capped: Boolean,
    sport: String? = null,
    paths: List<PathView> = emptyList(),
): AreaRuns {
    val centre = pointE7(viewport.centerLat, viewport.centerLng)
    val (lifts, runs) = found.partition { SegmentKind.of(it.record.kind) == SegmentKind.LIFT }
    val away = runs.associate { it.uri to metersTo(it, centre) }
    fun nearest(row: BrowseRow): Int =
        when (row) {
            is BrowseRow.Path -> row.groups.minOfOrNull { group -> group.members.minOf { away.getValue(it.uri) } } ?: Int.MAX_VALUE
            is BrowseRow.Group -> row.group.members.minOf { away.getValue(it.uri) }
        }
    return AreaRuns(
        rows =
            runs
                .sortedWith(compareBy<SegmentView> { away.getValue(it.uri) }.thenBy { it.currentName })
                .let { BrowseRow.of(it, paths, sport) }
                .map { row -> BrowsedRow(row, nearest(row)) }
                .sortedWith(compareBy(BrowsedRow::metersAway).thenBy { it.row.sortName })
                .toImmutableList(),
        lifts = lifts.mapNotNull { it.mapSegmentLine() }.toImmutableList(),
        capped = capped,
    )
}

/** How close [segment]'s line comes to [centre], to its nearest point; unknown lines sort last. */
private fun metersTo(segment: SegmentView, centre: GeoPoint): Int =
    SegmentGeometry.decodePolyline(segment.record.line)?.minOfOrNull { SegmentGeometry.distanceMeters(it, centre) }?.roundToInt()
        ?: Int.MAX_VALUE

/** A name result as a row: a path, narrowed to [sport] when one is chosen, or a segment group on none. */
private fun rowOf(result: SearchPathNames.Result, sport: String?): BrowseRow? =
    result.path?.let { BrowseRow.Path(it, sport = sport) }
        ?: result.group?.takeIf { it.isNotEmpty() }?.let { BrowseRow.Group(SegmentGroupRow.groupOf(it, sport)) }

/** The page [uri] opens: a path's Trail page, narrowed to [sport], or a segment's page. */
private fun screenOf(uri: AtUri, sport: String?): Screen =
    if (uri.collection == Nsid.FEED_PATH) PathScreen(uri.value, sport) else SegmentScreen(uri.value)

/** What a row is ordered by among equally near ones. */
private val BrowseRow.sortName: String
    get() =
        when (this) {
            is BrowseRow.Path -> path.name
            is BrowseRow.Group -> group.shown.currentName
        }

/** [this] row's lines as the browse map draws them, each keyed by the row: a path's pieces, or a group's shown segment's line. */
internal fun BrowseRow.mapSegmentLines(): List<MapSegmentLine> =
    when (this) {
        is BrowseRow.Path -> path.lines.mapNotNull { piece -> mapLine(piece)?.let { MapSegmentLine(key.value, it, rating = rating) } }
        is BrowseRow.Group -> listOfNotNull(group.shown.mapSegmentLine())
    }

/** Every position of [this] row's line, for framing it on the map. */
private fun BrowseRow.points(): List<GeoPoint>? =
    when (this) {
        is BrowseRow.Path -> path.lines.flatMap { SegmentGeometry.decodePolyline(it).orEmpty() }.takeIf { it.isNotEmpty() }
        is BrowseRow.Group -> SegmentGeometry.decodePolyline(group.shown.record.line)
    }

/** [this] segment as the browse map draws it; null when its line doesn't decode. */
internal fun SegmentView.mapSegmentLine(): MapSegmentLine? =
    mapLine(record.line)?.let {
        MapSegmentLine(uri.value, it, lift = SegmentKind.of(record.kind) == SegmentKind.LIFT, rating = TrailRating.of(this))
    }
