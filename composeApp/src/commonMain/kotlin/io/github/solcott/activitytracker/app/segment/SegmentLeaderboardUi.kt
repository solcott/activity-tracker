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
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
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
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_age_over
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_age_range
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_age_under
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_ebike
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_empty
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
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_weight_over_kg
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_weight_over_lb
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_weight_range_kg
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_weight_range_lb
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_weight_under_kg
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_weight_under_lb
import io.github.solcott.activitytracker.composeApp.resources.leaderboard_your_place
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.WeightConversions
import io.github.solcott.activitytracker.lexicon.app.LeaderboardAgeGroup
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.LeaderboardGender
import io.github.solcott.activitytracker.lexicon.app.LeaderboardPeriod
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.LeaderboardWeightClass
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import org.jetbrains.compose.resources.stringResource

private val PERIODS = persistentListOf(LeaderboardPeriod.ALL_TIME, LeaderboardPeriod.YEAR, LeaderboardPeriod.MONTH)
private val SCOPES = persistentListOf(LeaderboardScope.EVERYONE, LeaderboardScope.FOLLOWING)

/**
 * A segment's leaderboard: the period and scope, the e-bike board for a ride segment, and the age, weight and gender brackets above the
 * entries, each with its rank, athlete, time and date; the viewer's own place below them when it isn't among those loaded. Tapping an entry
 * opens its activity.
 */
@CircuitInject(SegmentLeaderboardScreen::class, SessionScope::class)
@Composable
fun SegmentLeaderboardUi(state: SegmentLeaderboardState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState, state.entries.canLoadMore) { state.eventSink(SegmentLeaderboardEvent.LoadMore) }

    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(title = state.name, onBack = { state.eventSink(SegmentLeaderboardEvent.Back) }, scrollBehavior = scrollBehavior)
        },
    ) { padding ->
        ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding + PaddingValues(vertical = 12.dp)) {
                item(key = "filters") { Filters(state) }
                pagedRefreshStatus(state.entries, UiError.Kind.LoadLeaderboard) { state.eventSink(SegmentLeaderboardEvent.Retry) }
                items(state.entries.items, key = { it.athlete.did.value }) { entry ->
                    EntryRow(entry, onClick = { state.eventSink(SegmentLeaderboardEvent.OpenActivity(entry.effort.activity)) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                footer(state)
            }
        }
    }
}

@Composable
private fun Filters(state: SegmentLeaderboardState) {
    val periodLabels =
        persistentListOf(
            stringResource(Res.string.leaderboard_period_all_time),
            stringResource(Res.string.leaderboard_period_year),
            stringResource(Res.string.leaderboard_period_month),
        )
    val scopeLabels =
        persistentListOf(stringResource(Res.string.leaderboard_scope_everyone), stringResource(Res.string.leaderboard_scope_following))
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSegmentedControl(
            periodLabels,
            selected = PERIODS.indexOf(state.period).coerceAtLeast(0),
            onSelect = { state.eventSink(SegmentLeaderboardEvent.PeriodChanged(PERIODS[it])) },
            modifier = Modifier.fillMaxWidth(),
        )
        AppSegmentedControl(
            scopeLabels,
            selected = SCOPES.indexOf(state.scope).coerceAtLeast(0),
            onSelect = { state.eventSink(SegmentLeaderboardEvent.ScopeChanged(SCOPES[it])) },
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.canChooseEBike) {
                AppFilterChip(
                    selected = state.eBike,
                    onClick = { state.eventSink(SegmentLeaderboardEvent.EBikeChanged(!state.eBike)) },
                    label = { Text(stringResource(Res.string.leaderboard_ebike)) },
                )
            }
            BracketChip(
                all = stringResource(Res.string.leaderboard_age_all),
                selected = state.ageGroup,
                labels = LeaderboardAgeGroup.all.associateWith { ageLabel(it) }.toImmutableMap(),
                onSelect = { state.eventSink(SegmentLeaderboardEvent.AgeGroupChanged(it)) },
            )
            BracketChip(
                all = stringResource(Res.string.leaderboard_weight_all),
                selected = state.weightClass,
                labels = LeaderboardWeightClass.all.associateWith { weightLabel(it) }.toImmutableMap(),
                onSelect = { state.eventSink(SegmentLeaderboardEvent.WeightClassChanged(it)) },
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
                onSelect = { state.eventSink(SegmentLeaderboardEvent.GenderChanged(it)) },
            )
        }
    }
}

/**
 * A chip choosing one bracket (segments/leaderboard-brackets) from a menu: [all] when [selected] is null, else its label from [labels],
 * which also lists the menu's choices in order after [all].
 */
@Composable
private fun BracketChip(all: String, selected: String?, labels: ImmutableMap<String, String>, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppFilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(selected?.let { labels[it] } ?: all) },
            trailingIcon = { PlatformIcon(AppIcon.ChevronDown, null, size = 16.dp) },
        )
        AppMenu(
            expanded = open,
            onDismiss = { open = false },
            items =
                (listOf(AppMenuItem(all, checked = selected == null) { onSelect(null) }) +
                        labels.map { (value, label) -> AppMenuItem(label, checked = value == selected) { onSelect(value) } })
                    .toImmutableList(),
        )
    }
}

/** An age group's label: `0-19` is "19 and under", `75+` "75 and over". */
@Composable
private fun ageLabel(group: String): String {
    val (from, to) = boundsOf(group)
    return when {
        to == null -> stringResource(Res.string.leaderboard_age_over, from)
        from == 0 -> stringResource(Res.string.leaderboard_age_under, to)
        else -> stringResource(Res.string.leaderboard_age_range, from, to)
    }
}

/**
 * A weight class's label in the viewer's units. A class is whole kilograms, so in pounds each bound is its kilogram bound converted and
 * rounded, and a class ends a pound before the next one starts.
 */
@Composable
private fun weightLabel(weightClass: String): String {
    val (from, to) = boundsOf(weightClass)
    val imperial = LocalUnitSystem.current == UnitSystem.Imperial
    fun bound(kg: Int): Int = if (imperial) (kg * WeightConversions.GRAMS_PER_KG / WeightConversions.GRAMS_PER_POUND).roundToInt() else kg
    return when {
        to == null ->
            stringResource(if (imperial) Res.string.leaderboard_weight_over_lb else Res.string.leaderboard_weight_over_kg, bound(from))
        from == 0 ->
            stringResource(if (imperial) Res.string.leaderboard_weight_under_lb else Res.string.leaderboard_weight_under_kg, bound(to + 1))
        else ->
            stringResource(
                if (imperial) Res.string.leaderboard_weight_range_lb else Res.string.leaderboard_weight_range_kg,
                bound(from),
                bound(to + 1) - 1,
            )
    }
}

/** A bracket token's bounds: `55-64` is 55 to 64, `75+` is 75 with no upper bound. */
private fun boundsOf(token: String): Pair<Int, Int?> =
    if (token.endsWith("+")) token.dropLast(1).toInt() to null else token.split('-').let { (from, to) -> from.toInt() to to.toInt() }

/** One athlete's place: rank, avatar and name, their time and the day they rode it. */
@Composable
private fun EntryRow(entry: LeaderboardEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            color = if (entry.rank <= PODIUM) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
        )
        Avatar(entry.athlete.avatar, name, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                effortDateLabel(entry.effort.startedOn),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Format.duration(entry.effort.elapsed), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

/** The viewer's place when it's off the list, then the next page's spinner or failure, or the empty state, below the entries. */
private fun LazyListScope.footer(state: SegmentLeaderboardState) {
    state.viewerEntry?.let { own ->
        item(key = "own-place") {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(stringResource(Res.string.leaderboard_your_place), style = MaterialTheme.typography.titleSmall)
                EntryRow(own, onClick = { state.eventSink(SegmentLeaderboardEvent.OpenActivity(own.effort.activity)) })
            }
        }
    }
    pagedFooter(state.entries, UiError.Kind.LoadLeaderboard, onRetry = { state.eventSink(SegmentLeaderboardEvent.Retry) }) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.leaderboard_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The ranks drawn in the accent colour. */
private const val PODIUM = 3
