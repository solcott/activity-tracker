package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.Avatar
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChip
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.pagedFooter
import io.github.solcott.activitytracker.app.ui.pagedRefreshStatus
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_age_all
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_ebike
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_gender_all
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_gender_female
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_gender_male
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_gender_non_binary
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_period_all_time
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_period_month
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_period_year
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_scope_everyone
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_scope_following
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_weight_all
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_your_place
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
import io.github.solcott.activitytracker.lexicon.app.LeaderboardAgeGroup
import io.github.solcott.activitytracker.lexicon.app.LeaderboardGender
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.LeaderboardWeightClass
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.MayorPeriod
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val SCOPES = persistentListOf(LeaderboardScope.EVERYONE, LeaderboardScope.FOLLOWING)

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

    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.mayors_title, state.name),
                onBack = { state.eventSink(SegmentMayorsEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding + PaddingValues(vertical = 12.dp)) {
                item(key = "filters") { Filters(state) }
                pagedRefreshStatus(state.entries, UiError.Kind.LoadMayors) { state.eventSink(SegmentMayorsEvent.Retry) }
                items(state.entries.items, key = { it.athlete.did.value }) { entry ->
                    MayorRow(entry, onClick = { state.eventSink(SegmentMayorsEvent.OpenAthlete(entry.athlete.did)) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                footer(state)
            }
        }
    }
}

/**
 * An active segment's Mayor in brief, on its page (segments/segment-mayors): the leader and their count, or that it has none yet, and the
 * viewer's own count. Tapping it opens the Mayors board.
 */
@Composable
internal fun MayorCard(mayor: MayorSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth().clickable(onClick = onClick).handCursor()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(Res.string.segment_mayor_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
            val leader = mayor.leader
            if (leader == null) {
                Text(
                    stringResource(Res.string.segment_mayor_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val name = leader.athlete.displayName ?: leader.athlete.handle.value
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(leader.athlete.avatar, name, 36.dp)
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        pluralStringResource(Res.plurals.mayor_efforts, leader.efforts, leader.efforts),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            mayor.viewer?.let { own ->
                Text(
                    pluralStringResource(Res.plurals.segment_mayor_yours, own.efforts, own.efforts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Filters(state: SegmentMayorsState) {
    val scopeLabels =
        persistentListOf(stringResource(Res.string.leaderboard_scope_everyone), stringResource(Res.string.leaderboard_scope_following))
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSegmentedControl(
            scopeLabels,
            selected = SCOPES.indexOf(state.scope).coerceAtLeast(0),
            onSelect = { state.eventSink(SegmentMayorsEvent.ScopeChanged(SCOPES[it])) },
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodChip(state.period) { state.eventSink(SegmentMayorsEvent.PeriodChanged(it)) }
            if (state.canChooseEBike) {
                AppFilterChip(
                    selected = state.eBike,
                    onClick = { state.eventSink(SegmentMayorsEvent.EBikeChanged(!state.eBike)) },
                    label = { Text(stringResource(Res.string.leaderboard_ebike)) },
                )
            }
            BracketChip(
                all = stringResource(Res.string.leaderboard_age_all),
                selected = state.ageGroup,
                labels = LeaderboardAgeGroup.all.associateWith { ageLabel(it) }.toImmutableMap(),
                onSelect = { state.eventSink(SegmentMayorsEvent.AgeGroupChanged(it)) },
            )
            BracketChip(
                all = stringResource(Res.string.leaderboard_weight_all),
                selected = state.weightClass,
                labels = LeaderboardWeightClass.all.associateWith { weightLabel(it) }.toImmutableMap(),
                onSelect = { state.eventSink(SegmentMayorsEvent.WeightClassChanged(it)) },
            )
            BracketChip(
                all = stringResource(Res.string.leaderboard_gender_all),
                selected = state.gender,
                labels =
                    persistentMapOf(
                        LeaderboardGender.FEMALE to stringResource(Res.string.leaderboard_gender_female),
                        LeaderboardGender.MALE to stringResource(Res.string.leaderboard_gender_male),
                        LeaderboardGender.NON_BINARY to stringResource(Res.string.leaderboard_gender_non_binary),
                    ),
                onSelect = { state.eventSink(SegmentMayorsEvent.GenderChanged(it)) },
            )
        }
    }
}

/** A chip choosing the board's period from a menu of every [MayorPeriod], always selected since one always applies. */
@Composable
private fun PeriodChip(period: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val labels = MayorPeriod.all.associateWith { periodLabel(it) }
    Box {
        AppFilterChip(
            selected = true,
            onClick = { open = true },
            label = { Text(labels[period] ?: period) },
            trailingIcon = { PlatformIcon(AppIcon.ChevronDown, null, size = 16.dp) },
        )
        AppMenu(
            expanded = open,
            onDismiss = { open = false },
            items = labels.map { (value, label) -> AppMenuItem(label, checked = value == period) { onSelect(value) } }.toImmutableList(),
        )
    }
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
private fun MayorRow(entry: MayorEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = entry.athlete.displayName ?: entry.athlete.handle.value
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).handCursor().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.rank.toString(),
            Modifier.width(36.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (entry.rank == 1) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
        )
        Avatar(entry.athlete.avatar, name, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Res.string.mayors_reached, effortDateLabel(entry.reachedOn)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            pluralStringResource(Res.plurals.mayor_efforts, entry.efforts, entry.efforts),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** The viewer's place when it's off the list, then the next page's spinner or failure, or the empty state, below the entries. */
private fun LazyListScope.footer(state: SegmentMayorsState) {
    state.viewerEntry?.let { own ->
        item(key = "own-place") {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(stringResource(Res.string.leaderboard_your_place), style = MaterialTheme.typography.titleSmall)
                MayorRow(own, onClick = { state.eventSink(SegmentMayorsEvent.OpenAthlete(own.athlete.did)) })
            }
        }
    }
    pagedFooter(state.entries, UiError.Kind.LoadMayors, onRetry = { state.eventSink(SegmentMayorsEvent.Retry) }) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.mayors_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
