package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.browse.BrowseList
import io.github.solcott.activitytracker.app.browse.MapBrowseEvent
import io.github.solcott.activitytracker.app.browse.MapBrowseState
import io.github.solcott.activitytracker.app.browse.MapBrowseTexts
import io.github.solcott.activitytracker.app.browse.NameScope
import io.github.solcott.activitytracker.app.record.rememberLocationPermissionRequest
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.app.ui.adaptive.WindowWidth
import io.github.solcott.activitytracker.app.ui.adaptive.WithWindowWidth
import kotlinx.collections.immutable.toImmutableList

/**
 * A map browse screen's layout (segments/segment-browse, dive-sites/site-browse): on a phone the [header] over the [map] over the [rows],
 * on a wider window the header and rows in a column beside the map, and on web, which has no map, the header and rows alone. Each slot is
 * given the modifier that sizes it.
 */
// One branch runs at a time, so each slot is placed once; crossing a width rebuilds them, as each screen's own layout did.
@Suppress("ContentSlotReused")
@Composable
fun MapBrowseLayout(
    header: @Composable () -> Unit,
    map: @Composable (Modifier) -> Unit,
    rows: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    WithWindowWidth(modifier.fillMaxSize()) { width ->
        when {
            !interactiveMapSupported ->
                Column(Modifier.fillMaxSize()) {
                    header()
                    rows(Modifier.weight(1f))
                }
            width == WindowWidth.Compact ->
                Column(Modifier.fillMaxSize()) {
                    header()
                    map(Modifier.fillMaxWidth().weight(MAP_WEIGHT))
                    rows(Modifier.fillMaxWidth().weight(1f))
                }
            else ->
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(LIST_PANE_WIDTH).fillMaxHeight()) {
                        header()
                        rows(Modifier.weight(1f))
                    }
                    map(Modifier.weight(1f).fillMaxHeight())
                }
        }
    }
}

/** Asks for the location permission once [browse] asks, where there's a map to place, and sends the answer. */
@Composable
fun MapBrowseLocationRequest(browse: MapBrowseState) {
    if (browse.askLocation && interactiveMapSupported) {
        val request = rememberLocationPermissionRequest { browse.eventSink(MapBrowseEvent.PermissionResult(it)) }
        SideEffect(Unit) { request() }
    }
}

/**
 * The row under a mouse pointer, in the list or as its line on the map (by its key, the line's URI): a UI's own state, highlighted on the
 * map and marked in the list while it's there.
 */
@Stable
class MapBrowseHover {
    var uri: String? by mutableStateOf(null)
}

@Composable fun rememberMapBrowseHover(): MapBrowseHover = remember { MapBrowseHover() }

/** The map of [browse]'s lines, with the selected and [hover]ed ones highlighted. */
@Composable
fun MapBrowseMap(browse: MapBrowseState, hover: MapBrowseHover, modifier: Modifier = Modifier) {
    SegmentsMap(
        browse.mapLines,
        selected = browse.selected?.value,
        camera = browse.camera,
        onViewport = { browse.eventSink(MapBrowseEvent.ViewportChanged(it)) },
        onSelect = { browse.eventSink(browse.selectEvent(it)) },
        modifier = modifier,
        hovered = hover.uri,
        onHover = { hover.uri = it },
    )
}

/** The search box labelled [searchLabel], the scope switch while a name is searched over a map, then the screen's own [extra] controls. */
@Composable
fun MapBrowseHeader(
    browse: MapBrowseState,
    searchLabel: AppText,
    modifier: Modifier = Modifier,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(browse.query, searchLabel.string(), Modifier.fillMaxWidth())
        if (browse.offersScope) {
            AppSegmentedControl(
                NameScope.entries.map { MapBrowseTexts.scope(it).string() }.toImmutableList(),
                selected = browse.scope.ordinal,
                onSelect = { browse.eventSink(MapBrowseEvent.ScopeChanged(NameScope.entries[it])) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        extra()
    }
}

/**
 * What the list shows ([MapBrowseState.listShows]): the [names] results while a name is searched, the [area]'s items, a spinner while
 * browsing finds where to open, or the screen's [message] for why there's nothing to list yet. Every branch fills the box, so the list
 * keeps the share of the screen the layout gave it, loaded or not.
 */
@Composable
fun MapBrowseRows(
    browse: MapBrowseState,
    message: (BrowseList) -> AppText?,
    names: @Composable () -> Unit,
    area: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        when (val shows = browse.listShows(interactiveMapSupported)) {
            BrowseList.Names -> names()
            BrowseList.Locating -> Locating()
            BrowseList.Area -> area()
            else -> message(shows)?.let { Message(it.string()) }
        }
    }
}

@Composable
private fun Locating(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppProgressIndicator()
        EmptyListText(MapBrowseTexts.locating.string())
    }
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) { EmptyListText(text) }
}

/** The map's share of a phone's height, the list taking the rest. */
private const val MAP_WEIGHT = 1.2f

private val LIST_PANE_WIDTH = 380.dp
