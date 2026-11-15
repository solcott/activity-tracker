package io.github.solcott.activitytracker.app.divesite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import io.github.solcott.activitytracker.app.segment.BrowseList
import io.github.solcott.activitytracker.app.segment.NameScope
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.EmptyListText
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.OpenNewResultsAtTop
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.SearchField
import io.github.solcott.activitytracker.app.ui.SegmentsMap
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
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import kotlinx.collections.immutable.toImmutableList

/**
 * Dive sites around a place and sites found by name (dive-sites/site-browse): on a phone the map above the list, on a wider window the list
 * beside the map, and on web, which has no map, the search and its list alone. It has no bar of its own: the Search tab hosts it.
 */
@CircuitInject(DiveSiteBrowseScreen::class, SessionScope::class)
@Composable
fun DiveSiteBrowseUi(state: DiveSiteBrowseState, modifier: Modifier = Modifier) {
    if (state.askLocation && interactiveMapSupported) {
        val request = rememberLocationPermissionRequest { state.eventSink(DiveSiteBrowseEvent.PermissionResult(it)) }
        SideEffect(Unit) { request() }
    }
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState, state.searchingNames && state.results.canLoadMore) { state.eventSink(DiveSiteBrowseEvent.LoadMore) }
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
private fun BrowseMap(state: DiveSiteBrowseState, modifier: Modifier = Modifier) {
    SegmentsMap(
        state.mapLines,
        selected = state.selected?.value,
        camera = state.camera,
        onViewport = { state.eventSink(DiveSiteBrowseEvent.ViewportChanged(it)) },
        onSelect = { uri -> state.eventSink(DiveSiteBrowseEvent.Select(uri?.let(::AtUri))) },
        modifier = modifier,
    )
}

/** The search box, and the scope switch while a name is searched over a map. */
@Composable
private fun BrowseHeader(state: DiveSiteBrowseState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(state.query, DiveSiteBrowseTexts.searchLabel.string(), Modifier.fillMaxWidth())
        if (state.offersScope) {
            AppSegmentedControl(
                NameScope.entries.map { DiveSiteBrowseTexts.scope(it).string() }.toImmutableList(),
                selected = state.scope.ordinal,
                onSelect = { state.eventSink(DiveSiteBrowseEvent.ScopeChanged(NameScope.entries[it])) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Name results while a name is searched, else the area's sites, or why there are none to show yet. */
@Composable
private fun BrowseRows(state: DiveSiteBrowseState, listState: LazyListState, modifier: Modifier = Modifier) {
    val retry = { state.eventSink(DiveSiteBrowseEvent.Retry) }
    // Every branch fills this box, so the list keeps the share of the screen the caller gave it, loaded or not.
    Box(modifier) {
        when (val shows = state.listShows(interactiveMapSupported)) {
            BrowseList.Names -> NameResults(state, listState)
            BrowseList.Locating -> Locating()
            BrowseList.Area ->
                ContentStateLayout(state.area, UiError.Kind.LoadAreaDiveSites, retry) { area ->
                    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 8.dp)) {
                        item(key = "refresh") { RefreshStatus(state.area, UiError.Kind.LoadAreaDiveSites, retry) }
                        if (area.capped) item(key = "capped") { EmptyListText(DiveSiteBrowseTexts.capped.string()) }
                        items(area.sites, key = { it.site.uri.value }) { browsed ->
                            SiteRow(
                                browsed.site,
                                DiveSiteBrowseTexts.details(browsed, LocalUnitSystem.current),
                                selected = state.isSelected(browsed.site),
                            ) {
                                state.eventSink(DiveSiteBrowseEvent.Open(browsed.site.uri))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (area.sites.isEmpty()) item(key = "none") { EmptyListText(DiveSiteBrowseTexts.noneHere.string()) }
                    }
                }
            else -> DiveSiteBrowseTexts.message(shows)?.let { Message(it.string()) }
        }
    }
}

@Composable
private fun NameResults(state: DiveSiteBrowseState, listState: LazyListState, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 8.dp)) {
        pagedRows(
            state.results,
            UiError.Kind.SearchDiveSites,
            onRetry = { state.eventSink(DiveSiteBrowseEvent.Retry) },
            key = { it.uri.value },
            empty = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyListText(DiveSiteBrowseTexts.noResults(state).string())
                    if (state.offersSearchEverywhere) {
                        AppTextButton(onClick = { state.eventSink(DiveSiteBrowseEvent.SearchEverywhere) }) {
                            Text(DiveSiteBrowseTexts.searchEverywhere.string())
                        }
                    }
                }
            },
        ) { site ->
            SiteRow(site, DiveSiteBrowseTexts.details(site, LocalUnitSystem.current), selected = state.isSelected(site)) {
                state.eventSink(DiveSiteBrowseEvent.Open(site.uri))
            }
        }
    }
}

/** A site: its current name, then its [details] line. The one tapped on the map is marked. */
@Composable
private fun SiteRow(site: DiveSiteView, details: AppText, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(site.currentName, style = MaterialTheme.typography.titleMedium)
        Text(details.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Where the list is scrolled: the site tapped on the map is brought into view, and a new search's results open at their best match. */
@Composable
private fun ListPosition(state: DiveSiteBrowseState, listState: LazyListState) {
    LaunchedEffect(state.selected) {
        val selected = state.selected ?: return@LaunchedEffect
        val index =
            if (state.searchingNames) state.results.items.indexOfFirst { it.uri == selected }
            else
                state.area.data.sites.indexOfFirst { it.site.uri == selected }.takeIf { it >= 0 }?.let { it + areaRowsBeforeSites(state) }
                    ?: -1
        if (index >= 0) listState.animateScrollToItem(index)
    }
    OpenNewResultsAtTop(listState, if (state.searchingNames) state.results.items.firstOrNull()?.uri?.value else null)
}

/** The rows above the first site in the area's list: its refresh status, and the cap notice when shown. */
private fun areaRowsBeforeSites(state: DiveSiteBrowseState): Int = if (state.area.data.capped) 2 else 1

@Composable
private fun Locating(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppProgressIndicator()
        EmptyListText(DiveSiteBrowseTexts.message(BrowseList.Locating)?.string().orEmpty())
    }
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) { EmptyListText(text) }
}

/** The map's share of a phone's height, the list taking the rest. */
private const val MAP_WEIGHT = 1.2f

private val LIST_PANE_WIDTH = 380.dp
