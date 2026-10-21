package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.DetailVisibilityPicker
import io.github.solcott.activitytracker.app.ui.UnitSystemPicker
import io.github.solcott.activitytracker.app.ui.VisibilityPicker
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.app.visibility.VisibilityEvent
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.activity_visibility_sign_in_again
import io.github.solcott.activitytracker.composeApp.resources.profile_sign_out
import io.github.solcott.activitytracker.composeApp.resources.settings_blocked_accounts
import io.github.solcott.activitytracker.composeApp.resources.settings_defaults_activity
import io.github.solcott.activitytracker.composeApp.resources.settings_defaults_heading
import io.github.solcott.activitytracker.composeApp.resources.settings_defaults_help
import io.github.solcott.activitytracker.composeApp.resources.settings_moderation
import io.github.solcott.activitytracker.composeApp.resources.settings_muted_accounts
import io.github.solcott.activitytracker.composeApp.resources.settings_privacy_zones
import io.github.solcott.activitytracker.composeApp.resources.settings_privacy_zones_count
import io.github.solcott.activitytracker.composeApp.resources.settings_privacy_zones_ends
import io.github.solcott.activitytracker.composeApp.resources.settings_privacy_zones_none
import io.github.solcott.activitytracker.composeApp.resources.settings_saving
import io.github.solcott.activitytracker.composeApp.resources.settings_title
import io.github.solcott.activitytracker.composeApp.resources.settings_units_heading
import io.github.solcott.activitytracker.composeApp.resources.settings_units_imperial_example
import io.github.solcott.activitytracker.composeApp.resources.settings_units_metric_example
import io.github.solcott.activitytracker.composeApp.resources.settings_zones
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitSystem
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(SettingsScreen::class, SessionScope::class)
@Composable
fun SettingsUi(state: SettingsState, modifier: Modifier = Modifier) {
    AppScaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = stringResource(Res.string.settings_title), onBack = { state.eventSink(SettingsEvent.Back) }) },
    ) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding).readableWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
            state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
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
                    !defaults.canChoose -> Res.string.activity_visibility_sign_in_again
                    else -> Res.string.settings_defaults_help
                }
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
