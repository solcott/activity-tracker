package io.github.solcott.activitytracker.app.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ActivityCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppPullToRefresh
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.feed_add
import io.github.solcott.activitytracker.composeApp.resources.feed_add_manual
import io.github.solcott.activitytracker.composeApp.resources.feed_empty_can_record
import io.github.solcott.activitytracker.composeApp.resources.feed_empty_manual_only
import io.github.solcott.activitytracker.composeApp.resources.feed_import
import io.github.solcott.activitytracker.composeApp.resources.feed_pending_writes
import io.github.solcott.activitytracker.composeApp.resources.feed_record
import io.github.solcott.activitytracker.composeApp.resources.feed_refresh
import io.github.solcott.activitytracker.composeApp.resources.feed_title
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
    val listState = rememberLazyListState()
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.feed_title),
                scrollBehavior = scrollBehavior,
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
        if (platform.isMobile) {
            AppPullToRefresh(
                isRefreshing = state.refreshing,
                onRefresh = { state.eventSink(FeedEvent.Refresh) },
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
            ) {
                Timeline(state, listState, padding, timelineModifier)
            }
        } else {
            Timeline(state, listState, padding, timelineModifier)
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
            items(state.activities, key = { it.uri.value }) { activity ->
                ActivityCard(
                    activity = activity,
                    onClick = { state.eventSink(FeedEvent.Open(activity)) },
                    onAuthorClick = { state.eventSink(FeedEvent.OpenAuthor(activity)) },
                    onOpenMedia = { state.eventSink(FeedEvent.OpenMedia(activity, it)) },
                    onKudos =
                        { state.eventSink(FeedEvent.ToggleKudos(activity)) }.takeUnless { activity.author.did.value == state.viewerDid },
                )
            }
            if (state.activities.isEmpty() && !state.refreshing) {
                item {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(if (state.canRecord) Res.string.feed_empty_can_record else Res.string.feed_empty_manual_only))
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
