package io.github.solcott.activitytracker.app.divesite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.RankRow
import io.github.solcott.activitytracker.app.ui.rankBoardRows
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.dive_site_regulars_empty
import io.github.solcott.activitytracker.composeApp.resources.dive_site_regulars_title
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRegularEntry
import org.jetbrains.compose.resources.stringResource

/**
 * A dive site's Regulars board (dive-sites/site-regulars): the scope and period above the entries, each with its rank, diver, visits and
 * the date they reached that count; the viewer's own place below them when it isn't among those loaded. Tapping an entry opens the profile.
 * What it offers and says is `DiveSiteRegularsTexts`' and the state's.
 */
@CircuitInject(DiveSiteRegularsScreen::class, SessionScope::class)
@Composable
fun DiveSiteRegularsUi(state: DiveSiteRegularsState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState, state.entries.canLoadMore) { state.eventSink(DiveSiteRegularsEvent.LoadMore) }
    ListScreen(
        title = stringResource(Res.string.dive_site_regulars_title, state.name),
        onBack = { state.eventSink(DiveSiteRegularsEvent.Back) },
        modifier = modifier,
        listState = listState,
        verticalPadding = 12.dp,
    ) {
        item(key = "filters") { Filters(state) }
        rankBoardRows(
            state.entries,
            state.viewerEntry,
            UiError.Kind.LoadRegulars,
            onRetry = { state.eventSink(DiveSiteRegularsEvent.Retry) },
            emptyText = Res.string.dive_site_regulars_empty,
            key = { it.athlete.did.value },
        ) { entry ->
            RegularRow(state, entry, onClick = { state.eventSink(DiveSiteRegularsEvent.OpenAthlete(entry.athlete.did)) })
        }
    }
}

@Composable
private fun Filters(state: DiveSiteRegularsState) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoardSegments(state.scopeChoice, onSelect = { it?.let { scope -> state.eventSink(DiveSiteRegularsEvent.ScopeChanged(scope)) } })
        BoardChoiceChip(
            state.periodChoice,
            onSelect = { it?.let { period -> state.eventSink(DiveSiteRegularsEvent.PeriodChanged(period)) } },
        )
    }
}

/** One diver's place: rank, avatar and name, the day they reached their count, and the count. */
@Composable
private fun RegularRow(state: DiveSiteRegularsState, entry: DiveSiteRegularEntry, onClick: () -> Unit) {
    RankRow(
        RankBoardTexts.rank(entry.rank).string(),
        accented = state.accents(entry),
        entry.athlete,
        detail = DiveSiteRegularsTexts.reached(entry).string(),
        value = DiveSiteRegularsTexts.visits(entry).string(),
        onClick = onClick,
    )
}
