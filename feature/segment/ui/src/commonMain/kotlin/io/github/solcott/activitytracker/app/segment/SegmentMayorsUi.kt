package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.boards.RankBoardTexts
import io.github.solcott.activitytracker.app.ui.LeaderCard
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.OwnPlaceBar
import io.github.solcott.activitytracker.app.ui.RankRow
import io.github.solcott.activitytracker.app.ui.rankBoardRows
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.mayors_empty
import io.github.solcott.activitytracker.composeApp.resources.mayors_title
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import org.jetbrains.compose.resources.stringResource

/**
 * A segment's Mayors board: the period, scope, e-bike board for a ride segment and the brackets above the entries, each with its rank,
 * athlete, count and the date it was reached; the viewer's own place below them when it isn't among those loaded. Tapping an entry opens
 * the athlete's profile. What it offers and says is `SegmentBoardTexts`' and the state's.
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
        verticalPadding = 4.dp,
        header = { SegmentBoardChipRow(state.filters) },
        bottomBar = {
            OwnPlaceBar(
                state.viewerEntry?.let { own ->
                    {
                        MayorRow(
                            state,
                            own,
                            note = state.gap?.string(),
                            onClick = { state.eventSink(SegmentMayorsEvent.OpenAthlete(own.athlete.did)) },
                        )
                    }
                },
            )
        },
    ) {
        rankBoardRows(
            state.entries,
            UiError.Kind.LoadMayors,
            onRetry = { state.eventSink(SegmentMayorsEvent.Retry) },
            emptyText = Res.string.mayors_empty,
            key = { it.athlete.did.value },
        ) { entry ->
            MayorRow(state, entry, onClick = { state.eventSink(SegmentMayorsEvent.OpenAthlete(entry.athlete.did)) })
        }
    }
}

/**
 * An active segment's Mayor in brief, on its page (segments/segment-mayors): the leader and their count, or that it has none yet, and the
 * viewer's own count. Tapping it opens the Mayors board.
 */
@Composable
internal fun MayorCard(mayor: MayorSummary, onClick: () -> Unit, modifier: Modifier = Modifier, stacked: Boolean = false) {
    LeaderCard(
        title = SegmentPageTexts.mayorTitle.string(),
        leader = mayor.leader?.athlete,
        leaderValue = mayor.leader?.let { SegmentPageTexts.mayorValue(it).string() }.orEmpty(),
        noneText = SegmentPageTexts.mayorNone.string(),
        viewerText = mayor.viewer?.let { SegmentPageTexts.mayorYours(it).string() },
        onClick = onClick,
        modifier = modifier,
        stacked = stacked,
    )
}

/** One athlete's place: rank, avatar and name, the day they reached their count, [note] under that, and the count. */
@Composable
private fun MayorRow(state: SegmentMayorsState, entry: MayorEntry, onClick: () -> Unit, note: String? = null) {
    RankRow(
        RankBoardTexts.rank(entry.rank).string(),
        accented = state.accents(entry),
        entry.athlete,
        detail = SegmentBoardTexts.reached(entry).string(),
        value = SegmentBoardTexts.efforts(entry).string(),
        onClick = onClick,
        own = state.isOwn(entry),
        note = note,
        crowned = state.crowned(entry),
    )
}
