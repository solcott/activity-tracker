package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.MapCameraRequest
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.app.fixtures.fakePath
import io.github.solcott.activitytracker.lexicon.app.PathOneWay
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import kotlinx.collections.immutable.persistentListOf
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

    /** Every shared sample and the page's states beyond content loading, for the native page's previews and snapshots. */
    val variants: Map<String, PathState>
        get() = all + mapOf("selected" to selected, "ride" to ride, "empty" to empty, ONE_WAY to oneWay)

    const val ONE_WAY = "oneWay"
}

private const val BURLINGTON_LAT = 44.4759
private const val BURLINGTON_LNG = -73.2121
