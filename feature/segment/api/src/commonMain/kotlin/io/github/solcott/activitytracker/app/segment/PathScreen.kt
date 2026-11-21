package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.MapCameraRequest
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.MapViewport
import io.github.solcott.activitytracker.app.SegmentsMap
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.PathView
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
 * them when the path has sections (sections holding two or more of them, and the rest); empty when it has none.
 */
@Immutable
data class PathContent(
    val path: PathView? = null,
    val groups: ImmutableList<SegmentGroupRow> = persistentListOf(),
    val credit: SegmentCredit? = null,
    val sectioned: ImmutableList<PathListItem> = persistentListOf(),
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
data class PathState(
    /** The path and its segment groups (ui/content-loading). */
    val content: ContentState<PathContent>,
    /** Only segments of this `SegmentSportGroup`, or every group when null. */
    val sport: String? = null,
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
    val eventSink: (PathEvent) -> Unit = {},
) : CircuitUiState {
    /** The path, once loaded. */
    val path: PathView?
        get() = content.data.path

    /** Who to credit for the path's line, as a segment page credits its line. */
    val credit: SegmentCredit?
        get() = content.data.credit

    /** The segment groups listed: of [sport] when one is chosen. Swift reads it as an array, empty or not. */
    val groups: List<SegmentGroupRow>
        get() = content.data.groups.toList()

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

    /** Whether "Show all sports" is offered: while the list is narrowed to one sport group. */
    val offersAllSports: Boolean
        get() = sport != null

    /**
     * What the list holds, in order: sections and the rows in none. Swift reads it as an array, empty or not: a copy, since the immutable
     * library's shared empty list stops the iOS app when read through a `List`.
     */
    val items: List<PathListItem>
        get() = content.data.items.toList()

    /** The sections listed, a copy as [items] is. */
    val sections: List<PathListItem.Section>
        get() = content.data.sections.toList()

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

    /** Lists every sport group's segments. */
    data object ShowAllSports : PathEvent

    /** A tap on a section's row: collapses it when expanded, else expands it. */
    data class ToggleSection(val name: String) : PathEvent

    /** Expands every section, or collapses every one. */
    data class SetAllSections(val expanded: Boolean) : PathEvent

    /** A tap on the Trail details control: unfolds the cards, or folds them. */
    data object ToggleDetails : PathEvent
}
