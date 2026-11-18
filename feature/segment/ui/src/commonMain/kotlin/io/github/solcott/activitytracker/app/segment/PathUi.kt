package io.github.solcott.activitytracker.app.segment

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
import androidx.compose.ui.Modifier
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
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.WindowWidth
import io.github.solcott.activitytracker.app.ui.adaptive.WithWindowWidth
import io.github.solcott.activitytracker.app.ui.interactiveMapSupported
import io.github.solcott.activitytracker.app.ui.rememberMapBrowseHover
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.data.di.SessionScope

/**
 * The Trail page (segments/path-page): the path's name, what it is and its credit, its line with its segments over it, and its segment
 * groups, longest first. On a phone the map is above the list, on a wider window beside it, and on web, which has no map, the list alone.
 * The map and the list share a selection as browse's do, and the row or line under a mouse pointer is this UI's own.
 */
@CircuitInject(PathScreen::class, SessionScope::class)
@Composable
fun PathUi(state: PathState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val hover = rememberMapBrowseHover()
    LaunchedEffect(state.selected) {
        val selected = state.selected ?: return@LaunchedEffect
        val index = state.content.data.groups.indexOfFirst { it.shown.uri == selected }
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
                val map = @Composable { m: Modifier -> PathMap(state, hover, m) }
                when {
                    !interactiveMapSupported -> list(Modifier.fillMaxSize())
                    width == WindowWidth.Compact ->
                        Column(Modifier.fillMaxSize()) {
                            map(Modifier.fillMaxWidth().weight(MAP_WEIGHT))
                            list(Modifier.fillMaxWidth().weight(1f))
                        }
                    else ->
                        Row(Modifier.fillMaxSize()) {
                            list(Modifier.width(LIST_PANE_WIDTH).fillMaxHeight())
                            map(Modifier.weight(1f).fillMaxHeight())
                        }
                }
            }
        }
    }
}

@Composable
private fun PathMap(state: PathState, hover: MapBrowseHover, modifier: Modifier = Modifier) {
    SegmentsMap(
        state.mapLines,
        selected = state.selected?.value,
        camera = state.camera,
        onViewport = { state.eventSink(PathEvent.ViewportChanged(it)) },
        onSelect = { uri -> state.eventSink(state.selectEvent(uri)) },
        modifier = modifier,
        hovered = hover.uri,
        onHover = { hover.uri = it },
    )
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
                state.path?.let { Text(PathTexts.summary(it, units).string(), style = MaterialTheme.typography.bodyMedium) }
                state.credit?.let { Credit(it) }
                if (state.offersAllSports) {
                    AppTextButton(onClick = { state.eventSink(PathEvent.ShowAllSports) }) { Text(PathTexts.showAllSports.string()) }
                }
            }
        }
        item(key = "refresh") { RefreshStatus(state.content, UiError.Kind.LoadPath, { state.eventSink(PathEvent.Retry) }) }
        items(state.content.data.groups, key = { it.shown.uri.value }) { group ->
            SelectableRow(
                key = group.shown.uri.value,
                name = PathTexts.name(group),
                details = PathTexts.details(group, units),
                selected = state.isSelected(group),
                hover = hover,
                onTap = { state.eventSink(state.tapRowEvent(group)) },
                onOpen = { state.eventSink(state.openEvent(group)) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (state.empty) item(key = "none") { EmptyListText(PathTexts.noneHere.string()) }
    }
}

/** The rows above the first segment group: the header and the refresh status. */
private const val HEADER_ROWS = 2

/** The map's share of a phone's height, the list taking the rest. */
private const val MAP_WEIGHT = 1.2f

private val LIST_PANE_WIDTH = 380.dp
