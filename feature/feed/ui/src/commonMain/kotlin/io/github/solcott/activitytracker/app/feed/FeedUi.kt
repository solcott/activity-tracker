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
import io.github.solcott.activitytracker.app.detail.untilText
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterOuterEvent
import io.github.solcott.activitytracker.app.posts.PostCard
import io.github.solcott.activitytracker.app.report.ReportingHost
import io.github.solcott.activitytracker.app.ui.ActivityListCard
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.ErrorText
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
import io.github.solcott.activitytracker.composeApp.resources.feed_groups_anyone
import io.github.solcott.activitytracker.composeApp.resources.feed_groups_body
import io.github.solcott.activitytracker.composeApp.resources.feed_groups_connected
import io.github.solcott.activitytracker.composeApp.resources.feed_groups_title
import io.github.solcott.activitytracker.composeApp.resources.feed_import
import io.github.solcott.activitytracker.composeApp.resources.feed_imports_paused
import io.github.solcott.activitytracker.composeApp.resources.feed_imports_waiting
import io.github.solcott.activitytracker.composeApp.resources.feed_other_changes
import io.github.solcott.activitytracker.composeApp.resources.feed_pending_writes
import io.github.solcott.activitytracker.composeApp.resources.feed_record
import io.github.solcott.activitytracker.composeApp.resources.feed_refresh
import io.github.solcott.activitytracker.composeApp.resources.feed_rejected_changes
import io.github.solcott.activitytracker.composeApp.resources.feed_segments_body
import io.github.solcott.activitytracker.composeApp.resources.feed_segments_not_now
import io.github.solcott.activitytracker.composeApp.resources.feed_segments_title
import io.github.solcott.activitytracker.composeApp.resources.feed_segments_turn_on
import io.github.solcott.activitytracker.composeApp.resources.feed_upload_paused
import io.github.solcott.activitytracker.composeApp.resources.feed_upload_paused_tomorrow
import io.github.solcott.activitytracker.composeApp.resources.feed_uploading
import io.github.solcott.activitytracker.composeApp.resources.feed_uploading_activities
import io.github.solcott.activitytracker.composeApp.resources.feed_uploading_and_imported
import io.github.solcott.activitytracker.composeApp.resources.feed_uploading_imported
import io.github.solcott.activitytracker.composeApp.resources.feed_uploading_with_other
import io.github.solcott.activitytracker.data.activity.UploadSummary
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.GroupChoice
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import io.github.solcott.uistate.isLoading
import kotlin.time.Instant
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
            if (uploads.refused > 0) {
                item(key = "rejected") {
                    AppTextButton(onClick = { state.eventSink(FeedEvent.OpenUploads) }) {
                        Text(
                            pluralStringResource(Res.plurals.feed_rejected_changes, uploads.refused, uploads.refused),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (uploads.uploading) {
                item(key = "uploading") {
                    AppTextButton(onClick = { state.eventSink(FeedEvent.OpenUploads) }) {
                        Column {
                            Text(uploadingText(uploads), style = MaterialTheme.typography.bodySmall)
                            if (uploads.importsPaused && uploads.imported > 0) {
                                Text(
                                    stringResource(Res.string.feed_imports_paused),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            uploads.serverPausedUntil?.let { until ->
                                Text(
                                    pausedUntil(until),
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
            if (state.items.isEmpty() && !state.content.isLoading) {
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

/** "Paused by the server until 3:40 PM", or "until tomorrow at" when the pause ends after midnight (data/outbox). */
@Composable
private fun pausedUntil(until: Instant): String = untilText(until, Res.string.feed_upload_paused, Res.string.feed_upload_paused_tomorrow)

/** The timeline's activities, import cards, posts and reposted posts, in their order. */
private fun LazyListScope.timelineItems(state: FeedState) {
    items(
        state.items,
        key = {
            when (it) {
                is TimelineItem.Activity -> it.view.uri.value
                is TimelineItem.ImportCard -> "importCard:${it.card.author.did.value}/${it.card.batch}"
                is TimelineItem.Post -> "post:${it.post.uri.value}"
                // A post comes once a timeline, as itself or through a repost, so its URI keys it either way.
                is TimelineItem.Repost -> "post:${it.reposted.post.uri.value}"
                is TimelineItem.Unknown -> it.hashCode()
            }
        },
    ) { item ->
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

/**
 * What is uploading, by activity (data/outbox): "Uploading 1 activity and 950 imported · 3 other changes". Imports the athlete paused are
 * waiting rather than uploading.
 */
@Composable
private fun uploadingText(uploads: UploadSummary): String {
    val own = pluralStringResource(Res.plurals.feed_uploading_activities, uploads.own, uploads.own)
    val imported = pluralStringResource(Res.plurals.feed_uploading_imported, uploads.imported, uploads.imported)
    val activities =
        when {
            uploads.own > 0 && uploads.imported > 0 -> stringResource(Res.string.feed_uploading_and_imported, own, uploads.imported)
            uploads.own > 0 -> stringResource(Res.string.feed_uploading, own)
            uploads.imported > 0 && uploads.importsPaused -> stringResource(Res.string.feed_imports_waiting, imported)
            uploads.imported > 0 -> stringResource(Res.string.feed_uploading, imported)
            else -> null
        }
    val other = pluralStringResource(Res.plurals.feed_other_changes, uploads.other, uploads.other)
    return when {
        activities != null && uploads.other > 0 -> stringResource(Res.string.feed_uploading_with_other, activities, other)
        activities != null -> activities
        else -> pluralStringResource(Res.plurals.feed_pending_writes, uploads.other, uploads.other)
    }
}
