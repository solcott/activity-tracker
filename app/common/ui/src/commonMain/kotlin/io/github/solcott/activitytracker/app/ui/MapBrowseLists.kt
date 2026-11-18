package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.browse.MapBrowseEvent
import io.github.solcott.activitytracker.app.browse.MapBrowseState
import io.github.solcott.activitytracker.app.browse.MapBrowseTexts
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.PagedState

// The lists a map browse screen shows under its header (segments/segment-browse, dive-sites/site-browse), and where they're scrolled.

/** The [area]'s list once loaded: its refresh status over the screen's [rows] of it (ui/content-loading). */
@Composable
fun <A> MapBrowseAreaList(
    area: ContentState<A>,
    errorKind: UiError.Kind,
    onRetry: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    rows: LazyListScope.(A) -> Unit,
) {
    ContentStateLayout(area, errorKind, onRetry, modifier) { loaded ->
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 8.dp)) {
            item(key = "refresh") { RefreshStatus(area, errorKind, onRetry) }
            rows(loaded)
        }
    }
}

/**
 * An area's [items], nearest first, each [row] keyed by [key] with a divider under it: under [cappedText] when the area holds more than
 * were read ([capped]), or [noneText] when it holds none. [MapBrowseListPosition] counts the rows above them ([areaRowsBefore]).
 */
@Suppress("LongParameterList") // The items and their key and row, and the two notes around them.
fun <T : Any> LazyListScope.mapBrowseAreaRows(
    items: List<T>,
    capped: Boolean,
    cappedText: AppText,
    noneText: AppText,
    key: (T) -> Any,
    row: @Composable (T) -> Unit,
) {
    if (capped) item(key = "capped") { EmptyListText(cappedText.string()) }
    items(items, key = key) { item ->
        row(item)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    if (items.isEmpty()) item(key = "none") { EmptyListText(noneText.string()) }
}

/** The rows above an area's first item in [MapBrowseAreaList]: its refresh status, and the cap notice when shown. */
fun areaRowsBefore(capped: Boolean): Int = if (capped) 2 else 1

/** Name [results] page by page, each [row] keyed by [key]; with none, [noResults] and, when [browse] offers it, Search everywhere. */
@Composable
fun <T : Any> MapBrowseNameResults(
    browse: MapBrowseState,
    results: PagedState<T, Unit>,
    errorKind: UiError.Kind,
    onRetry: () -> Unit,
    noResults: AppText,
    listState: LazyListState,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    row: @Composable (T) -> Unit,
) {
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 8.dp)) {
        pagedRows(
            results,
            errorKind,
            onRetry = onRetry,
            key = key,
            empty = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyListText(noResults.string())
                    if (browse.offersSearchEverywhere) {
                        AppTextButton(onClick = { browse.eventSink(MapBrowseEvent.SearchEverywhere) }) {
                            Text(MapBrowseTexts.searchEverywhere.string())
                        }
                    }
                }
            },
            row = row,
        )
    }
}

/**
 * Where the list is scrolled: the selected item is brought into view (never the hovered one), at the index [indexOf] gives for it (-1 when
 * it isn't listed), and a new search's results open at their best match, whose key is [firstResult] (null while no name is searched).
 */
@Composable
fun MapBrowseListPosition(browse: MapBrowseState, listState: LazyListState, firstResult: String?, indexOf: (AtUri) -> Int) {
    val currentIndexOf by rememberUpdatedState(indexOf)
    LaunchedEffect(browse.selected) {
        val selected = browse.selected ?: return@LaunchedEffect
        val index = currentIndexOf(selected)
        if (index >= 0) listState.animateScrollToItem(index)
    }
    OpenNewResultsAtTop(listState, firstResult)
}
