package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.MapCameraRequest
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.PagedStates
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.app.fixtures.fakeSegment
import io.github.solcott.activitytracker.app.fixtures.fakeSegmentRecord
import io.github.solcott.activitytracker.app.fixtures.pagedSamples
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.PagedState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Segment browse (segments/segment-browse): an area's runs on a map with a lift, name results, and the states before either. */
object SegmentBrowseSamples {
    private val ridge = fakeSegment("3kridge", record = fakeSegmentRecord("Ridge Trail"))
    private val hogback =
        fakeSegment("3khog", record = fakeSegmentRecord("Hogback Climb", sport = SegmentSportGroup.RUN), distanceMeters = 2_400)
    private val summit =
        fakeSegment("3ksummit", record = fakeSegmentRecord("Summit Run", sport = SegmentSportGroup.ALPINE), distanceMeters = 1_800)

    /** [Fixed.POLYLINE] the other way. */
    private val back = SegmentGeometry.encodePolyline(SegmentGeometry.decodePolyline(Fixed.POLYLINE).orEmpty().reversed())

    /** Ann's (segments/segment-routes): one trail as ride, run and hike segments each way, a route of six. */
    private val anns =
        listOf(SegmentSportGroup.RIDE, SegmentSportGroup.RUN, SegmentSportGroup.HIKE)
            .flatMap { sport ->
                listOf("eastbound" to Fixed.POLYLINE, "westbound" to back).map { (way, line) ->
                    fakeSegment(
                        "3kann$sport$way",
                        record = fakeSegmentRecord("Ann's ($way)", sport = sport, line = line),
                        distanceMeters = 8_690,
                    )
                }
            }
            .let { members -> members.map { it.copy(route = members.first().uri) } }
            .toImmutableList()

    private fun alone(segment: SegmentView) = SegmentRoute.routeOf(listOf(segment))

    /** A short line beside [Fixed.POLYLINE], for the lift. */
    private const val LIFT_LINE = "_p~iF~ps|U_ulLnnqC"

    /** The area's rows, nearest first: three trails of one segment each, then Ann's six. */
    val runs =
        persistentListOf(
            BrowsedSegment(alone(ridge), 120),
            BrowsedSegment(alone(hogback), 900),
            BrowsedSegment(alone(summit), 2_300),
            BrowsedSegment(SegmentRoute.routeOf(anns), 3_100),
        )

    private val area = AreaRuns(runs, lifts = persistentListOf(MapSegmentLine("at://did:plc:importer/lift", LIFT_LINE, lift = true)))

    private fun lines(segments: List<SegmentView>) = segments.map { MapSegmentLine(it.uri.value, Fixed.POLYLINE) }

    /** Browsing an area that loaded [content]; nothing typed. */
    private fun browsing(content: ContentState<AreaRuns>) =
        SegmentBrowseState(
            query = textFieldStateOf(),
            hasArea = true,
            start = BrowseStart.Placed,
            area = content,
            results = PagedStates.end(emptyList(), Unit),
            mapLines = (lines(content.data.runs.map { it.segment }) + content.data.lifts).toImmutableList(),
            camera = MapCameraRequest(1, BURLINGTON_LAT, BURLINGTON_LNG),
        )

    /** Searching "hog" in this area, with [results]. */
    private fun searching(results: PagedState<SegmentRoute, Unit>, scope: NameScope = NameScope.ThisArea) =
        browsing(ContentStates.loaded(area))
            .copy(
                query = textFieldStateOf("hog"),
                scope = scope,
                results = results,
                mapLines = lines(results.items.map { it.shown }).toImmutableList(),
            )

    /**
     * The area's states for the loading kinds a [ContentState] has, and name search's for those only a paged list has (ui/content-loading).
     */
    val all: Map<String, SegmentBrowseState>
        get() =
            contentSamples(AreaRuns(), area, ::browsing) +
                pagedSamples(listOf(alone(hogback), alone(ridge)), Unit) { searching(it) }
                    .filterKeys { it in setOf(SampleKind.EMPTY, SampleKind.LOADING_MORE, SampleKind.PAGE_FAILED, SampleKind.END) }

    val loaded: SegmentBrowseState
        get() = all.getValue(SampleKind.LOADED)

    /** A run tapped on the map, marked in the list. */
    val selected: SegmentBrowseState
        get() = loaded.copy(selected = hogback.uri)

    /** Zoomed out past where segments are searched. */
    val tooWide: SegmentBrowseState
        get() = browsing(ContentStates.loading(AreaRuns())).copy(tooWide = true, mapLines = persistentListOf())

    /** Name results in this area. */
    val nameResults: SegmentBrowseState
        get() = searching(PagedStates.end(listOf(alone(hogback)), Unit))

    /** Searching "ann" everywhere: Ann's six segments as one row. */
    val routeResults: SegmentBrowseState
        get() =
            searching(PagedStates.end(listOf(SegmentRoute.routeOf(anns), alone(hogback)), Unit), NameScope.Everywhere)
                .copy(query = textFieldStateOf("ann"))

    /** Nothing named so in this area: offers to search everywhere. */
    val nothingHere: SegmentBrowseState
        get() = searching(PagedStates.end(emptyList(), Unit))

    /** Nowhere to open: no location and no activity with a map. */
    val nowhere: SegmentBrowseState
        get() = browsing(ContentStates.loading(AreaRuns())).copy(start = BrowseStart.Nowhere, hasArea = false, camera = null)

    /** Finding where the athlete is. */
    val locating: SegmentBrowseState
        get() = nowhere.copy(start = BrowseStart.Locating)

    /** Every shared sample and the browse states beyond content loading, for the native page's previews and snapshots. */
    val variants: Map<String, SegmentBrowseState>
        get() =
            all +
                mapOf(
                    "selected" to selected,
                    "tooWide" to tooWide,
                    "nameResults" to nameResults,
                    "routeResults" to routeResults,
                    "nothingHere" to nothingHere,
                    "nowhere" to nowhere,
                    "locating" to locating,
                )
}

/** Burlington, Vermont: where the samples' map is centred. */
private const val BURLINGTON_LAT = 44.4759
private const val BURLINGTON_LNG = -73.2121
