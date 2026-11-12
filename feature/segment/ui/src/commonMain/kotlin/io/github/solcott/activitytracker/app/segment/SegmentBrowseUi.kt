package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.record.rememberLocationPermissionRequest
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.EmptyListText
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.OpenNewResultsAtTop
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.SearchField
import io.github.solcott.activitytracker.app.ui.SegmentsMap
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChip
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.WindowWidth
import io.github.solcott.activitytracker.app.ui.adaptive.WithWindowWidth
import io.github.solcott.activitytracker.app.ui.interactiveMapSupported
import io.github.solcott.activitytracker.app.ui.pagedRows
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import kotlinx.collections.immutable.toImmutableList

/**
 * Segments around a place and segments found by name (segments/segment-browse): on a phone the map above the list, on a wider window the
 * list beside the map, and on web, which has no map, the search and its list alone. It has no bar of its own: the Search tab hosts it.
 */
@CircuitInject(SegmentBrowseScreen::class, SessionScope::class)
@Composable
fun SegmentBrowseUi(state: SegmentBrowseState, modifier: Modifier = Modifier) {
    if (state.askLocation && interactiveMapSupported) {
        val request = rememberLocationPermissionRequest { state.eventSink(SegmentBrowseEvent.PermissionResult(it)) }
        SideEffect(Unit) { request() }
    }
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState, state.searchingNames && state.results.canLoadMore) { state.eventSink(SegmentBrowseEvent.LoadMore) }
    ListPosition(state, listState)
    WithWindowWidth(modifier.fillMaxSize()) { width ->
        when {
            !interactiveMapSupported ->
                Column(Modifier.fillMaxSize()) {
                    BrowseHeader(state)
                    BrowseRows(state, listState, Modifier.weight(1f))
                }
            width == WindowWidth.Compact ->
                Column(Modifier.fillMaxSize()) {
                    BrowseHeader(state)
                    BrowseMap(state, Modifier.fillMaxWidth().weight(MAP_WEIGHT))
                    BrowseRows(state, listState, Modifier.fillMaxWidth().weight(1f))
                }
            else ->
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(LIST_PANE_WIDTH).fillMaxHeight()) {
                        BrowseHeader(state)
                        BrowseRows(state, listState, Modifier.weight(1f))
                    }
                    BrowseMap(state, Modifier.weight(1f).fillMaxHeight())
                }
        }
    }
}

@Composable
private fun BrowseMap(state: SegmentBrowseState, modifier: Modifier = Modifier) {
    SegmentsMap(
        state.mapLines,
        selected = state.selected?.value,
        camera = state.camera,
        onViewport = { state.eventSink(SegmentBrowseEvent.ViewportChanged(it)) },
        onSelect = { uri -> state.eventSink(SegmentBrowseEvent.Select(uri?.let(::AtUri))) },
        modifier = modifier,
    )
}

/** The search box, the scope switch while there's a map, and the sport chips. */
@Composable
private fun BrowseHeader(state: SegmentBrowseState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(state.query, SegmentBrowseTexts.searchLabel.string(), Modifier.fillMaxWidth())
        if (state.offersScope) {
            AppSegmentedControl(
                NameScope.entries.map { SegmentBrowseTexts.scope(it).string() }.toImmutableList(),
                selected = state.scope.ordinal,
                onSelect = { state.eventSink(SegmentBrowseEvent.ScopeChanged(NameScope.entries[it])) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppFilterChip(
                selected = state.sport == null,
                onClick = { state.eventSink(SegmentBrowseEvent.SportChanged(null)) },
                label = { Text(SegmentBrowseTexts.allSports.string()) },
            )
            for (sport in state.sports) {
                AppFilterChip(
                    selected = state.sport == sport,
                    onClick = { state.eventSink(SegmentBrowseEvent.SportChanged(sport)) },
                    label = { Text(SegmentBrowseTexts.sport(sport).string()) },
                )
            }
        }
    }
}

/** Name results while a name is searched, else the area's runs, or why there are none to show yet. */
@Composable
private fun BrowseRows(state: SegmentBrowseState, listState: LazyListState, modifier: Modifier = Modifier) {
    val retry = { state.eventSink(SegmentBrowseEvent.Retry) }
    // Every branch fills this box, so the list keeps the share of the screen the caller gave it, loaded or not.
    Box(modifier) {
        when (val shows = state.listShows(interactiveMapSupported)) {
            BrowseList.Names -> NameResults(state, listState)
            BrowseList.Locating -> Locating()
            BrowseList.Area ->
                ContentStateLayout(state.area, UiError.Kind.LoadAreaSegments, retry) { area ->
                    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 8.dp)) {
                        item(key = "refresh") { RefreshStatus(state.area, UiError.Kind.LoadAreaSegments, retry) }
                        if (area.capped) item(key = "capped") { EmptyListText(SegmentBrowseTexts.capped.string()) }
                        items(area.runs, key = { it.segment.uri.value }) { run ->
                            SegmentRow(
                                run.segment,
                                SegmentBrowseTexts.details(run, LocalUnitSystem.current),
                                selected = run.segment.uri == state.selected,
                            ) {
                                state.eventSink(SegmentBrowseEvent.Open(run.segment.uri))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (area.runs.isEmpty()) item(key = "none") { EmptyListText(SegmentBrowseTexts.noneHere.string()) }
                    }
                }
            else -> SegmentBrowseTexts.message(shows)?.let { Message(it.string()) }
        }
    }
}

@Composable
private fun NameResults(state: SegmentBrowseState, listState: LazyListState, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 8.dp)) {
        pagedRows(
            state.results,
            UiError.Kind.SearchSegments,
            onRetry = { state.eventSink(SegmentBrowseEvent.Retry) },
            key = { it.uri.value },
            empty = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyListText(SegmentBrowseTexts.noResults(state).string())
                    if (state.offersSearchEverywhere) {
                        AppTextButton(onClick = { state.eventSink(SegmentBrowseEvent.SearchEverywhere) }) {
                            Text(SegmentBrowseTexts.searchEverywhere.string())
                        }
                    }
                }
            },
        ) { segment ->
            SegmentRow(segment, SegmentBrowseTexts.details(segment, LocalUnitSystem.current), selected = segment.uri == state.selected) {
                state.eventSink(SegmentBrowseEvent.Open(segment.uri))
            }
        }
    }
}

/** A segment: its name, then its [details] line. The one tapped on the map is marked. */
@Composable
private fun SegmentRow(segment: SegmentView, details: AppText, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(segment.currentName, style = MaterialTheme.typography.titleMedium)
        Text(details.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Where the list is scrolled: the segment tapped on the map is brought into view, and a new search's results open at their best match. */
@Composable
private fun ListPosition(state: SegmentBrowseState, listState: LazyListState) {
    LaunchedEffect(state.selected) {
        val selected = state.selected ?: return@LaunchedEffect
        val index =
            if (state.searchingNames) state.results.items.indexOfFirst { it.uri == selected }
            else
                state.area.data.runs.indexOfFirst { it.segment.uri == selected }.takeIf { it >= 0 }?.let { it + areaRowsBeforeRuns(state) }
                    ?: -1
        if (index >= 0) listState.animateScrollToItem(index)
    }
    OpenNewResultsAtTop(listState, if (state.searchingNames) state.results.items.firstOrNull()?.uri?.value else null)
}

/** The rows above the first run in the area's list: its refresh status, and the cap notice when shown. */
private fun areaRowsBeforeRuns(state: SegmentBrowseState): Int = if (state.area.data.capped) 2 else 1

@Composable
private fun Locating(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppProgressIndicator()
        EmptyListText(SegmentBrowseTexts.message(BrowseList.Locating)?.string().orEmpty())
    }
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) { EmptyListText(text) }
}

/** The map's share of a phone's height, the list taking the rest. */
private const val MAP_WEIGHT = 1.2f

private val LIST_PANE_WIDTH = 380.dp
