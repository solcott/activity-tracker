package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.names.OriginalName
import io.github.solcott.activitytracker.app.names.PlaceNameDialogs
import io.github.solcott.activitytracker.app.names.PlaceNames
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import kotlinx.collections.immutable.toImmutableList

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
                            SegmentPageTexts.star(starred).string(),
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
    // A lift is matched but never ranked or renamed (segments/segment-lifts): no leaderboard, Mayor or names.
    val lift = state.isLift
    val units = LocalUnitSystem.current
    ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding + PaddingValues(vertical = 12.dp)) {
            item { RefreshStatus(state.content, UiError.Kind.LoadSegment, onRetry) }
            state.polyline?.let { line ->
                item { ActivityMap(line, Modifier.fillMaxWidth(), onExpand = { state.eventSink(SegmentEvent.OpenMap) }) }
            }
            item {
                Column(Modifier.padding(vertical = 12.dp)) {
                    state.originalName?.let { OriginalName(it, Modifier.padding(bottom = 4.dp)) }
                    // Says whether it's a ski run or a ski lift, with the icon the activity page shows beside it.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SegmentKindIcon(segment.record.sport, segment.record.kind)
                        Text(SegmentPageTexts.summary(segment, units).string(), style = MaterialTheme.typography.titleMedium)
                    }
                    SegmentPageTexts.elevation(segment, units)?.let { Text(it.string(), style = MaterialTheme.typography.bodyMedium) }
                    state.profile?.let {
                        Text(SegmentPageTexts.extremes(it, units).string(), style = MaterialTheme.typography.bodyMedium)
                        SegmentProfileChart(it, Modifier.padding(top = 8.dp))
                    }
                    CreatedBy(segment.creator.handle.value)
                    state.credit?.let { Credit(it) }
                    PlaceStatus(
                        label = PlaceTexts.status(segment.status),
                        help = SegmentPageTexts.statusHelp(segment),
                        offersOriginal = SegmentPageTexts.offersOriginal(segment),
                        onOpenOriginal = { state.eventSink(SegmentEvent.OpenOriginal) },
                    )
                    if (state.offersLeaderboard) {
                        AppSecondaryButton(onClick = { state.eventSink(SegmentEvent.OpenLeaderboard) }, Modifier.padding(top = 8.dp)) {
                            Text(SegmentPageTexts.leaderboard.string())
                        }
                    }
                    state.mayor?.let { mayor ->
                        MayorCard(mayor, onClick = { state.eventSink(SegmentEvent.OpenMayors) }, Modifier.padding(top = 12.dp))
                    }
                }
            }
            if (!lift) item { SegmentNames(state, segment) }
            item { Text(SegmentPageTexts.efforts(lift).string(), style = MaterialTheme.typography.titleMedium) }
            if (state.efforts.isEmpty()) {
                item {
                    Text(
                        SegmentPageTexts.noEfforts.string(),
                        Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.efforts, key = { "${it.activity.value} ${it.startOffset}" }) { effort ->
                EffortRow(
                    effort,
                    EffortTexts.date(effort).string(),
                    onClick = { state.eventSink(SegmentEvent.OpenActivity(effort.activity)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** The segment's community names (segments/segment-names), through the shared names list: Propose, the open proposals and the history. */
@Composable
private fun SegmentNames(state: SegmentState, segment: SegmentView) {
    val proposals = remember(segment.nameProposals) { segment.nameProposals.toImmutableList() }
    val history = remember(segment.nameHistory) { segment.nameHistory.toImmutableList() }
    PlaceNames(
        proposals = proposals,
        history = history,
        canPropose = state.canPropose,
        canVote = state.canVote,
        fields = state.names,
    )
}

/** The name sheet while it's open, and the confirmation before the viewer's own proposal is deleted. */
@Composable
private fun SegmentNameDialogs(state: SegmentState) {
    PlaceNameDialogs(state.names)
}
