package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.boards.RankBoardTexts
import io.github.solcott.activitytracker.app.ui.BoardSegments
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.RankRow
import io.github.solcott.activitytracker.app.ui.rankBoardRows
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_empty
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry

/**
 * A segment's leaderboard: the period and scope, the e-bike board for a ride segment, and the age, weight and gender brackets above the
 * entries, each with its rank, athlete, time and date; the viewer's own place below them when it isn't among those loaded. Tapping an entry
 * opens its activity. What it offers and says is `SegmentBoardTexts`' and the state's.
 */
@CircuitInject(SegmentLeaderboardScreen::class, SessionScope::class)
@Composable
fun SegmentLeaderboardUi(state: SegmentLeaderboardState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState, state.entries.canLoadMore) { state.eventSink(SegmentLeaderboardEvent.LoadMore) }

    ListScreen(
        title = state.name,
        onBack = { state.eventSink(SegmentLeaderboardEvent.Back) },
        modifier = modifier,
        listState = listState,
        verticalPadding = 12.dp,
    ) {
        item(key = "filters") { Filters(state) }
        rankBoardRows(
            state.entries,
            state.viewerEntry,
            UiError.Kind.LoadLeaderboard,
            onRetry = { state.eventSink(SegmentLeaderboardEvent.Retry) },
            emptyText = Res.string.leaderboard_empty,
            key = { it.athlete.did.value },
        ) { entry ->
            EntryRow(state, entry, onClick = { state.eventSink(SegmentLeaderboardEvent.OpenActivity(entry.effort.activity)) })
        }
    }
}

@Composable
private fun Filters(state: SegmentLeaderboardState) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoardSegments(
            state.periodChoice,
            onSelect = { it?.let { period -> state.eventSink(SegmentLeaderboardEvent.PeriodChanged(period)) } },
        )
        BoardSegments(state.scopeChoice, onSelect = { it?.let { scope -> state.eventSink(SegmentLeaderboardEvent.ScopeChanged(scope)) } })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SegmentBoardChips(
                state.typeChoice,
                state.ageGroupChoice,
                state.weightClassChoice(LocalUnitSystem.current),
                state.genderChoice,
                onType = { state.eventSink(SegmentLeaderboardEvent.BoardChanged(it)) },
                onAgeGroup = { state.eventSink(SegmentLeaderboardEvent.AgeGroupChanged(it)) },
                onWeightClass = { state.eventSink(SegmentLeaderboardEvent.WeightClassChanged(it)) },
                onGender = { state.eventSink(SegmentLeaderboardEvent.GenderChanged(it)) },
            )
        }
    }
}

/** One athlete's place: rank, avatar and name, their time and the day they rode it. */
@Composable
private fun EntryRow(state: SegmentLeaderboardState, entry: LeaderboardEntry, onClick: () -> Unit) {
    RankRow(
        RankBoardTexts.rank(entry.rank).string(),
        accented = state.accents(entry),
        entry.athlete,
        detail = SegmentBoardTexts.effortDay(entry).string(),
        value = SegmentBoardTexts.time(entry).string(),
        onClick = onClick,
    )
}
