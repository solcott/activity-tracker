package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_retry
import io.github.solcott.uistate.LoadStatus
import io.github.solcott.uistate.PagedState
import io.github.solcott.uistate.hasAnswer
import org.jetbrains.compose.resources.stringResource

/** Asks for the next page ([onLoadMore]) once [listState]'s last row is on screen, while [canLoadMore]. */
@Composable
fun LoadMoreAtEnd(listState: LazyListState, canLoadMore: Boolean, onLoadMore: () -> Unit) {
    val atEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 1
        }
    }
    SideEffect(atEnd, canLoadMore) { if (atEnd && canLoadMore) onLoadMore() }
}

/**
 * Opens a new search's results at their best match. While a new query loads, the last results stay under a refresh row; the list anchors on
 * the row that was first, so when the new results replace it the list keeps that row's offset and hides the first match above the fold.
 * [first] is the first result's id, null while nothing is searched. Coming back to the same results keeps where the list was.
 */
@Composable
fun OpenNewResultsAtTop(listState: LazyListState, first: String?) {
    var shown by rememberSaveable { mutableStateOf(first) }
    LaunchedEffect(first) {
        if (first != null && first != shown) listState.scrollToItem(0)
        shown = first
    }
}

/** [pagedRefreshStatus] of [state]. */
fun LazyListScope.pagedRefreshStatus(state: PagedState<*, *>, errorKind: UiError.Kind, onRetry: () -> Unit) =
    pagedRefreshStatus(state.hasAnswer, state.refresh, errorKind, onRetry)

/**
 * Above a paged list's items once it has answered (ui/content-loading): a spinner while it refreshes or a new key's first page loads, or
 * why that failed with [onRetry]. Nothing before the first answer, which [pagedFooter] shows.
 */
fun LazyListScope.pagedRefreshStatus(hasAnswer: Boolean, refresh: LoadStatus, errorKind: UiError.Kind, onRetry: () -> Unit) {
    if (!hasAnswer) return
    when (refresh) {
        is LoadStatus.Failed -> item(key = "paged-refresh") { FailedRow(UiError(errorKind, refresh.error), onRetry) }
        LoadStatus.Loading -> item(key = "paged-refresh") { Spinner(small = true, TestTags.REFRESHING) }
        LoadStatus.Idle -> Unit
    }
}

/** [pagedFooter] of [state]. */
fun LazyListScope.pagedFooter(state: PagedState<*, *>, errorKind: UiError.Kind, onRetry: () -> Unit, empty: @Composable () -> Unit) =
    pagedFooter(state.hasAnswer, state.items.isEmpty(), state.refresh, state.append, errorKind, onRetry, empty, state.endReached)

/**
 * Below a paged list's items (ui/content-loading). Before the first answer: a spinner, or why it failed with [onRetry], or nothing while
 * the list is disabled. Once answered: [empty] when it lists nothing, and a spinner while the next page loads, or why it failed with
 * [onRetry], which tries that page again. Once [endReached], an empty marker for tests ([TestTags.LIST_END]).
 */
@Suppress("LongParameterList") // A paged list's statuses, taken apart for lists that lay their own items over the pages.
fun LazyListScope.pagedFooter(
    hasAnswer: Boolean,
    isEmpty: Boolean,
    refresh: LoadStatus,
    append: LoadStatus,
    errorKind: UiError.Kind,
    onRetry: () -> Unit,
    empty: @Composable () -> Unit,
    endReached: Boolean = false,
) {
    if (!hasAnswer) {
        when (refresh) {
            is LoadStatus.Failed ->
                item(key = "paged-footer") { FailedColumn(UiError(errorKind, refresh.error), onRetry, firstPage = true) }
            LoadStatus.Loading -> item(key = "paged-footer") { Spinner(small = false, TestTags.LOADING) }
            LoadStatus.Idle -> Unit
        }
        return
    }
    if (isEmpty && refresh !is LoadStatus.Loading) item(key = "paged-empty") { empty() }
    when (append) {
        is LoadStatus.Failed -> item(key = "paged-footer") { FailedColumn(UiError(errorKind, append.error), onRetry, firstPage = false) }
        LoadStatus.Loading -> item(key = "paged-footer") { Spinner(small = true, TestTags.PAGE_LOADING) }
        LoadStatus.Idle -> if (endReached && !isEmpty) item(key = "paged-end") { Spacer(Modifier.testTag(TestTags.LIST_END)) }
    }
}

/**
 * A paged list's rows (ui/content-loading): its [pagedRefreshStatus], each item's [row] keyed by [key] with a divider under it, then its
 * [pagedFooter] with [empty], or no footer when [empty] is null (a list the screen explains itself, such as one it can't load yet).
 */
@Suppress("LongParameterList") // A paged list's state, its error and retry, and the three pieces a screen fills in.
fun <T : Any> LazyListScope.pagedRows(
    state: PagedState<T, *>,
    errorKind: UiError.Kind,
    onRetry: () -> Unit,
    key: (T) -> Any,
    empty: (@Composable () -> Unit)?,
    row: @Composable (T) -> Unit,
) {
    pagedRefreshStatus(state, errorKind, onRetry)
    items(state.items, key = key) { item ->
        row(item)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    if (empty != null) pagedFooter(state, errorKind, onRetry, empty)
}

@Composable
private fun Spinner(small: Boolean, tag: String) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        AppProgressIndicator(Modifier.testTag(tag), small = small)
    }
}

@Composable
private fun FailedColumn(error: UiError, onRetry: () -> Unit, firstPage: Boolean) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            errorText(error),
            Modifier.testTag(if (firstPage) TestTags.LOAD_ERROR else TestTags.PAGE_ERROR),
            color = MaterialTheme.colorScheme.error,
        )
        AppTextButton(onClick = onRetry, Modifier.testTag(if (firstPage) TestTags.RETRY else TestTags.PAGE_RETRY)) {
            Text(stringResource(Res.string.action_retry))
        }
    }
}

@Composable
private fun FailedRow(error: UiError, onRetry: () -> Unit) {
    ErrorRetryRow(error, onRetry, Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(TestTags.REFRESH_ERROR))
}
