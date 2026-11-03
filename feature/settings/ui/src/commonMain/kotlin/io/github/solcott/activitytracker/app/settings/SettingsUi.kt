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
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.app.ui.visibilityLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.profile_sign_out
import io.github.solcott.activitytracker.composeApp.resources.settings_activity_defaults
import io.github.solcott.activitytracker.composeApp.resources.settings_blocked_accounts
import io.github.solcott.activitytracker.composeApp.resources.settings_gear
import io.github.solcott.activitytracker.composeApp.resources.settings_moderation
import io.github.solcott.activitytracker.composeApp.resources.settings_muted_accounts
import io.github.solcott.activitytracker.composeApp.resources.settings_privacy_zones
import io.github.solcott.activitytracker.composeApp.resources.settings_privacy_zones_count
import io.github.solcott.activitytracker.composeApp.resources.settings_privacy_zones_ends
import io.github.solcott.activitytracker.composeApp.resources.settings_privacy_zones_none
import io.github.solcott.activitytracker.composeApp.resources.settings_saving
import io.github.solcott.activitytracker.composeApp.resources.settings_section_general
import io.github.solcott.activitytracker.composeApp.resources.settings_section_privacy
import io.github.solcott.activitytracker.composeApp.resources.settings_section_safety
import io.github.solcott.activitytracker.composeApp.resources.settings_section_training
import io.github.solcott.activitytracker.composeApp.resources.settings_segments_and_groups
import io.github.solcott.activitytracker.composeApp.resources.settings_segments_off
import io.github.solcott.activitytracker.composeApp.resources.settings_segments_on
import io.github.solcott.activitytracker.composeApp.resources.settings_title
import io.github.solcott.activitytracker.composeApp.resources.settings_units_heading
import io.github.solcott.activitytracker.composeApp.resources.settings_units_imperial_example
import io.github.solcott.activitytracker.composeApp.resources.settings_units_metric_example
import io.github.solcott.activitytracker.composeApp.resources.settings_zones
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitSystem
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings' list (ui/settings-layout): units chosen in place, then a row per page, grouped, each summarizing its page where it can. On a
 * wide window the shell shows the chosen page beside it, and the list marks its row.
 */
@CircuitInject(SettingsScreen::class, SessionScope::class)
@Composable
fun SettingsUi(state: SettingsState, modifier: Modifier = Modifier) {
    val pane = LocalSettingsPane.current
    val shown = pane.shownPage.takeIf { pane.twoPane }
    fun row(page: SettingsPage) = shown == page
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
            AppListSection(stringResource(Res.string.settings_section_general)) { Units(state) }
            AppListSection(stringResource(Res.string.settings_section_privacy)) {
                AppListRow(
                    stringResource(Res.string.settings_activity_defaults),
                    summary = state.defaultActivityVisibility?.let { visibilityLabel(it) },
                    onClick = { open(ActivityDefaultsScreen) },
                    selected = row(ActivityDefaultsScreen),
                )
                AppListRow(
                    stringResource(Res.string.settings_privacy_zones),
                    summary = privacyZonesSummary(state),
                    onClick = { open(PrivacyZonesScreen) },
                    selected = row(PrivacyZonesScreen),
                )
                AppListRow(
                    stringResource(Res.string.settings_segments_and_groups),
                    summary =
                        state.segmentMatching?.let {
                            stringResource(if (it) Res.string.settings_segments_on else Res.string.settings_segments_off)
                        },
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
            AppListSection(null) {
                AppListRow(stringResource(Res.string.profile_sign_out), onClick = { state.eventSink(SettingsEvent.SignOut) })
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
        state.error?.let { ErrorText(it) }
    }
}

/** How many zones the athlete keeps, and whether route ends are hidden; null until read. */
@Composable
private fun privacyZonesSummary(state: SettingsState): String? {
    val count = state.privacyZoneCount ?: return null
    val zones =
        if (count == 0) stringResource(Res.string.settings_privacy_zones_none)
        else pluralStringResource(Res.plurals.settings_privacy_zones_count, count, count)
    return if (state.hidesRouteEnds) stringResource(Res.string.settings_privacy_zones_ends, zones) else zones
}
