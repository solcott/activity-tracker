package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.uistate.ContentState

/**
 * A screen that is one list: an [AppTopBar] with [title], [subtitle], a back button ([onBack]) and [actions], which follows [listState]'s
 * list as it scrolls under it, over [items] in a [ReadableList] column padded [verticalPadding] above and below. [edgeToEdge] rows (people,
 * gear) reach the window's sides on a phone; others keep a 16dp margin. Pass [listState] to page the list with [LoadMoreAtEnd].
 */
@Composable
fun ListScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    subtitle: String? = null,
    edgeToEdge: Boolean = false,
    verticalPadding: Dp = 8.dp,
    actions: @Composable RowScope.() -> Unit = {},
    items: LazyListScope.() -> Unit,
) {
    ListScaffold(title, onBack, subtitle, actions, modifier) { padding -> ListBody(listState, padding, edgeToEdge, verticalPadding, items) }
}

/**
 * A [ListScreen] whose items come from [content] (ui/content-loading): until it first loads, a spinner or why it failed ([errorKind]) with
 * [onRetry], in [ContentStateLayout]; once it has, [items] of what it holds. Show its refreshes with a [RefreshStatus] item.
 */
@Composable
fun <T> ListScreen(
    title: String,
    onBack: () -> Unit,
    content: ContentState<T>,
    errorKind: UiError.Kind,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    subtitle: String? = null,
    edgeToEdge: Boolean = false,
    verticalPadding: Dp = 8.dp,
    actions: @Composable RowScope.() -> Unit = {},
    items: LazyListScope.(T) -> Unit,
) {
    ListScaffold(title, onBack, subtitle, actions, modifier) { padding ->
        ContentStateLayout(content, errorKind, onRetry, contentPadding = padding) { loaded ->
            ListBody(listState, padding, edgeToEdge, verticalPadding) { items(loaded) }
        }
    }
}

@Composable
private fun ListScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String?,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { AppTopBar(title = title, onBack = onBack, subtitle = subtitle, scrollBehavior = scrollBehavior, actions = actions) },
        content = content,
    )
}

@Composable
private fun ListBody(
    listState: LazyListState,
    padding: PaddingValues,
    edgeToEdge: Boolean,
    verticalPadding: Dp,
    items: LazyListScope.() -> Unit,
) {
    ReadableList(listState, Modifier.fillMaxSize(), padding, minSidePadding = if (edgeToEdge) 0.dp else 16.dp) { listPadding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = listPadding + PaddingValues(vertical = verticalPadding),
            content = items,
        )
    }
}
