package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.MapCameraRequest
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.PagedStates
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.app.fixtures.fakePath
import io.github.solcott.activitytracker.app.fixtures.fakeSegment
import io.github.solcott.activitytracker.app.fixtures.fakeSegmentRecord
import io.github.solcott.activitytracker.app.fixtures.pagedSamples
import io.github.solcott.activitytracker.lexicon.app.PathKind
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.PagedState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Trails & roads (segments/segment-browse): an area's trails, roads and loose segment groups on a map with a lift, name results, and the
 * states before either.
 */
object SegmentBrowseSamples {
    private val ridge = fakeSegment("3kridge", record = fakeSegmentRecord("Ridge Trail"))
    private val hogback =
        fakeSegment("3khog", record = fakeSegmentRecord("Hogback Climb", sport = SegmentSportGroup.RUN), distanceMeters = 2_400)
    private val summit =
        fakeSegment("3ksummit", record = fakeSegmentRecord("Summit Run", sport = SegmentSportGroup.ALPINE), distanceMeters = 1_800)

    /** [Fixed.POLYLINE] the other way. */
    private val back = SegmentGeometry.encodePolyline(SegmentGeometry.decodePolyline(Fixed.POLYLINE).orEmpty().reversed())

    /** Ann's whole line (segments/segment-groups): ride, run and hike segments each way, a segment group of six. */
    val annsWhole =
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
            .let { members -> members.map { it.copy(group = members.first().uri) } }
            .toImmutableList()

    /** Ann's as a trail (segments/segment-paths): its whole line as one segment group, and a stretch as another. */
    val annsTrail =
        fakePath(
            "anns",
            name = "Ann's",
            lengthMeters = 8_690,
            sports = listOf(SegmentSportGroup.RIDE, SegmentSportGroup.RUN, SegmentSportGroup.HIKE),
            groupCount = 2,
        )

    /** A stretch of Ann's: ride and run, one way. */
    val annsStretch =
        listOf(SegmentSportGroup.RIDE, SegmentSportGroup.RUN)
            .map { sport ->
                fakeSegment(
                    "3kannstretch$sport",
                    record = fakeSegmentRecord("Ann's: Lupine Hiking → Potato Hill", sport = sport),
                    distanceMeters = 2_100,
                )
            }
            .let { members -> members.map { it.copy(group = members.first().uri) } }
            .toImmutableList()

    /**
     * Clarks' ride line (segments/segment-direction): one-way for bikes, clearly climbing from its start (199 m up over 2287 m), as the
     * segment importer publishes it from `oneway:bicycle`.
     */
    val clarksRide =
        fakeSegment(
                "3kclarksride",
                record = fakeSegmentRecord("Clarks", sport = SegmentSportGroup.RIDE),
                distanceMeters = 2_287,
            )
            .copy(oneWay = true, elevationGain = 199, elevationLoss = 0)

    /** Clarks' foot line: run and hike segments each way, a segment group of four, two-way on foot. */
    val clarksFoot =
        listOf(SegmentSportGroup.RUN, SegmentSportGroup.HIKE)
            .flatMap { sport ->
                listOf("eastbound" to Fixed.POLYLINE, "westbound" to back).map { (way, line) ->
                    fakeSegment(
                        "3kclarks$sport$way",
                        record = fakeSegmentRecord("Clarks - Bikes Uphill Only ($way)", sport = sport, line = line),
                        distanceMeters = 2_022,
                    )
                }
            }
            .let { members -> members.map { it.copy(group = members.first().uri) } }
            .toImmutableList()

    /** Pine St, a road with an athlete's segment on it. */
    private val pineSt =
        fakePath("pine", name = "Pine St", kind = PathKind.ROAD, lengthMeters = 3_200, sports = listOf(SegmentSportGroup.RIDE))

    private val sprint = fakeSegment("3ksprint", record = fakeSegmentRecord("Pine St sprint"), distanceMeters = 600)

    private fun alone(segment: SegmentView) = SegmentGroupRow.groupOf(listOf(segment))

    private val annsRow =
        BrowseRow.Path(annsTrail, persistentListOf(SegmentGroupRow.groupOf(annsWhole), SegmentGroupRow.groupOf(annsStretch)))

    /** A short line beside [Fixed.POLYLINE], for the lift. */
    private const val LIFT_LINE = "_p~iF~ps|U_ulLnnqC"

    /** The area's rows, nearest first: two segments on no path, the road Pine St, a third segment, then the trail Ann's. */
    val runs =
        persistentListOf(
            BrowsedRow(BrowseRow.Group(alone(ridge)), 120),
            BrowsedRow(BrowseRow.Group(alone(hogback)), 900),
            BrowsedRow(BrowseRow.Path(pineSt, persistentListOf(alone(sprint))), 1_500),
            BrowsedRow(BrowseRow.Group(alone(summit)), 2_300),
            BrowsedRow(annsRow, 3_100),
        )

    private val area = AreaRuns(runs, lifts = persistentListOf(MapSegmentLine("at://did:plc:importer/lift", LIFT_LINE, lift = true)))

    private fun lines(rows: List<BrowseRow>) = rows.map { MapSegmentLine(it.key.value, Fixed.POLYLINE) }

    /** Browsing an area that loaded [content]; nothing typed. */
    private fun browsing(content: ContentState<AreaRuns>) =
        SegmentBrowseState(
            query = textFieldStateOf(),
            hasArea = true,
            start = BrowseStart.Placed,
            area = content,
            results = PagedStates.end(emptyList(), Unit),
            mapLines = (lines(content.data.rows.map { it.row }) + content.data.lifts).toImmutableList(),
            camera = MapCameraRequest(1, BURLINGTON_LAT, BURLINGTON_LNG),
        )

    /** Searching "hog" in this area, with [results]. */
    private fun searching(results: PagedState<BrowseRow, Unit>, scope: NameScope = NameScope.ThisArea) =
        browsing(ContentStates.loaded(area))
            .copy(
                query = textFieldStateOf("hog"),
                scope = scope,
                results = results,
                mapLines = lines(results.items).toImmutableList(),
            )

    /**
     * The area's states for the loading kinds a [ContentState] has, and name search's for those only a paged list has (ui/content-loading).
     */
    val all: Map<String, SegmentBrowseState>
        get() =
            contentSamples(AreaRuns(), area, ::browsing) +
                pagedSamples(listOf<BrowseRow>(BrowseRow.Group(alone(hogback)), BrowseRow.Group(alone(ridge))), Unit) { searching(it) }
                    .filterKeys { it in setOf(SampleKind.EMPTY, SampleKind.LOADING_MORE, SampleKind.PAGE_FAILED, SampleKind.END) }

    val loaded: SegmentBrowseState
        get() = all.getValue(SampleKind.LOADED)

    /** A row tapped on the map, marked in the list. */
    val selected: SegmentBrowseState
        get() = loaded.copy(selected = hogback.uri)

    /** Zoomed out past where segments are searched. */
    val tooWide: SegmentBrowseState
        get() = browsing(ContentStates.loading(AreaRuns())).copy(tooWide = true, mapLines = persistentListOf())

    /** Name results in this area. */
    val nameResults: SegmentBrowseState
        get() = searching(PagedStates.end(listOf(BrowseRow.Group(alone(hogback))), Unit))

    /** Searching "ann" everywhere: the trail Ann's as one row, then a segment on no path. */
    val groupResults: SegmentBrowseState
        get() =
            searching(PagedStates.end(listOf(BrowseRow.Path(annsTrail), BrowseRow.Group(alone(hogback))), Unit), NameScope.Everywhere)
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
                    "groupResults" to groupResults,
                    "nothingHere" to nothingHere,
                    "nowhere" to nowhere,
                    "locating" to locating,
                )
}

/** Burlington, Vermont: where the samples' map is centred. */
private const val BURLINGTON_LAT = 44.4759
private const val BURLINGTON_LNG = -73.2121
