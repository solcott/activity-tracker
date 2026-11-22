package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.MapCameraRequest
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.MapViewport
import io.github.solcott.activitytracker.app.SegmentsMap
import io.github.solcott.activitytracker.app.chart.LineChart
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.PathView
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.textfield.TextFieldState
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The Trail page (segments/path-page): a trail or road, its line, and every segment on it the athlete may find, one row per segment group.
 * [sport], a `SegmentSportGroup` value, is the browse filter it was opened with.
 */
@CircuitSerializable(SessionScope::class) data class PathScreen(val uri: String, val sport: String? = null) : Screen

/**
 * The Trail page's content: the path, its segment groups, longest first, its data's [credit], and [sectioned], the groups as the list shows
 * them when the path has sections (sections holding two or more of them, and the rest); empty when it has none; and its terrain [profile].
 */
@Immutable
data class PathContent(
    val path: PathView? = null,
    val groups: ImmutableList<SegmentGroupRow> = persistentListOf(),
    val credit: SegmentCredit? = null,
    val sectioned: ImmutableList<PathListItem> = persistentListOf(),
    /** The path's terrain profile (segments/path-profile), null when the AppView sent none. */
    val profile: PathProfile? = null,
) {
    /** What the list shows, in order: [sectioned], or each of [groups] as a row when there are no sections. Worked out once. */
    val items: ImmutableList<PathListItem> = sectioned.ifEmpty { groups.map { PathListItem.Group(it) }.toImmutableList() }

    /** The sections among [items]. */
    val sections: ImmutableList<PathListItem.Section> = items.filterIsInstance<PathListItem.Section>().toImmutableList()
}

/** One entry of the Trail page's list (segments/path-page): a segment group's row, or a section of rows. */
@Immutable
sealed interface PathListItem {
    /** What the list keys the item by: the row's segment group, or the section's name. */
    val key: String

    /** A segment group in no section, or alone in its section. */
    @Immutable
    data class Group(val group: SegmentGroupRow) : PathListItem {
        override val key: String
            get() = group.shown.uri.value
    }

    /**
     * A section of the path's trail family holding [rows], longest first. [summary] gathers every row's members, the longest row's first,
     * so it reads like a segment group's row: its longest length, its sport groups and which ways they go.
     */
    @Immutable
    data class Section(val name: String, val rows: ImmutableList<SegmentGroupRow>, val summary: SegmentGroupRow) : PathListItem {
        override val key: String
            get() = "section:$name"

        /** Its rows' shown segments, by URI, as the map holds their lines. */
        val lineUris: Set<String>
            get() = rows.mapTo(mutableSetOf()) { it.shown.uri.value }
    }
}

@Immutable
@Suppress("TooManyFunctions") // The rules both UIs ask of the page: its selection, sections and filters, each once.
data class PathState(
    /** The path and its segment groups (ui/content-loading), before the search and filters. */
    val content: ContentState<PathContent>,
    /** Only segments of this `SegmentSportGroup`, or every group when null. */
    val sport: String? = null,
    /** [content] as the search and filters leave it (segments/path-filters); null while they leave it whole. */
    val filtered: PathContent? = null,
    /** The search field, which the presenter owns. */
    val query: TextFieldState = textFieldStateOf(""),
    /** The filter and sort choices. */
    val filter: PathFilter = PathFilter(),
    /** What the chips offer. */
    val options: PathFilterOptions = PathFilterOptions(),
    /** The athlete's units, which the length bands are named in. */
    val units: UnitSystem = UnitSystem.Metric,
    /** The lines the map draws: the path's, muted under them, and each segment group's shown segment. */
    val mapLines: ImmutableList<MapSegmentLine> = persistentListOf(),
    /** The segment group tapped on the map or in the list, by its shown segment, marked in the list. */
    val selected: AtUri? = null,
    /** Where the map should move: the whole path when loaded, or a selected row's line; a new [MapCameraRequest.id] moves it once. */
    val camera: MapCameraRequest? = null,
    /** The names of the sections collapsed; every section starts expanded. */
    val collapsed: ImmutableSet<String> = persistentSetOf(),
    /** Whether the Trail details cards show under their folded line (segments/trail-attributes); every visit starts folded. */
    val detailsExpanded: Boolean = false,
    /** The terrain profile's chart in [units] with no stretches marked (segments/path-profile); null without a profile. */
    val profileBase: LineChart? = null,
    /** [profileBase] with the listed rows' stretches marked and the selected one's shaded. */
    val profileChart: LineChart? = null,
    val eventSink: (PathEvent) -> Unit = {},
) : CircuitUiState {
    /** What the list and map show: [filtered], or all of [content] while nothing narrows it. */
    val shown: PathContent
        get() = filtered ?: content.data

    /** The path, once loaded. */
    val path: PathView?
        get() = content.data.path

    /** The path's terrain profile, once loaded; null when it has none (segments/path-profile). */
    val profile: PathProfile?
        get() = content.data.profile

    /** The line under the path's name in [units], with the trail's climb and descent when it has a profile. */
    fun summary(units: UnitSystem): AppText? = path?.let { PathTexts.summary(it, units, profile) }

    /** The profile's heading over its chart. */
    val profileTitle: AppText
        get() = AppText.named("path_profile_title")

    /** [profileChart] with [hovered]'s stretch shaded instead (a row's shown segment under the pointer), or as it is when none is. */
    fun profileChart(hovered: String?): LineChart? {
        val base = profileBase ?: return profileChart
        val listed = hovered?.takeIf { uri -> groups.any { it.shown.uri.value == uri } }
        return listed?.let { profile?.marked(base, groups, it, units) } ?: profileChart
    }

    /** Where on the trail the map marks for a profile readout at [x] along the chart; null without one or outside every piece. */
    fun profilePoint(x: Double?): GeoPoint? = x?.let { profile?.pointAt(it, units) }

    /** Who to credit for the path's line, as a segment page credits its line. */
    val credit: SegmentCredit?
        get() = content.data.credit

    /** The segment groups listed: of [sport] when one is chosen, as the search and filters leave them. Swift reads it as an array. */
    val groups: List<SegmentGroupRow>
        get() = shown.groups.toList()

    /**
     * The Trail details rows (segments/trail-attributes): every difficulty scale the served `trail` holds; empty when the AppView sent
     * none, so the section is left out.
     */
    fun trailDetails(): List<TrailDetail> = TrailTexts.rows(path?.trail, sport = null)

    /** [trailDetails] in their cards, every scale shown; empty when the AppView sent no `trail`. */
    fun trailGroups(): List<TrailDetailGroup> = TrailTexts.groups(path?.trail, sport = null)

    /** The folded Trail details line; null when there's nothing in it (the control still shows while [offersDetails]). */
    fun detailsSummary(): AppText? = TrailTexts.summary(path?.trail)

    /** Whether the Trail details line and its control show: the AppView sent a `trail` with anything in it. */
    val offersDetails: Boolean
        get() = trailGroups().isNotEmpty()

    /** Whether the search field shows. */
    val searchable: Boolean
        get() = options.searchable

    /** The sport chips' groups after All; empty when the chips don't show. A copy, for Swift. */
    val sports: List<String>
        get() = options.sports.toList()

    /** The difficulty choices; empty when the filter doesn't show. */
    val levels: List<LevelOption>
        get() = options.levels.toList()

    /** The direction choices; empty when the filter doesn't show. */
    val ways: List<WayChoice>
        get() = options.ways.toList()

    /** The length choices; empty when the filter doesn't show. */
    val lengths: List<LengthOption>
        get() = options.lengths.toList()

    /** The sort choices, Longest first. */
    val sorts: List<SortOption>
        get() = PathFilter.sorts.toList()

    /** Whether any filter is chosen (the iOS menu's filled icon). */
    val filtersActive: Boolean
        get() = filter.active

    fun isChosen(level: LevelOption): Boolean = level.rating in filter.levels

    fun isChosen(way: WayChoice): Boolean = way in filter.ways

    fun isChosen(length: LengthOption): Boolean = length.band in filter.lengths

    fun isChosen(sort: SortOption): Boolean = sort.sort == filter.sort

    /** The chosen sort. */
    val sortChoice: SortOption
        get() = SortOption(filter.sort)

    /** The difficulty chip: "Difficulty", the level chosen ("Intermediate"), or the first and how many more ("Intermediate +1"). */
    val difficultyLabel: AppText
        get() = PathTexts.filterChip(PathTexts.difficulty, levels.filter { isChosen(it) }.map(PathTexts::level))

    /** The direction chip, as [difficultyLabel] ("Downhill only"). */
    val directionLabel: AppText
        get() = PathTexts.filterChip(PathTexts.direction, ways.filter { isChosen(it) }.map(PathTexts::way))

    /** The length chip, as [difficultyLabel] ("1–3 mi"). */
    val lengthLabel: AppText
        get() = PathTexts.filterChip(PathTexts.length, lengths.filter { isChosen(it) }.map(::lengthText))

    /** What the sort chip, its icon and the chosen sort, reads as to assistive technology: "Sort: Longest". */
    val sortLabel: AppText
        get() = PathTexts.sortChip(sortChoice)

    /** [option]'s name, in the athlete's units. */
    fun lengthText(option: LengthOption): AppText = PathTexts.length(option, units)

    fun sportEvent(sport: String?): PathEvent = PathEvent.SportChanged(sport)

    fun levelEvent(level: LevelOption): PathEvent = PathEvent.ToggleLevel(level)

    fun wayEvent(way: WayChoice): PathEvent = PathEvent.ToggleWay(way)

    fun lengthEvent(length: LengthOption): PathEvent = PathEvent.ToggleLength(length)

    fun sortEvent(sort: SortOption): PathEvent = PathEvent.SortChanged(sort)

    /** Whether the search and filters leave nothing of a path that has segments to list. */
    val noMatches: Boolean
        get() = !empty && content.data.path != null && shown.groups.isEmpty()

    /**
     * What the list holds, in order: sections and the rows in none. Swift reads it as an array, empty or not: a copy, since the immutable
     * library's shared empty list stops the iOS app when read through a `List`.
     */
    val items: List<PathListItem>
        get() = shown.items.toList()

    /** The sections listed, a copy as [items] is. */
    val sections: List<PathListItem.Section>
        get() = shown.sections.toList()

    /** Whether [section]'s rows are shown. */
    fun isExpanded(section: PathListItem.Section): Boolean = section.name !in collapsed

    /** Whether the list offers to expand or collapse every section at once: with two or more sections. */
    val offersAllSections: Boolean
        get() = sections.size >= 2

    /** Whether every section is expanded, so the all-sections control collapses them; else it expands them. */
    val allSectionsExpanded: Boolean
        get() = sections.none { it.name in collapsed }

    /** The event for the all-sections control. */
    val allSectionsEvent: PathEvent
        get() = PathEvent.SetAllSections(expanded = !allSectionsExpanded)

    /** The event for a tap on [section]'s row. */
    fun toggleEvent(section: PathListItem.Section): PathEvent = PathEvent.ToggleSection(section.name)

    /** Whether there's nothing to list once loaded. */
    val empty: Boolean
        get() = content.data.path != null && content.data.groups.isEmpty()

    /** The map's lines with the selected group highlighted. */
    val segmentsMap: SegmentsMap
        get() = SegmentsMap.of(mapLines, selected?.value)

    /** [segmentsMap] with [hovered], a row or line under the pointer (a UI's own state), highlighted too. */
    fun segmentsMap(hovered: String?): SegmentsMap = SegmentsMap.of(mapLines, selected?.value, hovered)

    /** Whether [group]'s row is the one selected. */
    fun isSelected(group: SegmentGroupRow): Boolean = group.shown.uri == selected

    /** The event for a tap on the map that found [uri] (a line's URI as the map holds it), or none. */
    fun selectEvent(uri: String?): PathEvent = PathEvent.Select(uri?.let(::AtUri))

    /** The event for a tap on [group]'s row on a touch screen: it selects the row, or opens it when it's selected. */
    fun tapRowEvent(group: SegmentGroupRow): PathEvent = PathEvent.TapRow(group.shown.uri)

    /** The event for a click on [group]'s row with a pointer: opens it. */
    fun openEvent(group: SegmentGroupRow): PathEvent = PathEvent.Open(group.shown.uri)
}

sealed interface PathEvent {
    data object Back : PathEvent

    data object Retry : PathEvent

    /** The map stopped moving and shows [viewport]. */
    data class ViewportChanged(val viewport: MapViewport) : PathEvent

    /** A tap on a segment's line on the map, or null for a tap away from every line. */
    data class Select(val uri: AtUri?) : PathEvent

    /** A tap on a row on a touch screen: opens it when selected, else selects it, moving the map to show its line when off screen. */
    data class TapRow(val uri: AtUri) : PathEvent

    /** Opens a segment group's page: a click on its row, or a tap on the selected one. */
    data class Open(val uri: AtUri) : PathEvent

    /** A sport chip: lists [sport]'s segments, or every group's for null. */
    data class SportChanged(val sport: String?) : PathEvent

    /** Chooses a difficulty level, or unchooses it. */
    data class ToggleLevel(val level: LevelOption) : PathEvent

    /** Chooses a direction, or unchooses it. */
    data class ToggleWay(val way: WayChoice) : PathEvent

    /** Chooses a length band, or unchooses it. */
    data class ToggleLength(val length: LengthOption) : PathEvent

    data class SortChanged(val sort: SortOption) : PathEvent

    /** Clears the search and every filter; the sport and sort stay. */
    data object ClearFilters : PathEvent

    /** A tap on a section's row: collapses it when expanded, else expands it. */
    data class ToggleSection(val name: String) : PathEvent

    /** Expands every section, or collapses every one. */
    data class SetAllSections(val expanded: Boolean) : PathEvent

    /** A tap on the Trail details control: unfolds the cards, or folds them. */
    data object ToggleDetails : PathEvent
}
