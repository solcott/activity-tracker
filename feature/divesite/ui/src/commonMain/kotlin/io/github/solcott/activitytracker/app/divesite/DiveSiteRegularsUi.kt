package io.github.solcott.activitytracker.app.divesite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.segment.effortDateLabel
import io.github.solcott.activitytracker.app.ui.ChoiceChip
import io.github.solcott.activitytracker.app.ui.EmptyListText
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.RankRow
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.ownPlace
import io.github.solcott.activitytracker.app.ui.pagedFooter
import io.github.solcott.activitytracker.app.ui.pagedRefreshStatus
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.dive_site_regulars_empty
import io.github.solcott.activitytracker.composeApp.resources.dive_site_regulars_title
import io.github.solcott.activitytracker.composeApp.resources.dive_site_visits
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_period_all_time
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_period_year
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_scope_everyone
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_scope_following
import io.github.solcott.activitytracker.composeApp.resources.mayor_period_last_year
import io.github.solcott.activitytracker.composeApp.resources.mayors_reached
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRegularEntry
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.RegularsPeriod
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val SCOPES = persistentListOf(LeaderboardScope.EVERYONE, LeaderboardScope.FOLLOWING)

/**
 * A dive site's Regulars board (dive-sites/site-regulars): the scope and period above the entries, each with its rank, diver, visits and
 * the date they reached that count; the viewer's own place below them when it isn't among those loaded. Tapping an entry opens the profile.
 */
@CircuitInject(DiveSiteRegularsScreen::class, SessionScope::class)
@Composable
fun DiveSiteRegularsUi(state: DiveSiteRegularsState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState, state.entries.canLoadMore) { state.eventSink(DiveSiteRegularsEvent.LoadMore) }
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.dive_site_regulars_title, state.name),
                onBack = { state.eventSink(DiveSiteRegularsEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding + PaddingValues(vertical = 12.dp)) {
                item(key = "filters") { Filters(state) }
                pagedRefreshStatus(state.entries, UiError.Kind.LoadRegulars) { state.eventSink(DiveSiteRegularsEvent.Retry) }
                items(state.entries.items, key = { it.athlete.did.value }) { entry ->
                    RegularRow(entry, onClick = { state.eventSink(DiveSiteRegularsEvent.OpenAthlete(entry.athlete.did)) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                footer(state)
            }
        }
    }
}

@Composable
private fun Filters(state: DiveSiteRegularsState) {
    val scopeLabels =
        persistentListOf(stringResource(Res.string.leaderboard_scope_everyone), stringResource(Res.string.leaderboard_scope_following))
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSegmentedControl(
            scopeLabels,
            selected = SCOPES.indexOf(state.scope).coerceAtLeast(0),
            onSelect = { state.eventSink(DiveSiteRegularsEvent.ScopeChanged(SCOPES[it])) },
            modifier = Modifier.fillMaxWidth(),
        )
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
        }
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

/** The viewer's place when it's off the list, then the next page's spinner or failure, or the empty state, below the entries. */
private fun LazyListScope.footer(state: DiveSiteRegularsState) {
    ownPlace(state.viewerEntry) { own ->
        RegularRow(own, onClick = { state.eventSink(DiveSiteRegularsEvent.OpenAthlete(own.athlete.did)) })
    }
    pagedFooter(state.entries, UiError.Kind.LoadRegulars, onRetry = { state.eventSink(DiveSiteRegularsEvent.Retry) }) {
        EmptyListText(stringResource(Res.string.dive_site_regulars_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
