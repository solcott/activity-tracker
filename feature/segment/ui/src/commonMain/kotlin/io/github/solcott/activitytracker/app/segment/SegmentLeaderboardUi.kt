package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.boards.RankBoardTexts
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.OwnPlaceBar
import io.github.solcott.activitytracker.app.ui.RankRow
import io.github.solcott.activitytracker.app.ui.rankBoardRows
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_empty
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry

/**
 * A segment's leaderboard: the period, then the scope, the e-bike board for a ride segment, and the age, weight and gender brackets as
 * chips above the entries, each with its rank, athlete, time and date, the viewer's own marked; the viewer's own place pinned under the
 * list, with their gap to first, when it isn't among those loaded. Tapping an entry opens its activity. What it offers and says is
 * `SegmentBoardTexts`' and the state's.
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
        verticalPadding = 4.dp,
        header = { SegmentBoardChipRow(state.filters) },
        bottomBar = {
            OwnPlaceBar(
                state.viewerEntry?.let { own ->
                    {
                        EntryRow(
                            state,
                            own,
                            note = state.gap?.string(),
                            onClick = { state.eventSink(SegmentLeaderboardEvent.OpenActivity(own.effort.activity)) },
                        )
                    }
                },
            )
        },
    ) {
        rankBoardRows(
            state.entries,
            UiError.Kind.LoadLeaderboard,
            onRetry = { state.eventSink(SegmentLeaderboardEvent.Retry) },
            emptyText = Res.string.leaderboard_empty,
            key = { it.athlete.did.value },
        ) { entry ->
            EntryRow(state, entry, onClick = { state.eventSink(SegmentLeaderboardEvent.OpenActivity(entry.effort.activity)) })
        }
    }
}

/** One athlete's place: rank, avatar and name, their time and the day they rode it, and [note] under that. */
@Composable
private fun EntryRow(state: SegmentLeaderboardState, entry: LeaderboardEntry, onClick: () -> Unit, note: String? = null) {
    RankRow(
        RankBoardTexts.rank(entry.rank).string(),
        accented = state.accents(entry),
        entry.athlete,
        detail = SegmentBoardTexts.effortDay(entry).string(),
        value = SegmentBoardTexts.time(entry).string(),
        onClick = onClick,
        own = state.isOwn(entry),
        note = note,
    )
}
