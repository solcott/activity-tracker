package io.github.solcott.activitytracker.app.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterOuterEvent
import io.github.solcott.activitytracker.app.posts.PostCard
import io.github.solcott.activitytracker.app.posts.QuoteSheetHost
import io.github.solcott.activitytracker.app.report.ReportingHost
import io.github.solcott.activitytracker.app.ui.ActivityListCard
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.LocalClock
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppPullToRefresh
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.feed_filter_clear
import io.github.solcott.activitytracker.composeApp.resources.feed_groups_anyone
import io.github.solcott.activitytracker.composeApp.resources.feed_groups_body
import io.github.solcott.activitytracker.composeApp.resources.feed_groups_connected
import io.github.solcott.activitytracker.composeApp.resources.feed_groups_title
import io.github.solcott.activitytracker.composeApp.resources.feed_record
import io.github.solcott.activitytracker.composeApp.resources.feed_refresh
import io.github.solcott.activitytracker.composeApp.resources.feed_segments_body
import io.github.solcott.activitytracker.composeApp.resources.feed_segments_not_now
import io.github.solcott.activitytracker.composeApp.resources.feed_segments_title
import io.github.solcott.activitytracker.composeApp.resources.feed_segments_turn_on
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.GroupChoice
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import io.github.solcott.uistate.isLoading
import kotlinx.collections.immutable.toImmutableList
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
        FeedTexts.scopes(state)
            .map { option ->
                AppMenuItem(option.label.string(), checked = option.chosen) { state.eventSink(FeedEvent.SetScope(option.scope)) }
            }
            .toImmutableList()
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = FeedTexts.title(state).string(),
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
            val refresh = { state.eventSink(FeedEvent.Refresh) }
            ContentStateLayout(state.content, UiError.Kind.LoadFeed, onRetry = refresh, contentPadding = listPadding) {
                if (platform.isMobile) {
                    AppPullToRefresh(
                        isRefreshing = state.content.isLoading,
                        onRefresh = refresh,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = listPadding,
                    ) {
                        Timeline(state, listState, listPadding, showRefreshProgress = false, timelineModifier)
                    }
                } else {
                    Timeline(state, listState, listPadding, showRefreshProgress = true, timelineModifier)
                }
            }
        }
    }
    ReportingHost(state.reporting)
    QuoteSheetHost(state.quote)
}

@Composable
private fun Timeline(
    state: FeedState,
    listState: LazyListState,
    contentPadding: PaddingValues,
    showRefreshProgress: Boolean,
    modifier: Modifier = Modifier,
) {
    ReadableList(listState, modifier.fillMaxSize(), contentPadding) { listPadding ->
        LazyColumn(
            state = listState,
            contentPadding = listPadding + PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val uploads = state.uploads
            FeedUploadTexts.refused(uploads)?.let { refused ->
                item(key = "rejected") {
                    AppTextButton(onClick = { state.eventSink(FeedEvent.OpenUploads) }) {
                        Text(refused.string(), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            FeedUploadTexts.uploading(uploads)?.let { uploading ->
                item(key = "uploading") {
                    AppTextButton(onClick = { state.eventSink(FeedEvent.OpenUploads) }) {
                        Column {
                            Text(uploading.string(), style = MaterialTheme.typography.bodySmall)
                            FeedUploadTexts.importsPaused(uploads)?.let { paused ->
                                Text(
                                    paused.string(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FeedUploadTexts.pausedUntil(uploads, LocalClock.current.now(), LocalTimeZone.current)?.let { until ->
                                Text(
                                    until.string(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            item {
                RefreshStatus(
                    state.content,
                    UiError.Kind.LoadFeed,
                    onRetry = { state.eventSink(FeedEvent.Refresh) },
                    showProgress = showRefreshProgress,
                )
            }
            state.error?.let { item { ErrorText(it) } }
            if (state.askSegments) item { SegmentsPrompt(state) }
            if (state.askGroups) item { GroupsPrompt(state) }
            timelineItems(state)
            FeedTexts.empty(state)?.let { empty ->
                item {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(empty.string())
                        if (FeedTexts.emptyOffersClear(state)) {
                            AppTextButton(onClick = { state.eventSink(FeedEvent.ClearFilters) }) {
                                Text(stringResource(Res.string.feed_filter_clear))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Asks once whether to turn segment matching on; either answer is saved, so it isn't asked again. */
@Composable
private fun SegmentsPrompt(state: FeedState, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.feed_segments_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.feed_segments_body), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                AppTextButton(onClick = { state.eventSink(FeedEvent.AnswerSegments(on = false)) }) {
                    Text(stringResource(Res.string.feed_segments_not_now))
                }
                AppButton(onClick = { state.eventSink(FeedEvent.AnswerSegments(on = true)) }) {
                    Text(stringResource(Res.string.feed_segments_turn_on))
                }
            }
        }
    }
}

/** Asks once whose activities the athlete's may be grouped with; any answer is saved, so it isn't asked again. */
@Composable
private fun GroupsPrompt(state: FeedState, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.feed_groups_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.feed_groups_body), style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppTextButton(onClick = { state.eventSink(FeedEvent.AnswerGroups(GroupChoice.OFF)) }) {
                    Text(stringResource(Res.string.feed_segments_not_now))
                }
                AppSecondaryButton(onClick = { state.eventSink(FeedEvent.AnswerGroups(GroupChoice.CONNECTED)) }) {
                    Text(stringResource(Res.string.feed_groups_connected))
                }
                AppButton(onClick = { state.eventSink(FeedEvent.AnswerGroups(GroupChoice.ANYONE)) }) {
                    Text(stringResource(Res.string.feed_groups_anyone))
                }
            }
        }
    }
}

/** "+": record (when [recordInMenu] and this device can), add an activity by hand, or import a file. */
@Composable
private fun AddActivityMenu(state: FeedState, recordInMenu: Boolean, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val items =
        FeedTexts.addChoices(state, recordInMenu)
            .map { choice -> AppMenuItem(choice.label.string(), addIcon(choice.kind)) { state.eventSink(choice.event) } }
            .toImmutableList()
    Box(modifier) {
        AppIconButton(AppIcon.Add, FeedTexts.add().string(), onClick = { open = true })
        AppMenu(expanded = open, onDismiss = { open = false }, items = items)
    }
}

private fun addIcon(kind: FeedAddKind): AppIcon =
    when (kind) {
        FeedAddKind.Record -> AppIcon.Record
        FeedAddKind.Manual -> AppIcon.ManualEntry
        FeedAddKind.Import -> AppIcon.Import
    }

/** The timeline's activities, import cards, posts and reposted posts, in their order. */
private fun LazyListScope.timelineItems(state: FeedState) {
    items(state.items, key = state::keyOf) { item ->
        when (item) {
            is TimelineItem.Activity ->
                ActivityListCard(
                    item.view,
                    state.viewerDid,
                    authorClickable = true,
                    state.revealed,
                    onEvent = { state.eventSink(FeedEvent.Activity(it)) },
                )
            is TimelineItem.ImportCard -> ImportCard(item.card, onClick = { state.eventSink(FeedEvent.OpenImportBatch(item.card)) })
            is TimelineItem.Post ->
                PostCard(item.post, state.viewerDid, state.reporting.available, onEvent = { state.eventSink(FeedEvent.Post(it)) })
            is TimelineItem.Repost ->
                PostCard(
                    item.reposted.post,
                    state.viewerDid,
                    state.reporting.available,
                    onEvent = { state.eventSink(FeedEvent.Post(it)) },
                    reason = item.reposted.reason,
                )
            is TimelineItem.Unknown -> Unit
        }
    }
}
