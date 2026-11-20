package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.MapCameraRequest
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.app.fixtures.fakePath
import io.github.solcott.activitytracker.app.fixtures.fakeSegment
import io.github.solcott.activitytracker.app.fixtures.fakeSegmentRecord
import io.github.solcott.activitytracker.lexicon.app.PathOneWay
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

/** The Trail page (segments/path-page): Ann's, its whole line and a stretch, and the states before and around them. */
object PathSamples {
    private val groups =
        persistentListOf(
            SegmentGroupRow.groupOf(SegmentBrowseSamples.annsWhole),
            SegmentGroupRow.groupOf(SegmentBrowseSamples.annsStretch),
        )

    private val content = PathContent(SegmentBrowseSamples.annsTrail, groups, SegmentCredit.OpenStreetMap("ODbL-1.0"))

    private fun page(content: io.github.solcott.uistate.ContentState<PathContent>) =
        PathState(
            content = content,
            mapLines =
                (listOfNotNull(content.data.path?.let { MapSegmentLine(it.uri.value, Fixed.POLYLINE, lift = true) }) +
                        content.data.groups.map { MapSegmentLine(it.shown.uri.value, Fixed.POLYLINE) })
                    .toImmutableList(),
            camera = MapCameraRequest(1, BURLINGTON_LAT, BURLINGTON_LNG),
        )

    /** The page's states for each loading kind (ui/content-loading). */
    val all: Map<String, PathState>
        get() = contentSamples(PathContent(), content, ::page)

    val loaded: PathState
        get() = page(ContentStates.loaded(content))

    /** The stretch selected on the map, marked in the list. */
    val selected: PathState
        get() = loaded.copy(selected = groups[1].shown.uri)

    /** Opened from browse with Ride chosen: only ride segments, and Show all sports. */
    val ride: PathState
        get() =
            page(
                    ContentStates.loaded(
                        content.copy(
                            groups =
                                groups
                                    .map { group ->
                                        SegmentGroupRow.groupOf(group.members.filter { it.record.sport == SegmentSportGroup.RIDE })
                                    }
                                    .toImmutableList(),
                        ),
                    ),
                )
                .copy(sport = SegmentSportGroup.RIDE)

    /** A road whose only segment someone else proposed: nothing listed. */
    val empty: PathState
        get() = page(ContentStates.loaded(content.copy(groups = persistentListOf())))

    /**
     * Clarks (segments/segment-direction, segments/path-page): one line run and hiked both ways and ridden one way, clearly climbing, a
     * single row "Clarks" whose details read "Run, Hike both ways · Ride uphill only", under a header reading "Trail · 1.26 mi · Run, Hike
     * · Ride uphill only".
     */
    val oneWay: PathState
        get() =
            page(
                ContentStates.loaded(
                    content.copy(
                        path =
                            fakePath(
                                "clarks",
                                name = "Clarks",
                                lengthMeters = 2_022,
                                sports = listOf(SegmentSportGroup.RIDE, SegmentSportGroup.RUN, SegmentSportGroup.HIKE),
                                oneWays = listOf(PathOneWay(SegmentSportGroup.RIDE, "uphill")),
                            ),
                        groups =
                            persistentListOf(
                                SegmentGroupRow.groupOf(
                                    SegmentBrowseSamples.clarksFoot +
                                        SegmentBrowseSamples.clarksRide.copy(group = SegmentBrowseSamples.clarksFoot.first().uri),
                                ),
                            ),
                    ),
                ),
            )

    /** A downhill-only ride row of Rush named [name], [meters] long. */
    private fun rushRow(rkey: String, name: String, meters: Long, sport: String = SegmentSportGroup.RIDE): SegmentGroupRow =
        SegmentGroupRow.groupOf(
            listOf(
                fakeSegment(
                        rkey,
                        record = fakeSegmentRecord(name, sport = sport, source = "osm", license = "ODbL-1.0"),
                        distanceMeters = meters,
                    )
                    .copy(oneWay = true, climb = "downhill"),
            ),
        )

    private fun section(name: String, vararg rows: SegmentGroupRow): PathListItem.Section =
        PathListItem.Section(name, rows.toList().toImmutableList(), SegmentGroupRow.groupOf(rows.flatMap { it.members }))

    private val throughTheTrees =
        section(
            "Through the Trees Trail",
            rushRow("3krushttt", "Rush › Through the Trees Trail", 6_695),
            rushRow("3krushtttch", "Rush › Through the Trees Trail → Canyon Hollow", 4_715),
            rushRow("3krushtttsec", "Rush - Through the Trees Trail", 1_722),
        )
    private val exitStageLeft =
        section(
            "Exit Stage Left",
            rushRow("3krushesl", "Rush › Exit Stage Left", 5_681),
            rushRow("3krusheslch", "Rush › Exit Stage Left → Canyon Hollow", 3_701),
            rushRow("3krusheslsec", "Rush - Exit Stage Left Trail", 1_610),
        )
    private val familySpan = rushRow("3krushbst", "Rush → Bonneville Shoreline", 3_000)
    private val limelight = rushRow("3krushlime", "Rush - Limelight", 1_448)

    /**
     * Rush (segments/path-page): its Through the Trees Trail and Exit Stage Left sections, each its whole branch, a span and its section
     * trail, then a span named after the family and a lone section's row, all downhill only.
     */
    val sections: PathState
        get() {
            val items = persistentListOf(throughTheTrees, exitStageLeft, PathListItem.Group(familySpan), PathListItem.Group(limelight))
            val rows = throughTheTrees.rows + exitStageLeft.rows + familySpan + limelight
            val rush =
                fakePath(
                    "rush",
                    name = "Rush Trail",
                    lengthMeters = 7_564,
                    sports = listOf(SegmentSportGroup.RIDE),
                    oneWays = listOf(PathOneWay(SegmentSportGroup.RIDE, "downhill")),
                )
            return page(ContentStates.loaded(PathContent(rush, rows.toImmutableList(), content.credit, items)))
        }

    /** [sections] with Through the Trees Trail collapsed: its rows hidden, and the control offering Expand all. */
    val sectionsCollapsed: PathState
        get() = sections.copy(collapsed = persistentSetOf("Through the Trees Trail"))

    /** Every shared sample and the page's states beyond content loading, for the native page's previews and snapshots. */
    val variants: Map<String, PathState>
        get() =
            all +
                mapOf(
                    "selected" to selected,
                    "ride" to ride,
                    "empty" to empty,
                    ONE_WAY to oneWay,
                    SECTIONS to sections,
                    "sectionsCollapsed" to sectionsCollapsed,
                )

    const val ONE_WAY = "oneWay"

    const val SECTIONS = "sections"
}

private const val BURLINGTON_LAT = 44.4759
private const val BURLINGTON_LNG = -73.2121
