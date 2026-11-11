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
import io.github.solcott.activitytracker.app.ui.BoardChoiceChip
import io.github.solcott.activitytracker.app.ui.BoardSegments
import io.github.solcott.activitytracker.app.ui.LeaderCard
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.RankRow
import io.github.solcott.activitytracker.app.ui.rankBoardRows
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.mayors_empty
import io.github.solcott.activitytracker.composeApp.resources.mayors_title
import io.github.solcott.activitytracker.composeApp.resources.segment_mayor_none
import io.github.solcott.activitytracker.composeApp.resources.segment_mayor_title
import io.github.solcott.activitytracker.composeApp.resources.segment_mayor_yours
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import org.jetbrains.compose.resources.pluralStringResource
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
            MayorRow(state, entry, onClick = { state.eventSink(SegmentMayorsEvent.OpenAthlete(entry.athlete.did)) })
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
        leaderValue = mayor.leader?.let { SegmentBoardTexts.efforts(it).string() }.orEmpty(),
        noneText = stringResource(Res.string.segment_mayor_none),
        viewerText = mayor.viewer?.let { pluralStringResource(Res.plurals.segment_mayor_yours, it.efforts, it.efforts) },
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun Filters(state: SegmentMayorsState) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoardSegments(state.scopeChoice, onSelect = { it?.let { scope -> state.eventSink(SegmentMayorsEvent.ScopeChanged(scope)) } })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoardChoiceChip(
                state.periodChoice,
                onSelect = { it?.let { period -> state.eventSink(SegmentMayorsEvent.PeriodChanged(period)) } },
            )
            SegmentBoardChips(
                state.typeChoice,
                state.ageGroupChoice,
                state.weightClassChoice(LocalUnitSystem.current),
                state.genderChoice,
                onType = { state.eventSink(SegmentMayorsEvent.BoardChanged(it)) },
                onAgeGroup = { state.eventSink(SegmentMayorsEvent.AgeGroupChanged(it)) },
                onWeightClass = { state.eventSink(SegmentMayorsEvent.WeightClassChanged(it)) },
                onGender = { state.eventSink(SegmentMayorsEvent.GenderChanged(it)) },
            )
        }
    }
}

/** One athlete's place: rank, avatar and name, the day they reached their count, and the count. */
@Composable
private fun MayorRow(state: SegmentMayorsState, entry: MayorEntry, onClick: () -> Unit) {
    RankRow(
        RankBoardTexts.rank(entry.rank).string(),
        accented = state.accents(entry),
        entry.athlete,
        detail = SegmentBoardTexts.reached(entry).string(),
        value = SegmentBoardTexts.efforts(entry).string(),
        onClick = onClick,
    )
}
