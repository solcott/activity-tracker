package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.gear.GearListScreen
import io.github.solcott.activitytracker.app.profile.BlockListKind
import io.github.solcott.activitytracker.app.profile.BlockListScreen
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.UnitSystemPicker
import io.github.solcott.activitytracker.app.ui.adaptive.AppListRow
import io.github.solcott.activitytracker.app.ui.adaptive.AppListSection
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.settings_activity_defaults
import io.github.solcott.activitytracker.composeApp.resources.settings_blocked_accounts
import io.github.solcott.activitytracker.composeApp.resources.settings_gear
import io.github.solcott.activitytracker.composeApp.resources.settings_moderation
import io.github.solcott.activitytracker.composeApp.resources.settings_muted_accounts
import io.github.solcott.activitytracker.composeApp.resources.settings_privacy_zones
import io.github.solcott.activitytracker.composeApp.resources.settings_section_account
import io.github.solcott.activitytracker.composeApp.resources.settings_section_general
import io.github.solcott.activitytracker.composeApp.resources.settings_section_privacy
import io.github.solcott.activitytracker.composeApp.resources.settings_section_safety
import io.github.solcott.activitytracker.composeApp.resources.settings_section_training
import io.github.solcott.activitytracker.composeApp.resources.settings_segments_and_groups
import io.github.solcott.activitytracker.composeApp.resources.settings_title
import io.github.solcott.activitytracker.composeApp.resources.settings_units_heading
import io.github.solcott.activitytracker.composeApp.resources.settings_zones
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.stringResource

/**
 * Settings' list (ui/settings-layout): units chosen in place, then a row per page, grouped, each summarizing its page where it can. On a
 * wide window the shell shows the chosen page beside it, and the list marks its row.
 */
@CircuitInject(SettingsScreen::class, SessionScope::class)
@Composable
fun SettingsUi(state: SettingsState, modifier: Modifier = Modifier) {
    val pane = LocalSettingsPane.current
    fun row(page: SettingsPage) = pane.marks(page)
    val open = { page: SettingsPage -> state.eventSink(SettingsEvent.Open(page)) }
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
        ReadableScrollColumn(padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppListSection(stringResource(Res.string.settings_section_account)) {
                AppListRow(
                    state.handle,
                    summary = SettingsTexts.accountSummary(state)?.string(),
                    onClick = { open(AccountScreen) },
                    selected = row(AccountScreen),
                )
            }
            AppListSection(stringResource(Res.string.settings_section_general)) { Units(state) }
            AppListSection(stringResource(Res.string.settings_section_privacy)) {
                AppListRow(
                    stringResource(Res.string.settings_activity_defaults),
                    summary = SettingsTexts.defaultVisibility(state.defaultActivityVisibility)?.string(),
                    onClick = { open(ActivityDefaultsScreen) },
                    selected = row(ActivityDefaultsScreen),
                )
                AppListRow(
                    stringResource(Res.string.settings_privacy_zones),
                    summary = SettingsTexts.privacyZones(state.privacyZoneCount, state.hidesRouteEnds)?.string(),
                    onClick = { open(PrivacyZonesScreen) },
                    selected = row(PrivacyZonesScreen),
                )
                AppListRow(
                    stringResource(Res.string.settings_segments_and_groups),
                    summary = SettingsTexts.segmentMatching(state.segmentMatching)?.string(),
                    onClick = { open(SegmentsAndGroupsScreen) },
                    selected = row(SegmentsAndGroupsScreen),
                )
            }
            AppListSection(stringResource(Res.string.settings_section_training)) {
                AppListRow(
                    stringResource(Res.string.settings_zones),
                    onClick = { open(TrainingZonesScreen) },
                    selected = row(TrainingZonesScreen),
                )
                AppListRow(stringResource(Res.string.settings_gear), onClick = { open(GearListScreen) }, selected = row(GearListScreen))
            }
            AppListSection(stringResource(Res.string.settings_section_safety)) {
                val blocked = BlockListScreen(BlockListKind.Blocked)
                val muted = BlockListScreen(BlockListKind.Muted)
                AppListRow(stringResource(Res.string.settings_blocked_accounts), onClick = { open(blocked) }, selected = row(blocked))
                AppListRow(stringResource(Res.string.settings_muted_accounts), onClick = { open(muted) }, selected = row(muted))
            }
            // Moderators review on desktop and web, where the labeler's queue is; it opens full width.
            if (state.canModerate && !LocalUiPlatform.current.isMobile) {
                AppListSection(null) {
                    AppListRow(stringResource(Res.string.settings_moderation), onClick = { state.eventSink(SettingsEvent.OpenModeration) })
                }
            }
        }
    }
}

/** The units choice, saved as soon as it changes, with what it means under it. */
@Composable
private fun Units(state: SettingsState, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.settings_units_heading), style = MaterialTheme.typography.bodyLarge)
        UnitSystemPicker(state.units, { state.eventSink(SettingsEvent.UnitsChanged(it)) }, Modifier.fillMaxWidth(), enabled = !state.saving)
        Text(SettingsTexts.unitsNote(state.units, state.saving).string(), style = MaterialTheme.typography.bodySmall)
        state.error?.let { ErrorText(it) }
    }
}
