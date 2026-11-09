package io.github.solcott.activitytracker.app.divesite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.segment.effortDateLabel
import io.github.solcott.activitytracker.app.ui.ChoiceChip
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.RankRow
import io.github.solcott.activitytracker.app.ui.ScopeControl
import io.github.solcott.activitytracker.app.ui.rankBoardRows
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.dive_site_regulars_empty
import io.github.solcott.activitytracker.composeApp.resources.dive_site_regulars_title
import io.github.solcott.activitytracker.composeApp.resources.dive_site_visits
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_period_all_time
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_period_year
import io.github.solcott.activitytracker.composeApp.resources.mayor_period_last_year
import io.github.solcott.activitytracker.composeApp.resources.mayors_reached
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRegularEntry
import io.github.solcott.activitytracker.lexicon.app.RegularsPeriod
import kotlinx.collections.immutable.toImmutableMap
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A dive site's Regulars board (dive-sites/site-regulars): the scope and period above the entries, each with its rank, diver, visits and
 * the date they reached that count; the viewer's own place below them when it isn't among those loaded. Tapping an entry opens the profile.
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
            RegularRow(entry, onClick = { state.eventSink(DiveSiteRegularsEvent.OpenAthlete(entry.athlete.did)) })
        }
    }
}

@Composable
private fun Filters(state: DiveSiteRegularsState) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScopeControl(state.scope, onSelect = { state.eventSink(DiveSiteRegularsEvent.ScopeChanged(it)) })
        ChoiceChip(
            state.period,
            RegularsPeriod.all.associateWith { periodLabel(it) }.toImmutableMap(),
            onSelect = { state.eventSink(DiveSiteRegularsEvent.PeriodChanged(it)) },
        )
    }
}

@Composable
private fun periodLabel(period: String): String =
    stringResource(
        when (period) {
            RegularsPeriod.THIS_YEAR -> Res.string.leaderboard_period_year
            RegularsPeriod.LAST_YEAR -> Res.string.mayor_period_last_year
            else -> Res.string.leaderboard_period_all_time
        },
    )

/** One diver's place: rank, avatar and name, the day they reached their count, and the count. */
@Composable
private fun RegularRow(entry: DiveSiteRegularEntry, onClick: () -> Unit) {
    RankRow(
        entry.rank,
        accented = entry.rank == 1,
        entry.athlete,
        detail = stringResource(Res.string.mayors_reached, effortDateLabel(entry.reachedOn)),
        value = pluralStringResource(Res.plurals.dive_site_visits, entry.visits, entry.visits),
        onClick = onClick,
    )
}
