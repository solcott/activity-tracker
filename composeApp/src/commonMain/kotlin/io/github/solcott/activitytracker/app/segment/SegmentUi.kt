package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.segment_created_by
import io.github.solcott.activitytracker.composeApp.resources.segment_duplicate_help
import io.github.solcott.activitytracker.composeApp.resources.segment_leaderboard
import io.github.solcott.activitytracker.composeApp.resources.segment_no_efforts
import io.github.solcott.activitytracker.composeApp.resources.segment_open_original
import io.github.solcott.activitytracker.composeApp.resources.segment_star
import io.github.solcott.activitytracker.composeApp.resources.segment_status_archived_help
import io.github.solcott.activitytracker.composeApp.resources.segment_status_proposed_help
import io.github.solcott.activitytracker.composeApp.resources.segment_unstar
import io.github.solcott.activitytracker.composeApp.resources.segment_your_efforts
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import org.jetbrains.compose.resources.stringResource

/**
 * A segment: a star to keep it in the viewer's starred list, its line on a map, its original name when renamed, its length, sport group and
 * creator, its community names (segments/segment-names), then the viewer's own efforts on it, fastest first.
 */
@CircuitInject(SegmentScreen::class, SessionScope::class)
@Composable
fun SegmentUi(state: SegmentState, modifier: Modifier = Modifier) {
    val segment = state.segment
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = segment?.currentName.orEmpty(),
                onBack = { state.eventSink(SegmentEvent.Back) },
                scrollBehavior = scrollBehavior,
                actions = {
                    val starred = state.starred
                    if (segment != null && starred != null) {
                        AppIconButton(
                            AppIcon.Star,
                            stringResource(if (starred) Res.string.segment_unstar else Res.string.segment_star),
                            onClick = { state.eventSink(SegmentEvent.ToggleStar) },
                            filled = starred,
                        )
                    }
                },
            )
        },
    ) { padding ->
        val retry = { state.eventSink(SegmentEvent.Retry) }
        ContentStateLayout(state.content, UiError.Kind.LoadSegment, onRetry = retry, contentPadding = padding) { loaded ->
            // Loaded content is never null; the placeholder before the first load is.
            if (loaded != null) Loaded(state, loaded, padding, retry)
        }
    }
    SegmentNameDialogs(state)
}

@Composable
private fun Loaded(state: SegmentState, segment: SegmentView, padding: PaddingValues, onRetry: () -> Unit) {
    val listState = rememberLazyListState()
    ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding + PaddingValues(vertical = 12.dp)) {
            item { RefreshStatus(state.content, UiError.Kind.LoadSegment, onRetry) }
            state.polyline?.let { line ->
                item { ActivityMap(line, Modifier.fillMaxWidth(), onExpand = { state.eventSink(SegmentEvent.OpenMap) }) }
            }
            item {
                Column(Modifier.padding(vertical = 12.dp)) {
                    state.originalName?.let { OriginalName(it, Modifier.padding(bottom = 4.dp)) }
                    Text(
                        "${Format.distance(segment.distanceMeters.toDouble())} · ${segmentSportLabel(segment.record.sport)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(Res.string.segment_created_by, handleLabel(segment.creator.handle.value)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.credit?.let { Credit(it) }
                    Status(segment) { state.eventSink(SegmentEvent.OpenOriginal) }
                    if (SegmentStatus.of(segment.status) == SegmentStatus.ACTIVE) {
                        AppSecondaryButton(onClick = { state.eventSink(SegmentEvent.OpenLeaderboard) }, Modifier.padding(top = 8.dp)) {
                            Text(stringResource(Res.string.segment_leaderboard))
                        }
                    }
                    state.mayor?.let { mayor ->
                        MayorCard(mayor, onClick = { state.eventSink(SegmentEvent.OpenMayors) }, Modifier.padding(top = 12.dp))
                    }
                }
            }
            item { SegmentNames(state, segment) }
            item { Text(stringResource(Res.string.segment_your_efforts), style = MaterialTheme.typography.titleMedium) }
            if (state.efforts.isEmpty()) {
                item {
                    Text(
                        stringResource(Res.string.segment_no_efforts),
                        Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.efforts, key = { "${it.activity.value} ${it.startOffset}" }) { effort ->
                EffortRow(
                    effort,
                    effortDateLabel(effort.startedOn),
                    onClick = { state.eventSink(SegmentEvent.OpenActivity(effort.activity)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** A segment that isn't active: its status and what it means, and for a duplicate a link to the original. */
@Composable
private fun Status(segment: SegmentView, onOpenOriginal: () -> Unit) {
    val status = SegmentStatus.of(segment.status)
    val help =
        when (status) {
            SegmentStatus.PROPOSED -> Res.string.segment_status_proposed_help
            SegmentStatus.DUPLICATE -> Res.string.segment_duplicate_help
            SegmentStatus.ARCHIVED -> Res.string.segment_status_archived_help
            else -> return
        }
    Column(Modifier.padding(top = 8.dp)) {
        segmentStatusLabel(segment.status)?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        }
        Text(stringResource(help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (status == SegmentStatus.DUPLICATE && segment.duplicateOf != null) {
            AppTextButton(onClick = onOpenOriginal) { Text(stringResource(Res.string.segment_open_original)) }
        }
    }
}
