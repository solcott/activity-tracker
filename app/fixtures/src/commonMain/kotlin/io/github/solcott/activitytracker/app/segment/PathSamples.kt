package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.MapCameraRequest
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.app.fixtures.fakePath
import io.github.solcott.activitytracker.app.fixtures.fakeSegment
import io.github.solcott.activitytracker.app.fixtures.fakeSegmentRecord
import io.github.solcott.activitytracker.app.fixtures.fakeTrailAttributes
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.GetPath
import io.github.solcott.activitytracker.lexicon.app.PathOneWay
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

/** The Trail page (segments/path-page): Ann's, its whole line and a stretch, and the states before and around them. */
object PathSamples {
    /** Ann's two segment groups, its ride segments rated (segments/trail-ratings): the whole line Easy, the stretch Intermediate. */
    private val groups =
        persistentListOf(
            SegmentGroupRow.groupOf(SegmentBrowseSamples.annsWhole.rated(imba = 1)),
            SegmentGroupRow.groupOf(SegmentBrowseSamples.annsStretch.rated(imba = 2)),
        )

    private fun List<SegmentView>.rated(imba: Int) = map {
        if (it.record.sport == SegmentSportGroup.RIDE) it.copy(trail = TrailAttributes(mtbScaleImba = imba)) else it
    }

    private val content = PathContent(SegmentBrowseSamples.annsTrail, groups, SegmentCredit.OpenStreetMap("ODbL-1.0"))

    private fun page(
        content: io.github.solcott.uistate.ContentState<PathContent>,
        sport: String? = null,
        filter: PathFilter = PathFilter(),
        query: String = "",
        selected: SegmentGroupRow? = null,
    ): PathState {
        val shown = filter.apply(content.data, query, sport, UnitSystem.Imperial)
        val profile = content.data.profile
        val base = profile?.chart(UnitSystem.Imperial)
        return PathState(
            content = content,
            sport = sport,
            filtered = shown.takeUnless { it === content.data },
            query = textFieldStateOf(query),
            filter = filter,
            options = PathFilter.options(content.data, sport, UnitSystem.Imperial),
            units = UnitSystem.Imperial,
            mapLines =
                (listOfNotNull(content.data.path?.let { MapSegmentLine(it.uri.value, Fixed.POLYLINE, lift = true) }) +
                        shown.groups.map { MapSegmentLine(it.shown.uri.value, Fixed.POLYLINE, rating = it.rating) })
                    .toImmutableList(),
            selected = selected?.shown?.uri,
            camera = MapCameraRequest(1, BURLINGTON_LAT, BURLINGTON_LNG),
            profileBase = base,
            profileChart = base?.let { profile.marked(it, shown.groups, selected?.shown?.uri?.value, UnitSystem.Imperial) },
        )
    }

    /** The page's states for each loading kind (ui/content-loading). */
    val all: Map<String, PathState>
        get() = contentSamples(PathContent(), content, ::page)

    val loaded: PathState
        get() = page(ContentStates.loaded(content))

    /** The stretch selected on the map, marked in the list. */
    val selected: PathState
        get() = loaded.copy(selected = groups[1].shown.uri)

    /** Opened from browse with Ride chosen: only ride segments, with Ride chosen in the sport chips. */
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
                sport = SegmentSportGroup.RIDE,
            )

    /**
     * Ann's terrain profile (segments/path-profile): its one piece, 8.69 km climbing to Potato Hill and down again, about 50 m between
     * heights, with the whole line's stretch along all of it and the Lupine Hiking → Potato Hill stretch along its middle.
     */
    private val annsProfile: PathProfile
        get() {
            val count = 175
            val heights = (0 until count).map { i -> (1_500 + 900 * sin(i.toDouble() / (count - 1) * PI)).roundToInt() * 10 }
            return checkNotNull(
                PathProfile.of(
                    GetPath.PathProfile(
                        pieces = listOf(GetPath.PieceProfile(piece = 0, heights = heights, lengthMeters = 8_690, maxGrade = 118)),
                        gain = 905,
                        loss = 902,
                        spans =
                            listOf(
                                GetPath.SegmentSpan(groups[0].shown.uri, 0, 0, 8_690),
                                GetPath.SegmentSpan(groups[1].shown.uri, 0, 3_100, 5_200),
                            ),
                    ),
                    SegmentBrowseSamples.annsTrail.lines,
                ),
            )
        }

    /** Ann's with its profile under the map, each row's stretch marked under it, and the climb and descent in the summary. */
    val profile: PathState
        get() = page(ContentStates.loaded(content.copy(profile = annsProfile)))

    /** [profile] with the stretch selected: its span shaded on the chart. */
    val profileSelected: PathState
        get() = page(ContentStates.loaded(content.copy(profile = annsProfile)), selected = groups[1])

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
    @Suppress("LongParameterList") // A row's every field a sample sets.
    private fun rushRow(
        rkey: String,
        name: String,
        meters: Long,
        sport: String = SegmentSportGroup.RIDE,
        imba: Int? = null,
        downhill: Boolean = true,
    ): SegmentGroupRow =
        SegmentGroupRow.groupOf(
            listOf(
                fakeSegment(
                        rkey,
                        record = fakeSegmentRecord(name, sport = sport, source = "osm", license = "ODbL-1.0"),
                        distanceMeters = meters,
                    )
                    .copy(
                        oneWay = downhill.takeIf { it },
                        climb = "downhill".takeIf { downhill },
                        trail = imba?.let { TrailAttributes(mtbScaleImba = it) },
                    ),
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

    /** Ann's with Trail details (segments/trail-attributes): every scale its data holds, the hiking grade too, under its credit. */
    val trail: PathState
        get() = page(ContentStates.loaded(content.copy(path = SegmentBrowseSamples.annsTrail.copy(trail = fakeTrailAttributes()))))

    /** [trail] with its Trail details unfolded into their cards. */
    val trailExpanded: PathState
        get() = trail.copy(detailsExpanded = true)

    /** [sections] with Through the Trees Trail collapsed: its rows hidden, and the control offering Expand all. */
    val sectionsCollapsed: PathState
        get() = sections.copy(collapsed = persistentSetOf("Through the Trees Trail"))

    /**
     * Corner Canyon (segments/path-filters): six ride rows of every rating but Extreme, one unrated, going both ways or downhill only,
     * short to long, so the search, sport chips, difficulty, direction, length and sort all show.
     */
    private val corner: PathContent
        get() {
            val rows =
                listOf(
                    rushRow("3kccrush", "Rush", 7_564, imba = 3),
                    rushRow("3kccclarks", "Clarks", 4_200, imba = 2, downhill = false),
                    rushRow("3kccldt", "Lower Bobsled", 3_100, imba = 3),
                    rushRow("3kccyb", "Ghost Falls", 2_400, imba = 1, downhill = false),
                    rushRow("3kccmk", "Maple Hollow", 1_300, imba = 0, downhill = false),
                    rushRow("3kccown", "Carol's Connector", 900, downhill = false),
                )
            val path =
                fakePath(
                    "corner",
                    name = "Corner Canyon",
                    lengthMeters = 12_000,
                    sports = listOf(SegmentSportGroup.RIDE, SegmentSportGroup.RUN, SegmentSportGroup.HIKE),
                    groupCount = rows.size,
                )
            return PathContent(path, rows.toImmutableList(), content.credit)
        }

    /** [corner] with nothing chosen: every chip offered. */
    val filters: PathState
        get() = page(ContentStates.loaded(corner), sport = SegmentSportGroup.RIDE)

    /** [corner] narrowed to Difficult, sorted shortest first. */
    val filtered: PathState
        get() =
            page(
                ContentStates.loaded(corner),
                sport = SegmentSportGroup.RIDE,
                filter = PathFilter(levels = persistentSetOf(TrailRating.DIFFICULT), sort = PathSort.Shortest),
            )

    /** [corner] searched for a name none has: no segments match. */
    val noMatches: PathState
        get() = page(ContentStates.loaded(corner), sport = SegmentSportGroup.RIDE, query = "zzz")

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
                    TRAIL to trail,
                    TRAIL_EXPANDED to trailExpanded,
                    FILTERS to filters,
                    FILTERED to filtered,
                    NO_MATCHES to noMatches,
                    PROFILE to profile,
                    PROFILE_SELECTED to profileSelected,
                )

    const val ONE_WAY = "oneWay"

    const val SECTIONS = "sections"

    const val TRAIL = "trail"
    const val TRAIL_EXPANDED = "trailExpanded"
    const val FILTERS = "filters"
    const val FILTERED = "filtered"
    const val NO_MATCHES = "noMatches"
    const val PROFILE = "profile"
    const val PROFILE_SELECTED = "profileSelected"
}

private const val BURLINGTON_LAT = 44.4759
private const val BURLINGTON_LNG = -73.2121
