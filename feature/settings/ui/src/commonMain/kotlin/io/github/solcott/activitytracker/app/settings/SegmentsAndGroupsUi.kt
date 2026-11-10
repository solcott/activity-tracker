package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.app.ui.adaptive.AppSwitch
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.settings_groups_heading
import io.github.solcott.activitytracker.composeApp.resources.settings_segments_and_groups
import io.github.solcott.activitytracker.composeApp.resources.settings_segments_brackets
import io.github.solcott.activitytracker.composeApp.resources.settings_segments_brackets_help
import io.github.solcott.activitytracker.composeApp.resources.settings_segments_heading
import io.github.solcott.activitytracker.composeApp.resources.settings_segments_leaderboards
import io.github.solcott.activitytracker.composeApp.resources.settings_segments_matching
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/** Segment matching and its leaderboard choices, then activity grouping, each with what it shares and why. */
@CircuitInject(SegmentsAndGroupsScreen::class, SessionScope::class)
@Composable
fun SegmentsAndGroupsUi(state: SegmentsAndGroupsState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.settings_segments_and_groups),
                onBack = { state.eventSink(SegmentsAndGroupsEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ReadableScrollColumn(padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SegmentMatching(state)
            HorizontalDivider()
            GroupActivities(state)
            state.error?.let { ErrorText(it) }
        }
    }
}

/**
 * Whether a copy of each route without the privacy zones goes to the AppView, so it can find segments, and while it does whether the
 * athlete shows on segment leaderboards and on age, weight and gender boards.
 */
@Composable
private fun SegmentMatching(state: SegmentsAndGroupsState, modifier: Modifier = Modifier) {
    val enabled = state.canChangeMatching
    val checked = state.segmentMatching == true
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.settings_segments_heading), style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth()
                .toggleable(checked, enabled = enabled, role = Role.Switch) {
                    state.eventSink(SegmentsAndGroupsEvent.SegmentMatchingChanged(it))
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.settings_segments_matching), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            AppSwitch(checked, onCheckedChange = null, enabled = enabled)
        }
        val shown = state.showOnLeaderboards
        if (state.offersLeaderboards && shown != null) {
            val leaderboardsEnabled = state.canChangeLeaderboards
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(shown, enabled = leaderboardsEnabled, role = Role.Switch) {
                        state.eventSink(SegmentsAndGroupsEvent.LeaderboardsChanged(it))
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.settings_segments_leaderboards),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                AppSwitch(shown, onCheckedChange = null, enabled = leaderboardsEnabled)
            }
        }
        val brackets = state.bracketBoards
        if (state.offersBracketBoards && brackets != null) {
            val bracketsEnabled = state.canChangeBracketBoards
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(brackets, enabled = bracketsEnabled, role = Role.Switch) {
                        state.eventSink(SegmentsAndGroupsEvent.BracketBoardsChanged(it))
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.settings_segments_brackets), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(Res.string.settings_segments_brackets_help), style = MaterialTheme.typography.bodySmall)
                }
                AppSwitch(brackets, onCheckedChange = null, enabled = bracketsEnabled)
            }
        }
        Text(SettingsTexts.segmentsNote(state).string(), style = MaterialTheme.typography.bodySmall)
    }
}

/** Whose activities the athlete's may be grouped with (feed/group-activities): off, people they follow or who follow them, or anyone. */
@Composable
private fun GroupActivities(state: SegmentsAndGroupsState, modifier: Modifier = Modifier) {
    val options = state.groupOptions
    val selected = options.indexOfFirst { it.choice == state.shownGroupChoice }
    val labels = options.map { it.label.string() }.toImmutableList()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.settings_groups_heading), style = MaterialTheme.typography.titleMedium)
        AppSegmentedControl(
            labels,
            selected,
            onSelect = { state.eventSink(SegmentsAndGroupsEvent.GroupActivitiesChanged(options[it].choice)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canChangeGroups,
        )
        Text(SettingsTexts.groupsNote(state).string(), style = MaterialTheme.typography.bodySmall)
    }
}
