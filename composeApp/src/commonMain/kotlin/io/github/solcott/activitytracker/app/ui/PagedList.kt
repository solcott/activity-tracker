package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        LoadStatus.Loading -> item(key = "paged-refresh") { Spinner(small = true) }
        LoadStatus.Idle -> Unit
    }
}

/** [pagedFooter] of [state]. */
fun LazyListScope.pagedFooter(state: PagedState<*, *>, errorKind: UiError.Kind, onRetry: () -> Unit, empty: @Composable () -> Unit) =
    pagedFooter(state.hasAnswer, state.items.isEmpty(), state.refresh, state.append, errorKind, onRetry, empty)

/**
 * Below a paged list's items (ui/content-loading). Before the first answer: a spinner, or why it failed with [onRetry], or nothing while
 * the list is disabled. Once answered: [empty] when it lists nothing, and a spinner while the next page loads, or why it failed with
 * [onRetry], which tries that page again.
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
) {
    if (!hasAnswer) {
        when (refresh) {
            is LoadStatus.Failed -> item(key = "paged-footer") { FailedColumn(UiError(errorKind, refresh.error), onRetry) }
            LoadStatus.Loading -> item(key = "paged-footer") { Spinner(small = false) }
            LoadStatus.Idle -> Unit
        }
        return
    }
    if (isEmpty && refresh !is LoadStatus.Loading) item(key = "paged-empty") { empty() }
    when (append) {
        is LoadStatus.Failed -> item(key = "paged-footer") { FailedColumn(UiError(errorKind, append.error), onRetry) }
        LoadStatus.Loading -> item(key = "paged-footer") { Spinner(small = true) }
        LoadStatus.Idle -> Unit
    }
}

@Composable
private fun Spinner(small: Boolean) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { AppProgressIndicator(small = small) }
}

@Composable
private fun FailedColumn(error: UiError, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(errorText(error), color = MaterialTheme.colorScheme.error)
        AppTextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
    }
}

@Composable
private fun FailedRow(error: UiError, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(errorText(error), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
        AppTextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
    }
}
