package io.github.solcott.activitytracker.app.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.subcircuit.SubCircuitContent
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterOuterEvent
import io.github.solcott.activitytracker.app.ui.activityItems
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppPullToRefresh
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.feed_add
import io.github.solcott.activitytracker.composeApp.resources.feed_add_manual
import io.github.solcott.activitytracker.composeApp.resources.feed_empty_can_record
import io.github.solcott.activitytracker.composeApp.resources.feed_empty_filtered
import io.github.solcott.activitytracker.composeApp.resources.feed_empty_manual_only
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_clear
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_following
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_mine
import io.github.solcott.activitytracker.composeApp.resources.feed_import
import io.github.solcott.activitytracker.composeApp.resources.feed_pending_writes
import io.github.solcott.activitytracker.composeApp.resources.feed_record
import io.github.solcott.activitytracker.composeApp.resources.feed_refresh
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The timeline. Phones pull to refresh and pointer platforms get a Refresh button. New activities come from the "+" menu, except that
 * Android records from a floating action button, its own idiom.
 */
@CircuitInject(FeedScreen::class, SessionScope::class)
@Composable
fun FeedUi(state: FeedState, modifier: Modifier = Modifier) {
    val platform = LocalUiPlatform.current
    // One scroll position per scope and filter, so a new one opens at its top rather than wherever the last list was.
    val listState = key(state.scope, state.filter) { rememberLazyListState() }
    val scopeMenu =
        state.scopes
            .map { scope -> AppMenuItem(scopeLabel(scope), checked = scope == state.scope) { state.eventSink(FeedEvent.SetScope(scope)) } }
            .toImmutableList()
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = scopeLabel(state.scope),
                scrollBehavior = scrollBehavior,
                titleMenu = scopeMenu,
                actions = {
                    if (platform.hasPointer) {
                        AppIconButton(AppIcon.Refresh, stringResource(Res.string.feed_refresh), { state.eventSink(FeedEvent.Refresh) })
                    }
                    AddActivityMenu(state, recordInMenu = platform == UiPlatform.Ios)
                },
            )
        },
        floatingActionButton = {
            if (state.canRecord && platform == UiPlatform.Android) {
                ExtendedFloatingActionButton(
                    onClick = { state.eventSink(FeedEvent.Record) },
                    icon = { PlatformIcon(AppIcon.Record, null) },
                    text = { Text(stringResource(Res.string.feed_record)) },
                )
            }
        },
    ) { padding ->
        // On the list, inside pull to refresh, so a pull at the top expands a collapsed title before it refreshes.
        val timelineModifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
        val layoutDirection = LocalLayoutDirection.current
        // The filter stays put above the list rather than scrolling in it: a lazy item that scrolls off is disposed, and the control's
        // state with it.
        val listPadding =
            PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                end = padding.calculateEndPadding(layoutDirection),
                bottom = padding.calculateBottomPadding(),
            )
        Column(Modifier.fillMaxSize()) {
            SubCircuitContent(
                state.filterScreen,
                outerEventSink = { event ->
                    if (event is FeedFilterOuterEvent.Changed) state.eventSink(FeedEvent.FilterChanged(event.filter))
                },
                // Not the list's padding: its bottom clears iOS's floating tab bar, which would open a gap under the control.
                modifier =
                    Modifier.padding(
                            start = listPadding.calculateStartPadding(layoutDirection),
                            top = padding.calculateTopPadding(),
                            end = listPadding.calculateEndPadding(layoutDirection),
                        )
                        .readableWidth()
                        .padding(vertical = 8.dp),
            )
            if (platform.isMobile) {
                AppPullToRefresh(
                    isRefreshing = state.refreshing,
                    onRefresh = { state.eventSink(FeedEvent.Refresh) },
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = listPadding,
                ) {
                    Timeline(state, listState, listPadding, timelineModifier)
                }
            } else {
                Timeline(state, listState, listPadding, timelineModifier)
            }
        }
    }
}

@Composable
private fun Timeline(state: FeedState, listState: LazyListState, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    ReadableList(listState, modifier.fillMaxSize(), contentPadding) { listPadding ->
        LazyColumn(
            state = listState,
            contentPadding = listPadding + PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.pendingWrites > 0) {
                item {
                    Text(
                        pluralStringResource(Res.plurals.feed_pending_writes, state.pendingWrites, state.pendingWrites),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            state.error?.let { item { Text(errorText(it), color = MaterialTheme.colorScheme.error) } }
            activityItems(state.activities, state.viewerDid, authorClickable = true) { state.eventSink(FeedEvent.Activity(it)) }
            if (state.activities.isEmpty() && !state.refreshing) {
                item {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.filter.isNarrowed) {
                            Text(stringResource(Res.string.feed_empty_filtered))
                            AppTextButton(onClick = { state.eventSink(FeedEvent.ClearFilters) }) {
                                Text(stringResource(Res.string.feed_filter_clear))
                            }
                        } else {
                            Text(
                                stringResource(if (state.canRecord) Res.string.feed_empty_can_record else Res.string.feed_empty_manual_only)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "+": record (when [recordInMenu] and this device can), add an activity by hand, or import a file. */
@Composable
private fun AddActivityMenu(state: FeedState, recordInMenu: Boolean, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val record = stringResource(Res.string.feed_record)
    val manual = stringResource(Res.string.feed_add_manual)
    val import = stringResource(Res.string.feed_import)
    val items =
        remember(state.canRecord, recordInMenu, record, manual, import, state.eventSink) {
            buildList {
                if (recordInMenu && state.canRecord) add(AppMenuItem(record, AppIcon.Record) { state.eventSink(FeedEvent.Record) })
                add(AppMenuItem(manual, AppIcon.ManualEntry) { state.eventSink(FeedEvent.AddManual) })
                add(AppMenuItem(import, AppIcon.Import) { state.eventSink(FeedEvent.Import) })
            }
                .toImmutableList()
        }
    Box(modifier) {
        AppIconButton(AppIcon.Add, stringResource(Res.string.feed_add), onClick = { open = true })
        AppMenu(expanded = open, onDismiss = { open = false }, items = items)
    }
}

@Composable
private fun scopeLabel(scope: FeedScope): String =
    when (scope) {
        FeedScope.Following -> stringResource(Res.string.feed_filter_following)
        FeedScope.Mine -> stringResource(Res.string.feed_filter_mine)
    }
