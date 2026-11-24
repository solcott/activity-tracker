package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.GetPath
import io.github.solcott.activitytracker.lexicon.app.PathKind
import io.github.solcott.activitytracker.lexicon.app.PathView
import kotlinx.collections.immutable.persistentListOf

/** What the Trail page says (segments/path-page, dev/shared-display-text), chosen here for the shared and the native UI. */
@Suppress("TooManyFunctions") // One per line and choice of the page, so both UIs read the same words.
object PathTexts {
    /** The page's title before the path loads. */
    val title: AppText = AppText.named("path_title")

    /** What the path is: "Trail" or "Road". */
    fun kind(path: PathView): AppText = AppText.named(if (path.kind == PathKind.ROAD) "path_kind_road" else "path_kind_trail")

    /**
     * The line under the path's name: its kind, length, how much it climbs and descends when it has a [profile] (segments/path-profile),
     * and its sport groups, with which go it one way only as a trail's row says (segments/path-page): "Trail · 5.40 mi · Ride, Run, Hike",
     * "Trail · 4.70 mi · 520 m up · 310 m down · Ride · Downhill only".
     */
    fun summary(path: PathView, units: UnitSystem): AppText = summary(path, units, profile = null)

    /** [summary] with the trail's climb and descent from its [profile] after its length (segments/path-profile). */
    fun summary(path: PathView, units: UnitSystem, profile: PathProfile?): AppText =
        (listOf(kind(path), StatText.distance(path.lengthMeters.toDouble(), units)) +
                profile?.let { PathProfileTexts.climb(it, units) }.orEmpty() +
                pathSportParts(path, path.sports))
            .reduce { line, next -> AppText.named("segment_browse_details", line, next) }

    /** A segment group's row: its name, as browse names it. */
    fun name(group: SegmentGroupRow): AppText = SegmentBrowseTexts.groupName(group)

    /** A segment group's line under its name, as browse shows it ("1.20 mi · Ride, Run · Both ways"). */
    fun details(group: SegmentGroupRow, units: UnitSystem): AppText = SegmentBrowseTexts.details(group, units)

    /** A row's leader's time, at its trailing edge (segments/path-page): "12:48". */
    fun leaderTime(leader: GetPath.SegmentLeader): AppText = SegmentBoardTexts.time(leader.leader)

    /** Who holds a row's fastest time, under it: their display name, or their handle. */
    fun leaderName(leader: GetPath.SegmentLeader): AppText = AppText.written(leader.leader.athlete.shownName)

    /**
     * The viewer's own time and place on a row's board, as the Leaderboard card says it ("You: 14:02 · #12"); null when they're not on it.
     */
    fun yours(leader: GetPath.SegmentLeader): AppText? = leader.viewer?.let(SegmentPageTexts::leaderboardYours)

    /** A section's row: the section's name, as the importer names it ("Through the Trees Trail"). */
    fun name(section: PathListItem.Section): AppText = AppText.written(section.name)

    /**
     * A section's line under its name, as a segment group's row reads, then how many rows it holds: "4.16 mi · Ride · Downhill only · 4
     * segments".
     */
    fun details(section: PathListItem.Section, units: UnitSystem): AppText =
        AppText.named(
            "segment_browse_details",
            SegmentBrowseTexts.details(section.summary, units),
            AppText.Plural("path_segment_count", section.rows.size, persistentListOf(AppText.whole(section.rows.size))),
        )

    /** What a section's row does when tapped, for assistive technology: "Collapse Through the Trees Trail", "Expand …". */
    fun toggleLabel(section: PathListItem.Section, expanded: Boolean): AppText =
        AppText.named(if (expanded) "path_section_collapse" else "path_section_expand", AppText.written(section.name))

    /** The all-sections control: "Collapse all" while every section is expanded, else "Expand all". */
    fun allSections(allExpanded: Boolean): AppText =
        AppText.named(if (allExpanded) "path_sections_collapse_all" else "path_sections_expand_all")

    /** No segment on the path that the athlete may see. */
    val noneHere: AppText = AppText.named("path_none_here")

    /** The search field's label (segments/path-filters). */
    val search: AppText = AppText.named("path_search")

    /** The iOS filter menu's label. */
    val filters: AppText = AppText.named("path_filters")

    /** A sport chip: All for null, else the sport group, as browse's chips read. */
    fun sportChoice(sport: String?): AppText = SegmentBrowseTexts.sportChoice(sport)

    /** The difficulty filter's name. */
    val difficulty: AppText = AppText.named("path_filter_difficulty")

    /** The direction filter's name. */
    val direction: AppText = AppText.named("path_filter_direction")

    /** The length filter's name. */
    val length: AppText = AppText.named("path_filter_length")

    /**
     * A filter chip: its [name] while nothing is [chosen], the choice itself when one is ("1–3 mi"), else the first and how many more
     * ("Intermediate +1").
     */
    fun filterChip(name: AppText, chosen: List<AppText>): AppText =
        when (chosen.size) {
            0 -> name
            1 -> chosen.single()
            else -> AppText.named("path_filter_more", chosen.first(), AppText.whole(chosen.size - 1))
        }

    /** What a filter chip reads as to assistive technology: its [name], then what it [shows] ("Length: 1–3 mi"). */
    fun filterDescription(name: AppText, shows: AppText): AppText =
        if (shows == name) name else AppText.named("path_filter_described", name, shows)

    /** A difficulty choice: the level's name, or Unrated. */
    fun level(option: LevelOption): AppText = option.rating?.label ?: TrailRating.unratedLabel

    /** A direction choice: "Both ways", or "Uphill only" and the like. */
    fun way(choice: WayChoice): AppText =
        choice.direction?.let { AppText.named("segment_one_way_${it.word}") } ?: AppText.named("segment_browse_both_ways")

    /** A length band in [units]: "Under 1 mi", "1–3 mi", "Over 3 mi", or the metric bands. */
    fun length(option: LengthOption, units: UnitSystem): AppText =
        AppText.named("path_length_${option.band.name.lowercase()}_${if (units == UnitSystem.Imperial) "imperial" else "metric"}")

    /** The sort chip read aloud, beside its icon: "Sort: Longest". */
    fun sortChip(option: SortOption): AppText = AppText.named("path_sort_chip", sort(option))

    /** The sort control's name. */
    val sort: AppText = AppText.named("path_sort")

    /** A sort choice: "Longest", "Most climbing"… */
    fun sort(option: SortOption): AppText =
        AppText.named(
            when (option.sort) {
                PathSort.Longest -> "path_sort_longest"
                PathSort.Shortest -> "path_sort_shortest"
                PathSort.Name -> "path_sort_name"
                PathSort.MostClimbing -> "path_sort_most_climbing"
                PathSort.EasiestFirst -> "path_sort_easiest_first"
                PathSort.HardestFirst -> "path_sort_hardest_first"
            },
        )

    /** When the search and filters leave no row. */
    val noMatches: AppText = AppText.named("path_no_matches")

    /** Clears the search and every filter but the sport. */
    val clearFilters: AppText = AppText.named("path_clear_filters")
}
