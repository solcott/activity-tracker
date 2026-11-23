package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
 * gear) reach the window's sides on a phone; others keep a 16dp margin. Pass [listState] to page the list with [LoadMoreAtEnd]. A
 * [bottomBar] stays fixed under the list, which leaves room for it, and a [header] (a board's choices) stays fixed under the bar, taking
 * the bar's colour while the list is scrolled under them.
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
    bottomBar: @Composable () -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    items: LazyListScope.() -> Unit,
) {
    ListScaffold(title, onBack, subtitle, actions, bottomBar, header, modifier) { padding ->
        ListBody(listState, padding, edgeToEdge, verticalPadding, items)
    }
}

/**
 * A [ListScreen] whose items come from [content] (ui/content-loading): until it first loads, a spinner or why it failed ([errorKind]) with
 * [onRetry], in [ContentStateLayout]; once it has, [items] of what it holds. Show its refreshes with a [RefreshStatus] item. A page whose
 * placeholder before the first load is null shows no list for it. [notFound], when set, says what the screen opened isn't there instead.
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
    notFound: String? = null,
    bottomBar: @Composable () -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    items: LazyListScope.(T & Any) -> Unit,
) {
    ListScaffold(title, onBack, subtitle, actions, bottomBar, header, modifier) { padding ->
        if (notFound != null) {
            Text(
                notFound,
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ContentStateLayout(content, errorKind, onRetry, contentPadding = padding) { loaded ->
                if (loaded != null) ListBody(listState, padding, edgeToEdge, verticalPadding) { items(loaded) }
            }
        }
    }
}

@Composable
private fun ListScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String?,
    actions: @Composable RowScope.() -> Unit,
    bottomBar: @Composable () -> Unit,
    header: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                AppTopBar(title = title, onBack = onBack, subtitle = subtitle, scrollBehavior = scrollBehavior, actions = actions)
                if (header != null) {
                    // The bar's own colours, so the two read as one while the list is scrolled under them.
                    val scrolledUnder = scrollBehavior.state.overlappedFraction > SCROLLED_UNDER
                    val colors = MaterialTheme.colorScheme
                    Box(Modifier.fillMaxWidth().background(if (scrolledUnder) colors.surfaceContainer else colors.surface)) { header() }
                }
            }
        },
        bottomBar = bottomBar,
        content = content,
    )
}

/** How far the list must be under the bar before the header takes the bar's scrolled colour, as Material's bar does. */
private const val SCROLLED_UNDER = 0.01f

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
