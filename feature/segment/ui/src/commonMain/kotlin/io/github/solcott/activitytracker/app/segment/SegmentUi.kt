package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.names.OriginalName
import io.github.solcott.activitytracker.app.names.PlaceNameDialogs
import io.github.solcott.activitytracker.app.names.PlaceNames
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.LeaderCard
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChip
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import kotlinx.collections.immutable.ImmutableList
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

/**
 * The segment group's sport groups and the shown group's directions (segments/segment-groups), as chips; choosing one replaces the page
 * with that segment's. A one-way sport group's one direction shows as a disabled chip ("Uphill only").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupChoices(group: GroupSwitcher, eventSink: (SegmentEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (group.showsSports) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.sports.forEach { sport ->
                    AppFilterChip(
                        selected = sport == group.currentSport,
                        onClick = { group.sportEvent(sport)?.let(eventSink) },
                        label = { Text(GroupSwitcherTexts.sport(sport).string()) },
                    )
                }
            }
        }
        val directions = group.directions
        if (group.oneWay) {
            // A one-way sport group's one direction (segments/segment-direction): shown as the choice, which can't be changed.
            AppFilterChip(
                selected = true,
                onClick = {},
                enabled = false,
                label = { Text(GroupSwitcherTexts.oneWay(directions.single()).string()) },
            )
        } else if (directions.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                directions.forEach { direction ->
                    AppFilterChip(
                        selected = group.isCurrent(direction),
                        onClick = { group.directionEvent(direction)?.let(eventSink) },
                        label = { Text(GroupSwitcherTexts.direction(direction).string()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Loaded(state: SegmentState, segment: SegmentView, padding: PaddingValues, onRetry: () -> Unit) {
    val listState = rememberLazyListState()
    // A lift is matched but never ranked or renamed (segments/segment-lifts): no leaderboard, Mayor or names.
    val lift = state.isLift
    val units = LocalUnitSystem.current
    // The parts in the order segments/segment-page gives them; a part with nothing to show is left out.
    ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = listPadding + PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { RefreshStatus(state.content, UiError.Kind.LoadSegment, onRetry, showProgress = state.showsRefreshProgress) }
            state.polyline?.let { line ->
                item { ActivityMap(line, Modifier.fillMaxWidth(), onExpand = { state.eventSink(SegmentEvent.OpenMap) }) }
            }
            item { Heading(state, segment) }
            state.group?.takeIf { it.offered }?.let { group -> item { GroupChoices(group, state.eventSink) } }
            item { Figures(state.figures(units).toImmutableList()) }
            state.profile?.let { profile -> item { ProfileCard(profile) } }
            if (state.showsBoards) item { Boards(state) }
            state.trailGroups().takeIf { it.isNotEmpty() }?.let { groups -> item { TrailDetailGroups(groups.toImmutableList()) } }
            if (!lift) item { SegmentNames(state, segment) }
            item { Text(SegmentPageTexts.efforts(lift).string(), style = MaterialTheme.typography.titleMedium) }
            if (state.efforts.isEmpty()) {
                item { Text(SegmentPageTexts.noEfforts.string(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.efforts, key = { "${it.activity.value} ${it.startOffset}" }) { effort ->
                Column {
                    EffortRow(
                        effort,
                        EffortTexts.date(effort).string(),
                        onClick = { state.eventSink(SegmentEvent.OpenActivity(effort.activity)) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            item { Footer(state, segment) }
        }
    }
}

/**
 * The heading (segments/segment-page): what it is and its status, with the ski icon the activity page shows, its current name, its original
 * name when renamed, and the trails it lies on, each opening its Trail page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Heading(state: SegmentState, segment: SegmentView) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SegmentKindIcon(segment.record.sport, segment.record.kind)
            Text(
                SegmentPageTexts.heading(segment).string(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(segment.currentName, style = MaterialTheme.typography.headlineSmall)
        state.originalName?.let { OriginalName(it) }
        if (state.trails.isNotEmpty()) {
            FlowRow(verticalArrangement = Arrangement.Center, itemVerticalAlignment = Alignment.CenterVertically) {
                Text(SegmentPageTexts.onTrails.string(), style = MaterialTheme.typography.bodyMedium)
                state.trails.forEach { trail ->
                    AppTextButton(onClick = { state.eventSink(SegmentEvent.OpenTrail(trail.uri)) }) { Text(trail.name) }
                }
            }
        }
    }
}

/** The figure row (segments/segment-page): each figure's value over its label, side by side in one card. */
@Composable
private fun Figures(figures: ImmutableList<SegmentFigure>) {
    AppCard(Modifier.testTag(SEGMENT_FIGURES_TAG)) {
        Row(Modifier.padding(vertical = 12.dp, horizontal = 4.dp)) {
            figures.forEach { figure ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(figure.value.string(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        figure.label.string(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    figure.detail?.let {
                        Text(it.string(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** The elevation profile in a card, headed by its high and low points (segments/segment-elevation). */
@Composable
private fun ProfileCard(profile: SegmentProfile) {
    val units = LocalUnitSystem.current
    AppCard {
        Column(Modifier.padding(12.dp)) {
            Text(
                SegmentPageTexts.extremes(profile, units).string(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SegmentProfileChart(profile, Modifier.padding(top = 8.dp))
        }
    }
}

/**
 * The boards row (segments/segment-page): the Leaderboard card and the Mayor card side by side, one under the other when the page is too
 * narrow for both, and the Leaderboard card across the row when the Mayor isn't shown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Boards(state: SegmentState) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 2) {
        val card = Modifier.weight(1f).widthIn(min = BOARD_CARD_MIN_WIDTH)
        LeaderboardCard(state.leaderboard, onClick = { state.eventSink(SegmentEvent.OpenLeaderboard) }, card)
        state.mayor?.let { mayor -> MayorCard(mayor, onClick = { state.eventSink(SegmentEvent.OpenMayors) }, card, stacked = true) }
    }
}

/**
 * The Leaderboard card (segments/segment-leaderboards): its leader's time, or that no one is on it, and the viewer's own time and place;
 * the title alone while [leaderboard] hasn't loaded or failed to. Tapping it opens the leaderboard.
 */
@Composable
private fun LeaderboardCard(leaderboard: LeaderboardSummary?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LeaderCard(
        title = SegmentPageTexts.leaderboard.string(),
        leader = leaderboard?.leader?.athlete,
        leaderValue = leaderboard?.leader?.let { SegmentPageTexts.leaderboardValue(it).string() }.orEmpty(),
        noneText = leaderboard?.let { SegmentPageTexts.leaderboardNone.string() },
        viewerText = leaderboard?.viewer?.let { SegmentPageTexts.leaderboardYours(it).string() },
        onClick = onClick,
        modifier = modifier,
        stacked = true,
    )
}

/** The footer (segments/segment-page): who created it, its data credit, and what its status means, with Open the original. */
@Composable
private fun Footer(state: SegmentState, segment: SegmentView) {
    Column(Modifier.padding(top = 8.dp)) {
        CreatedBy(segment.creator.handle.value)
        state.credit?.let { Credit(it) }
        PlaceStatus(
            label = PlaceTexts.status(segment.status),
            help = SegmentPageTexts.statusHelp(segment),
            offersOriginal = SegmentPageTexts.offersOriginal(segment),
            onOpenOriginal = { state.eventSink(SegmentEvent.OpenOriginal) },
        )
    }
}

/** Where a test finds the figure row. */
internal const val SEGMENT_FIGURES_TAG = "segment-figures"

/** The narrowest a board card gets before the row wraps it under the other. */
private val BOARD_CARD_MIN_WIDTH = 160.dp

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
