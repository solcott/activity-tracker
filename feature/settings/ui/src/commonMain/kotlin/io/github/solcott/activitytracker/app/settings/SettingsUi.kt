package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
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
import io.github.solcott.activitytracker.app.ui.DetailVisibilityPicker
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.UnitSystemPicker
import io.github.solcott.activitytracker.app.ui.VisibilityPicker
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.app.ui.adaptive.AppSwitch
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.lockedVisibilityNote
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.app.visibility.VisibilityEvent
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.profile_sign_out
import io.github.solcott.activitytracker.composeApp.resources.settings_blocked_accounts
import io.github.solcott.activitytracker.composeApp.resources.settings_defaults_activity
import io.github.solcott.activitytracker.composeApp.resources.settings_defaults_heading
import io.github.solcott.activitytracker.composeApp.resources.settings_defaults_help
import io.github.solcott.activitytracker.composeApp.resources.settings_gear
import io.github.solcott.activitytracker.composeApp.resources.settings_groups_anyone
import io.github.solcott.activitytracker.composeApp.resources.settings_groups_connected
import io.github.solcott.activitytracker.composeApp.resources.settings_groups_heading
import io.github.solcott.activitytracker.composeApp.resources.settings_groups_help
import io.github.solcott.activitytracker.composeApp.resources.settings_groups_off
import io.github.solcott.activitytracker.composeApp.resources.settings_groups_sign_in_again
import io.github.solcott.activitytracker.composeApp.resources.settings_moderation
import io.github.solcott.activitytracker.composeApp.resources.settings_muted_accounts
import io.github.solcott.activitytracker.composeApp.resources.settings_privacy_zones
import io.github.solcott.activitytracker.composeApp.resources.settings_privacy_zones_count
import io.github.solcott.activitytracker.composeApp.resources.settings_privacy_zones_ends
import io.github.solcott.activitytracker.composeApp.resources.settings_privacy_zones_none
import io.github.solcott.activitytracker.composeApp.resources.settings_saving
import io.github.solcott.activitytracker.composeApp.resources.settings_segments_brackets
import io.github.solcott.activitytracker.composeApp.resources.settings_segments_brackets_help
import io.github.solcott.activitytracker.composeApp.resources.settings_segments_heading
import io.github.solcott.activitytracker.composeApp.resources.settings_segments_help
import io.github.solcott.activitytracker.composeApp.resources.settings_segments_leaderboards
import io.github.solcott.activitytracker.composeApp.resources.settings_segments_matching
import io.github.solcott.activitytracker.composeApp.resources.settings_segments_sign_in_again
import io.github.solcott.activitytracker.composeApp.resources.settings_title
import io.github.solcott.activitytracker.composeApp.resources.settings_units_heading
import io.github.solcott.activitytracker.composeApp.resources.settings_units_imperial_example
import io.github.solcott.activitytracker.composeApp.resources.settings_units_metric_example
import io.github.solcott.activitytracker.composeApp.resources.settings_zones
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.GroupChoice
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(SettingsScreen::class, SessionScope::class)
@Composable
fun SettingsUi(state: SettingsState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.settings_title),
                onBack = { state.eventSink(SettingsEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ReadableScrollColumn(padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.settings_units_heading), style = MaterialTheme.typography.titleMedium)
            UnitSystemPicker(
                state.units,
                { state.eventSink(SettingsEvent.UnitsChanged(it)) },
                Modifier.fillMaxWidth(),
                enabled = !state.saving,
            )
            Text(
                stringResource(
                    if (state.saving) {
                        Res.string.settings_saving
                    } else {
                        when (state.units) {
                            UnitSystem.Metric -> Res.string.settings_units_metric_example
                            UnitSystem.Imperial -> Res.string.settings_units_imperial_example
                        }
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
            state.defaults?.let { defaults -> DefaultVisibility(defaults, state.savingDefaults) }
            state.error?.let { ErrorText(it) }
            HorizontalDivider()
            AppSecondaryButton(onClick = { state.eventSink(SettingsEvent.OpenZones) }) { Text(stringResource(Res.string.settings_zones)) }
            AppSecondaryButton(onClick = { state.eventSink(SettingsEvent.OpenPrivacyZones) }) {
                Text(stringResource(Res.string.settings_privacy_zones))
            }
            state.privacyZoneCount?.let { count ->
                val zones =
                    if (count == 0) stringResource(Res.string.settings_privacy_zones_none)
                    else pluralStringResource(Res.plurals.settings_privacy_zones_count, count, count)
                Text(
                    if (state.hidesRouteEnds) stringResource(Res.string.settings_privacy_zones_ends, zones) else zones,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider()
            SegmentMatching(state)
            HorizontalDivider()
            GroupActivities(state)
            HorizontalDivider()
            AppSecondaryButton(onClick = { state.eventSink(SettingsEvent.OpenGear) }) { Text(stringResource(Res.string.settings_gear)) }
            AppSecondaryButton(onClick = { state.eventSink(SettingsEvent.OpenBlocked) }) {
                Text(stringResource(Res.string.settings_blocked_accounts))
            }
            AppSecondaryButton(onClick = { state.eventSink(SettingsEvent.OpenMuted) }) {
                Text(stringResource(Res.string.settings_muted_accounts))
            }
            // Moderators review on desktop and web, where the labeler's queue is.
            if (state.canModerate && !LocalUiPlatform.current.isMobile) {
                HorizontalDivider()
                AppSecondaryButton(onClick = { state.eventSink(SettingsEvent.OpenModeration) }) {
                    Text(stringResource(Res.string.settings_moderation))
                }
            }
            HorizontalDivider()
            AppSecondaryButton(onClick = { state.eventSink(SettingsEvent.SignOut) }) { Text(stringResource(Res.string.profile_sign_out)) }
        }
    }
}

/** Who new activities and each detail category start out shown to; the categories' choices are capped at the activity's. */
@Composable
private fun DefaultVisibility(
    defaults: VisibilityFields,
    saving: Boolean,
    modifier: Modifier = Modifier,
) {
    val enabled = defaults.canChoose && !saving
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.settings_defaults_heading), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.settings_defaults_activity), style = MaterialTheme.typography.labelLarge)
        VisibilityPicker(
            defaults.activity,
            onSelect = { defaults.eventSink(VisibilityEvent.ActivityChanged(it)) },
            approvesFollowers = defaults.approvesFollowers,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
        DetailVisibilityPicker(
            defaults,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
        Text(
            stringResource(
                when {
                    saving -> Res.string.settings_saving
                    !defaults.canChoose -> lockedVisibilityNote(defaults)
                    else -> Res.string.settings_defaults_help
                }
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Whether a copy of each route without the privacy zones goes to the AppView, so it can find segments, and while it does whether the
 * athlete shows on segment leaderboards and on age, weight and gender boards.
 */
@Composable
private fun SegmentMatching(state: SettingsState, modifier: Modifier = Modifier) {
    val enabled = state.canUseSegments && state.segmentMatching != null && !state.savingSegments
    val checked = state.segmentMatching == true
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.settings_segments_heading), style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth()
                .toggleable(checked, enabled = enabled, role = Role.Switch) { state.eventSink(SettingsEvent.SegmentMatchingChanged(it)) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.settings_segments_matching), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            AppSwitch(checked, onCheckedChange = null, enabled = enabled)
        }
        val shown = state.showOnLeaderboards
        if (checked && shown != null) {
            val leaderboardsEnabled = enabled && !state.savingLeaderboards
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(shown, enabled = leaderboardsEnabled, role = Role.Switch) {
                        state.eventSink(SettingsEvent.LeaderboardsChanged(it))
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
        if (checked && state.canUseBracketBoards && brackets != null) {
            val bracketsEnabled = enabled && !state.savingBracketBoards
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(brackets, enabled = bracketsEnabled, role = Role.Switch) {
                        state.eventSink(SettingsEvent.BracketBoardsChanged(it))
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
        Text(
            stringResource(
                when {
                    !state.canUseSegments -> Res.string.settings_segments_sign_in_again
                    state.savingSegments || state.savingLeaderboards || state.savingBracketBoards -> Res.string.settings_saving
                    else -> Res.string.settings_segments_help
                }
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Whose activities the athlete's may be grouped with (feed/group-activities): off, people they follow or who follow them, or anyone. */
@Composable
private fun GroupActivities(state: SettingsState, modifier: Modifier = Modifier) {
    val choices = GROUP_CHOICES
    val selected = choices.indexOf(state.groupActivities.takeUnless { it == GroupChoice.NEVER_ASKED } ?: GroupChoice.OFF)
    val labels =
        persistentListOf(
            stringResource(Res.string.settings_groups_off),
            stringResource(Res.string.settings_groups_connected),
            stringResource(Res.string.settings_groups_anyone),
        )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.settings_groups_heading), style = MaterialTheme.typography.titleMedium)
        AppSegmentedControl(
            labels,
            selected,
            onSelect = { state.eventSink(SettingsEvent.GroupActivitiesChanged(choices[it])) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canUseGroups && state.groupActivities != null && !state.savingGroups,
        )
        Text(
            stringResource(
                when {
                    !state.canUseGroups -> Res.string.settings_groups_sign_in_again
                    state.savingGroups -> Res.string.settings_saving
                    else -> Res.string.settings_groups_help
                }
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private val GROUP_CHOICES = listOf(GroupChoice.OFF, GroupChoice.CONNECTED, GroupChoice.ANYONE)
