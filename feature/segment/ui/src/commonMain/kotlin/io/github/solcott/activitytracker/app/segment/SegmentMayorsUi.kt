package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.ChoiceChip
import io.github.solcott.activitytracker.app.ui.LeaderCard
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.RankRow
import io.github.solcott.activitytracker.app.ui.ScopeControl
import io.github.solcott.activitytracker.app.ui.rankBoardRows
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_period_all_time
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_period_month
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_period_year
import io.github.solcott.activitytracker.composeApp.resources.mayor_efforts
import io.github.solcott.activitytracker.composeApp.resources.mayor_period_last_90_days
import io.github.solcott.activitytracker.composeApp.resources.mayor_period_last_month
import io.github.solcott.activitytracker.composeApp.resources.mayor_period_last_week
import io.github.solcott.activitytracker.composeApp.resources.mayor_period_last_year
import io.github.solcott.activitytracker.composeApp.resources.mayor_period_this_week
import io.github.solcott.activitytracker.composeApp.resources.mayors_empty
import io.github.solcott.activitytracker.composeApp.resources.mayors_reached
import io.github.solcott.activitytracker.composeApp.resources.mayors_title
import io.github.solcott.activitytracker.composeApp.resources.segment_mayor_none
import io.github.solcott.activitytracker.composeApp.resources.segment_mayor_title
import io.github.solcott.activitytracker.composeApp.resources.segment_mayor_yours
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.MayorPeriod
import kotlinx.collections.immutable.toImmutableMap
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A segment's Mayors board: the period, scope, e-bike board for a ride segment and the brackets above the entries, each with its rank,
 * athlete, count and the date it was reached; the viewer's own place below them when it isn't among those loaded. Tapping an entry opens
 * the athlete's profile.
 */
@CircuitInject(SegmentMayorsScreen::class, SessionScope::class)
@Composable
fun SegmentMayorsUi(state: SegmentMayorsState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState, state.entries.canLoadMore) { state.eventSink(SegmentMayorsEvent.LoadMore) }

    ListScreen(
        title = stringResource(Res.string.mayors_title, state.name),
        onBack = { state.eventSink(SegmentMayorsEvent.Back) },
        modifier = modifier,
        listState = listState,
        verticalPadding = 12.dp,
    ) {
        item(key = "filters") { Filters(state) }
        rankBoardRows(
            state.entries,
            state.viewerEntry,
            UiError.Kind.LoadMayors,
            onRetry = { state.eventSink(SegmentMayorsEvent.Retry) },
            emptyText = Res.string.mayors_empty,
            key = { it.athlete.did.value },
        ) { entry ->
            MayorRow(entry, onClick = { state.eventSink(SegmentMayorsEvent.OpenAthlete(entry.athlete.did)) })
        }
    }
}

/**
 * An active segment's Mayor in brief, on its page (segments/segment-mayors): the leader and their count, or that it has none yet, and the
 * viewer's own count. Tapping it opens the Mayors board.
 */
@Composable
internal fun MayorCard(mayor: MayorSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LeaderCard(
        title = stringResource(Res.string.segment_mayor_title),
        leader = mayor.leader?.athlete,
        leaderValue = mayor.leader?.let { pluralStringResource(Res.plurals.mayor_efforts, it.efforts, it.efforts) }.orEmpty(),
        noneText = stringResource(Res.string.segment_mayor_none),
        viewerText = mayor.viewer?.let { pluralStringResource(Res.plurals.segment_mayor_yours, it.efforts, it.efforts) },
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun Filters(state: SegmentMayorsState) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScopeControl(state.scope, onSelect = { state.eventSink(SegmentMayorsEvent.ScopeChanged(it)) })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodChip(state.period) { state.eventSink(SegmentMayorsEvent.PeriodChanged(it)) }
            BoardTypeChip(state.board, state.boards) { state.eventSink(SegmentMayorsEvent.BoardChanged(it)) }
            BracketChips(
                state.ageGroup,
                state.weightClass,
                state.gender,
                onAgeGroup = { state.eventSink(SegmentMayorsEvent.AgeGroupChanged(it)) },
                onWeightClass = { state.eventSink(SegmentMayorsEvent.WeightClassChanged(it)) },
                onGender = { state.eventSink(SegmentMayorsEvent.GenderChanged(it)) },
            )
        }
    }
}

/** A chip choosing the board's period from a menu of every [MayorPeriod]. */
@Composable
private fun PeriodChip(period: String, onSelect: (String) -> Unit) {
    ChoiceChip(period, MayorPeriod.all.associateWith { periodLabel(it) }.toImmutableMap(), onSelect)
}

@Composable
private fun periodLabel(period: String): String =
    stringResource(
        when (period) {
            MayorPeriod.ALL_TIME -> Res.string.leaderboard_period_all_time
            MayorPeriod.THIS_YEAR -> Res.string.leaderboard_period_year
            MayorPeriod.LAST_YEAR -> Res.string.mayor_period_last_year
            MayorPeriod.THIS_MONTH -> Res.string.leaderboard_period_month
            MayorPeriod.LAST_MONTH -> Res.string.mayor_period_last_month
            MayorPeriod.THIS_WEEK -> Res.string.mayor_period_this_week
            MayorPeriod.LAST_WEEK -> Res.string.mayor_period_last_week
            else -> Res.string.mayor_period_last_90_days
        }
    )

/** One athlete's place: rank, avatar and name, the day they reached their count, and the count. */
@Composable
private fun MayorRow(entry: MayorEntry, onClick: () -> Unit) {
    RankRow(
        entry.rank,
        accented = entry.rank == 1,
        entry.athlete,
        detail = stringResource(Res.string.mayors_reached, effortDateLabel(entry.reachedOn)),
        value = pluralStringResource(Res.plurals.mayor_efforts, entry.efforts, entry.efforts),
        onClick = onClick,
    )
}
