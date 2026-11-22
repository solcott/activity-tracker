package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.EmptyListText
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.MapBrowseHover
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.SegmentsMap
import io.github.solcott.activitytracker.app.ui.SelectableRow
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.WindowWidth
import io.github.solcott.activitytracker.app.ui.adaptive.WithWindowWidth
import io.github.solcott.activitytracker.app.ui.chart.LineChartUi
import io.github.solcott.activitytracker.app.ui.interactiveMapSupported
import io.github.solcott.activitytracker.app.ui.mapBrowseHover
import io.github.solcott.activitytracker.app.ui.rememberMapBrowseHover
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.location.GeoPoint
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/**
 * The Trail page (segments/path-page): the path's name, what it is and its credit, its line with its segments over it, and its segment
 * groups, longest first. On a phone the map is above the list, on a wider window beside it, and on web, which has no map, the list alone.
 * The map and the list share a selection as browse's do, and the row or line under a mouse pointer is this UI's own. The search and filters
 * (segments/path-filters) sit in the header, over the list on every width.
 */
@CircuitInject(PathScreen::class, SessionScope::class)
@Composable
fun PathUi(state: PathState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val hover = rememberMapBrowseHover()
    // Where the profile's readout is along the chart, which the map marks (segments/path-profile); null while it isn't showing.
    var scrubbed by remember { mutableStateOf<Double?>(null) }
    // Keyed on the selection alone: collapsing or expanding another section leaves the list where it is. A selection that expands its
    // section changes both at once, so the row's index here is already the expanded one.
    LaunchedEffect(state.selected) {
        val selected = state.selected?.value ?: return@LaunchedEffect
        val index = visibleKeys(state).indexOf(selected)
        // The header and refresh rows come first.
        if (index >= 0) listState.animateScrollToItem(index + HEADER_ROWS)
    }
    AppScaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = (state.path?.name ?: PathTexts.title.string()), onBack = { state.eventSink(PathEvent.Back) }) },
    ) { padding ->
        val retry = { state.eventSink(PathEvent.Retry) }
        ContentStateLayout(state.content, UiError.Kind.LoadPath, onRetry = retry, contentPadding = padding) {
            WithWindowWidth(Modifier.fillMaxSize().padding(padding)) { width ->
                val list = @Composable { m: Modifier -> PathList(state, listState, hover, m) }
                val map = @Composable { m: Modifier -> PathMap(state, hover, state.profilePoint(scrubbed), m) }
                // The profile under the map (segments/path-profile), and under nothing on the web, which draws no map.
                val profile = @Composable { PathProfileChart(state, hover, onScrub = { scrubbed = it }) }
                when {
                    !interactiveMapSupported ->
                        Column(Modifier.fillMaxSize()) {
                            profile()
                            list(Modifier.fillMaxWidth().weight(1f))
                        }
                    width == WindowWidth.Compact ->
                        Column(Modifier.fillMaxSize()) {
                            map(Modifier.fillMaxWidth().weight(MAP_WEIGHT))
                            profile()
                            list(Modifier.fillMaxWidth().weight(1f))
                        }
                    else ->
                        Row(Modifier.fillMaxSize()) {
                            list(Modifier.width(LIST_PANE_WIDTH).fillMaxHeight())
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                map(Modifier.fillMaxWidth().weight(1f))
                                profile()
                            }
                        }
                }
            }
        }
    }
}

/**
 * The trail's elevation profile (segments/path-profile), its listed rows' stretches marked under it and the selected or hovered one shaded;
 * nothing without a profile. [onScrub] hears where its readout is, for the map's marker.
 */
@Composable
private fun PathProfileChart(state: PathState, hover: MapBrowseHover, onScrub: (Double?) -> Unit) {
    val hovered = hover.uri
    val chart = remember(state.profileChart, state.profileBase, hovered) { state.profileChart(hovered) } ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(TestTags.PATH_PROFILE)) {
        Text(state.profileTitle.string(), style = MaterialTheme.typography.titleSmall)
        LineChartUi(chart, Modifier.fillMaxWidth(), height = PROFILE_HEIGHT, onScrub = onScrub)
    }
}

@Composable
private fun PathMap(state: PathState, hover: MapBrowseHover, marker: GeoPoint?, modifier: Modifier = Modifier) {
    SegmentsMap(
        state.mapLines,
        selected = state.selected?.value,
        camera = state.camera,
        onViewport = { state.eventSink(PathEvent.ViewportChanged(it)) },
        onSelect = { uri -> state.eventSink(state.selectEvent(uri)) },
        modifier = modifier,
        hovered = hover.uri,
        onHover = { hover.uri = it },
        hoveredLines = state.sections.find { it.key == hover.uri }?.lineUris?.toImmutableSet() ?: persistentSetOf(),
        marker = marker,
    )
}

/** The keys of the list's items below its header, in order, as [PathList] lays them out: a collapsed section's rows left out. */
private fun visibleKeys(state: PathState): List<String> =
    state.items.flatMap { item ->
        when (item) {
            is PathListItem.Group -> listOf(item.key)
            is PathListItem.Section -> listOf(item.key) + if (state.isExpanded(item)) item.rows.map { it.shown.uri.value } else emptyList()
        }
    }

@Composable
private fun PathList(
    state: PathState,
    listState: LazyListState,
    hover: MapBrowseHover,
    modifier: Modifier = Modifier,
) {
    val units = LocalUnitSystem.current
    LazyColumn(modifier, state = listState) {
        item(key = "header") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                state.summary(units)?.let { Text(it.string(), style = MaterialTheme.typography.bodyMedium) }
                state.credit?.let { Credit(it) }
                if (state.offersDetails) DetailsFold(state)
                PathFilterBar(state, Modifier.padding(vertical = 8.dp))
                if (state.offersAllSections) {
                    AppTextButton(onClick = { state.eventSink(state.allSectionsEvent) }, Modifier.testTag(TestTags.PATH_ALL_SECTIONS)) {
                        Text(PathTexts.allSections(state.allSectionsExpanded).string())
                    }
                }
            }
        }
        item(key = "refresh") { RefreshStatus(state.content, UiError.Kind.LoadPath, { state.eventSink(PathEvent.Retry) }) }
        for (entry in state.shown.items) {
            when (entry) {
                is PathListItem.Group -> item(key = entry.key) { GroupRow(state, entry.group, hover, units) }
                is PathListItem.Section -> {
                    val expanded = state.isExpanded(entry)
                    item(key = entry.key) { SectionRow(state, entry, expanded, hover, units) }
                    if (expanded) {
                        items(entry.rows, key = { it.shown.uri.value }) { group ->
                            GroupRow(state, group, hover, units, Modifier.padding(start = SECTION_INDENT))
                        }
                    }
                }
            }
        }
        if (state.empty) item(key = "none") { EmptyListText(PathTexts.noneHere.string()) }
        if (state.noMatches) {
            item(key = "noMatches") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyListText(PathTexts.noMatches.string())
                    AppTextButton(onClick = { state.eventSink(PathEvent.ClearFilters) }, Modifier.testTag(TestTags.PATH_CLEAR_FILTERS)) {
                        Text(PathTexts.clearFilters.string())
                    }
                }
            }
        }
    }
}

/**
 * The Trail details folded to one line (segments/trail-attributes): up to three values and a control that unfolds the cards under it, and
 * folds them again.
 */
@Composable
private fun DetailsFold(state: PathState) {
    val expanded = state.detailsExpanded
    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            // Its hover and press fill reach past the column by the row's inner padding, so its text still lines up with the header's.
            Modifier.outdent(FOLD_PADDING)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = TrailTexts.toggleLabel(expanded).string()) { state.eventSink(PathEvent.ToggleDetails) }
                .testTag(TestTags.PATH_DETAILS_TOGGLE)
                .padding(horizontal = FOLD_PADDING, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                state.detailsSummary()?.string().orEmpty(),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(TrailTexts.title.string(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            PlatformIcon(if (expanded) AppIcon.ChevronUp else AppIcon.ChevronDown, null)
        }
        if (expanded) TrailDetailGroups(state.trailGroups().toImmutableList(), Modifier.padding(bottom = 4.dp), titled = false)
    }
}

/** Widens a full-width element by [by] on each side of the space its parent gives it, drawing it that far past the parent's padding. */
private fun Modifier.outdent(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = by.roundToPx()
    val wide =
        if (constraints.hasBoundedWidth) {
            constraints.copy(minWidth = constraints.maxWidth + 2 * extra, maxWidth = constraints.maxWidth + 2 * extra)
        } else {
            constraints
        }
    val placeable = measurable.measure(wide)
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
    layout(width, placeable.height) { placeable.place(-extra, 0) }
}

/** The Trail details row's inner padding, which its hover fill reaches past the header column by. */
private val FOLD_PADDING = 12.dp

@Composable
private fun GroupRow(state: PathState, group: SegmentGroupRow, hover: MapBrowseHover, units: UnitSystem, modifier: Modifier = Modifier) {
    Column(modifier) {
        SelectableRow(
            key = group.shown.uri.value,
            name = PathTexts.name(group),
            details = PathTexts.details(group, units),
            selected = state.isSelected(group),
            hover = hover,
            onTap = { state.eventSink(state.tapRowEvent(group)) },
            onOpen = { state.eventSink(state.openEvent(group)) },
            rating = group.rating,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * A section's row (segments/path-page): a chevron, its name and what it covers. A tap collapses or expands it, and a mouse pointer over it
 * highlights its rows' lines on the map, as [hover] under the section's key.
 */
@Composable
private fun SectionRow(
    state: PathState,
    section: PathListItem.Section,
    expanded: Boolean,
    hover: MapBrowseHover,
    units: UnitSystem,
) {
    Column {
        Row(
            Modifier.fillMaxWidth()
                .then(if (hover.uri == section.key) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier)
                .mapBrowseHover(hover, section.key)
                .clickable(onClickLabel = PathTexts.toggleLabel(section, expanded).string()) { state.eventSink(state.toggleEvent(section)) }
                .testTag(TestTags.PATH_SECTION)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlatformIcon(if (expanded) AppIcon.ChevronDown else AppIcon.Chevron, null)
            Column(Modifier.padding(start = 8.dp)) {
                Text(PathTexts.name(section).string(), style = MaterialTheme.typography.titleMedium)
                Text(
                    PathTexts.details(section, units).string(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** How far a section's rows sit in from its own. */
private val SECTION_INDENT = 24.dp

/** The rows above the first segment group: the header and the refresh status. */
private const val HEADER_ROWS = 2

/** The map's share of a phone's height, the list taking the rest. */
private const val MAP_WEIGHT = 1.2f

private val LIST_PANE_WIDTH = 380.dp

/** The profile chart's height, short enough to leave a phone room for the map and the list. */
private val PROFILE_HEIGHT = 110.dp
