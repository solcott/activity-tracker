package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.browse.MapSelection
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.GetPath
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.textfield.clearText
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet

/**
 * The Trail page (segments/path-page): loads the path and the segments on it, lists them one row per segment group, longest first, narrowed
 * to the browse filter it was opened with until the athlete shows every sport, and shares a selection between the map and the list as
 * browse does ([MapSelection]). Rows in one of the path's sections fold under it, expanded until the athlete collapses it; selecting a
 * row's line expands its section. The search, filters and sort (segments/path-filters) narrow and order the list and the map's lines;
 * they're retained, so they last while the page is open, and a new visit starts without them.
 */
@CircuitInject(PathScreen::class, SessionScope::class)
@Composable
@Suppress("CyclomaticComplexMethod") // One branch per page event; the choices are PathChoices'.
fun PathPresenter(screen: PathScreen, navigator: Navigator, segments: SegmentRepository, units: UnitsRepository): PathState {
    val choices = rememberRetained { PathChoices(screen.sport) }
    val sport = choices.sport
    val filter = choices.filter
    val query = choices.query
    val unitSystem by units.units.collectAsState()
    var retry by rememberRetained { mutableIntStateOf(0) }
    val selection = rememberRetained { MapSelection() }
    var collapsed by rememberRetained { mutableStateOf(persistentSetOf<String>()) }
    // Retained, not saved: a new visit opens with the Trail details folded (segments/trail-attributes).
    var detailsExpanded by rememberRetained { mutableStateOf(false) }
    // Retained, so coming back from a segment shows the page at once; a retry reloads under it. A new sport filter only regroups what
    // loaded, so it never asks again.
    val loaded = produceRetainedContentState<GetPath.Output?>(null, screen.uri, retry) { segments.pathOutcome(AtUri(screen.uri)) }
    val content =
        remember(loaded, sport) { ContentState(loaded.data?.let { contentOf(it, sport) } ?: PathContent(), loaded.origin, loaded.status) }

    // Once loaded, frame the whole path, once per path.
    val path = content.data.path
    var framed by rememberRetained { mutableStateOf<String?>(null) }
    SideEffect {
        if (path != null && framed != path.uri.value) {
            framed = path.uri.value
            selection.frame(path.lines.flatMap { SegmentGeometry.decodePolyline(it).orEmpty() })
        }
    }

    val options = remember(content.data, sport, unitSystem) { PathFilter.options(content.data, sport, unitSystem) }
    // Read in composition, so typing publishes a new state.
    val typed = query.text.toString()
    val shown = remember(content.data, filter, typed, sport, unitSystem) { filter.apply(content.data, typed, sport, unitSystem) }

    val mapLines =
        remember(content.data.path, shown) {
            (content.data.path?.let { BrowseRow.Path(it).mapSegmentLines() }.orEmpty().map { it.muted() } +
                    shown.groups.mapNotNull { it.shown.mapSegmentLine() })
                .toImmutableList()
        }

    // The profile's chart is built once per unit system; its stretches follow what the list shows and the selection.
    val profile = content.data.profile
    val profileBase = remember(profile, unitSystem) { profile?.chart(unitSystem) }
    val profileChart =
        remember(profileBase, shown, selection.selected, unitSystem) {
            profileBase?.let { base -> profile?.marked(base, shown.groups, selection.selected?.value, unitSystem) }
        }

    // A row the search or a filter hides is no longer selected; and a choice no row has any more, after the sport changed, drops.
    SideEffect {
        val selected = selection.selected
        if (selected != null && content.data.path != null && shown.groups.none { it.shown.uri == selected }) selection.selected = null
        val pruned = filter.pruned(options)
        if (content.data.path != null && pruned != filter) choices.filter = pruned
    }

    fun open(uri: AtUri) = navigator.goTo(SegmentScreen(uri.value))

    /** Shows the row of [uri]: its section, when collapsed, expands. */
    fun reveal(uri: AtUri?) {
        val section = shown.sections.find { s -> s.rows.any { it.shown.uri == uri } }
        if (section != null) collapsed = collapsed.removing(section.name)
    }

    return PathState(
        content = content,
        sport = sport,
        filtered = shown.takeUnless { it === content.data },
        query = query,
        filter = filter,
        options = options,
        units = unitSystem,
        mapLines = mapLines,
        selected = selection.selected,
        camera = selection.camera,
        collapsed = collapsed,
        detailsExpanded = detailsExpanded,
        profileBase = profileBase,
        profileChart = profileChart,
    ) { event ->
        if (choices.on(event)) return@PathState
        when (event) {
            PathEvent.Back -> navigator.pop()
            PathEvent.Retry -> retry++
            is PathEvent.ViewportChanged -> selection.viewportChanged(event.viewport)
            is PathEvent.Select -> {
                reveal(event.uri)
                selection.selected = event.uri
            }
            is PathEvent.ToggleSection ->
                collapsed = if (event.name in collapsed) collapsed.removing(event.name) else collapsed.adding(event.name)
            is PathEvent.SetAllSections ->
                collapsed = if (event.expanded) persistentSetOf() else shown.sections.map { it.name }.toPersistentSet()
            PathEvent.ToggleDetails -> detailsExpanded = !detailsExpanded
            is PathEvent.Open -> open(event.uri)
            is PathEvent.TapRow ->
                if (event.uri == selection.selected) {
                    open(event.uri)
                } else {
                    val row = shown.groups.find { it.shown.uri == event.uri }
                    selection.selectRow(event.uri, row?.let { SegmentGeometry.decodePolyline(it.shown.record.line) })
                }
            else -> Unit
        }
    }
}

/**
 * The Trail page's sport, search and filter choices (segments/path-filters), retained with the page: they last while it's open, and a new
 * visit starts with none but the [sport] browse opened it with.
 */
@Stable
private class PathChoices(sport: String?) {
    var sport by mutableStateOf(sport)
    var filter by mutableStateOf(PathFilter())
    val query = textFieldStateOf("")

    /** Takes [event] when it's a choice: true when it was one. */
    fun on(event: PathEvent): Boolean {
        when (event) {
            is PathEvent.SportChanged -> sport = event.sport
            is PathEvent.ToggleLevel -> filter = filter.toggled(event.level.rating)
            is PathEvent.ToggleWay -> filter = filter.toggled(event.way)
            is PathEvent.ToggleLength -> filter = filter.toggled(event.length.band)
            is PathEvent.SortChanged -> filter = filter.copy(sort = event.sort.sort)
            PathEvent.ClearFilters -> {
                filter = filter.cleared()
                query.clearText()
            }
            else -> return false
        }
        return true
    }
}

/**
 * [output] as the page lists it: its segments as segment groups of [sport] (or every group), longest first, then by name, with the groups
 * whose shown segment is in one of the path's sections folded under it when it holds two or more (segments/path-page). Sections and the
 * other rows run together by their longest row, then by name.
 */
internal fun contentOf(output: GetPath.Output, sport: String?): PathContent {
    val listed = output.segments.filter { sport == null || it.record.sport == sport }
    val groups =
        SegmentGroupRow.of(listed, sport)
            .sortedWith(compareByDescending<SegmentGroupRow> { it.shown.distanceMeters }.thenBy { it.shown.currentName })
    val path = output.path
    return PathContent(
        path,
        groups.toImmutableList(),
        creditOf(path.source, path.license, path.attribution),
        itemsOf(groups, output.sections, sport),
        PathProfile.of(output.profile, path.lines),
    )
}

/** [groups], longest first, as list items: those in one of [sections] with another row folded under it, the rest as rows. */
private fun itemsOf(groups: List<SegmentGroupRow>, sections: List<GetPath.SectionView>, sport: String?): ImmutableList<PathListItem> {
    val sectionOf = sections.flatMap { section -> section.segments.map { it to section.name } }.toMap()
    val bySection = groups.groupBy { sectionOf[it.shown.uri] }
    val items = groups.mapNotNull { group ->
        val name = sectionOf[group.shown.uri]
        val rows = name?.let(bySection::getValue)
        when {
            rows == null || rows.size < 2 -> PathListItem.Group(group)
            // A section is placed where its longest row would be.
            rows.first() === group ->
                PathListItem.Section(name, rows.toImmutableList(), SegmentGroupRow.groupOf(rows.flatMap { it.members }, sport))
            else -> null
        }
    }
    return items.toImmutableList()
}

/** Path lines are drawn muted under the segments, as lifts are on browse, so they're never selected or highlighted. */
private fun MapSegmentLine.muted(): MapSegmentLine = copy(lift = true, rating = null)
