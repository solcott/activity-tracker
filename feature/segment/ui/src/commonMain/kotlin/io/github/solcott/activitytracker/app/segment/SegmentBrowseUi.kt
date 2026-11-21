package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.MapBrowseAreaList
import io.github.solcott.activitytracker.app.ui.MapBrowseHeader
import io.github.solcott.activitytracker.app.ui.MapBrowseHover
import io.github.solcott.activitytracker.app.ui.MapBrowseLayout
import io.github.solcott.activitytracker.app.ui.MapBrowseListPosition
import io.github.solcott.activitytracker.app.ui.MapBrowseLocationRequest
import io.github.solcott.activitytracker.app.ui.MapBrowseMap
import io.github.solcott.activitytracker.app.ui.MapBrowseNameResults
import io.github.solcott.activitytracker.app.ui.MapBrowseRows
import io.github.solcott.activitytracker.app.ui.SelectableRow
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChip
import io.github.solcott.activitytracker.app.ui.areaRowsBefore
import io.github.solcott.activitytracker.app.ui.mapBrowseAreaRows
import io.github.solcott.activitytracker.app.ui.rememberMapBrowseHover
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.data.di.SessionScope

/**
 * Trails and roads around a place, and those found by name (segments/segment-browse), laid out by [MapBrowseLayout]. It has no bar of its
 * own: the Search tab hosts it. The row under a mouse pointer, in the list or as its line on the map, is this UI's own: highlighted on the
 * map and marked in the list while it's there.
 */
@CircuitInject(SegmentBrowseScreen::class, SessionScope::class)
@Composable
fun SegmentBrowseUi(state: SegmentBrowseState, modifier: Modifier = Modifier) {
    val browse = state.browse
    MapBrowseLocationRequest(browse)
    val listState = rememberLazyListState()
    val hover = rememberMapBrowseHover()
    val retry = { state.eventSink(SegmentBrowseEvent.Retry) }
    LoadMoreAtEnd(listState, browse.searchingNames && state.results.canLoadMore) { state.eventSink(SegmentBrowseEvent.LoadMore) }
    MapBrowseListPosition(
        browse,
        listState,
        firstResult = if (browse.searchingNames) state.results.items.firstOrNull()?.key?.value else null,
    ) { selected ->
        if (browse.searchingNames) state.results.items.indexOfFirst { it.key == selected }
        else
            state.area.data.rows
                .indexOfFirst { it.row.key == selected }
                .takeIf { it >= 0 }
                ?.let { it + areaRowsBefore(state.area.data.capped) } ?: -1
    }
    MapBrowseLayout(
        header = { BrowseHeader(state) },
        map = { MapBrowseMap(browse, hover, it) },
        rows = { rowsModifier ->
            MapBrowseRows(
                browse,
                message = SegmentBrowseTexts::message,
                names = {
                    MapBrowseNameResults(
                        browse,
                        state.results,
                        UiError.Kind.SearchSegments,
                        onRetry = retry,
                        noResults = SegmentBrowseTexts.noResults(state),
                        listState = listState,
                        key = { it.key.value },
                    ) { row ->
                        BrowseRowItem(state, hover, row, SegmentBrowseTexts.details(row, LocalUnitSystem.current))
                    }
                },
                area = {
                    MapBrowseAreaList(state.area, UiError.Kind.LoadAreaSegments, retry, listState) { area ->
                        mapBrowseAreaRows(
                            area.rows,
                            area.capped,
                            SegmentBrowseTexts.capped,
                            SegmentBrowseTexts.noneHere,
                            key = { it.row.key.value },
                        ) { run ->
                            BrowseRowItem(state, hover, run.row, SegmentBrowseTexts.details(run, LocalUnitSystem.current))
                        }
                    }
                },
                modifier = rowsModifier,
            )
        },
        modifier = modifier,
    )
}

/** The search box, the scope switch while there's a map, and the sport chips. */
@Composable
private fun BrowseHeader(state: SegmentBrowseState) {
    MapBrowseHeader(state.browse, SegmentBrowseTexts.searchLabel) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppFilterChip(
                selected = state.sport == null,
                onClick = { state.eventSink(SegmentBrowseEvent.SportChanged(null)) },
                label = { Text(SegmentBrowseTexts.sportChoice(null).string()) },
            )
            for (sport in state.sports) {
                AppFilterChip(
                    selected = state.sport == sport,
                    onClick = { state.eventSink(SegmentBrowseEvent.SportChanged(sport)) },
                    label = { Text(SegmentBrowseTexts.sportChoice(sport).string()) },
                )
            }
        }
    }
}

/**
 * A row (segments/segment-browse): a trail or road, or a segment group on none, with its name and its [details] line, selected, hovered and
 * opened by its key ([SelectableRow]).
 */
@Composable
private fun BrowseRowItem(state: SegmentBrowseState, hover: MapBrowseHover, row: BrowseRow, details: AppText) {
    SelectableRow(
        key = row.key.value,
        name = SegmentBrowseTexts.name(row),
        details = details,
        selected = state.isSelected(row),
        hover = hover,
        onTap = { state.eventSink(state.tapRowEvent(row)) },
        onOpen = { state.eventSink(state.openEvent(row)) },
        rating = row.rating,
    )
}
