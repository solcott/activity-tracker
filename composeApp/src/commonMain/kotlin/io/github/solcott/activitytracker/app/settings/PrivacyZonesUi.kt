package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.record.rememberLocationPermissionRequest
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.MenuField
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ScrollbarBox
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_retry
import io.github.solcott.activitytracker.composeApp.resources.privacy_zones_add_here
import io.github.solcott.activitytracker.composeApp.resources.privacy_zones_empty
import io.github.solcott.activitytracker.composeApp.resources.privacy_zones_from_activity
import io.github.solcott.activitytracker.composeApp.resources.privacy_zones_help
import io.github.solcott.activitytracker.composeApp.resources.privacy_zones_hide_ends
import io.github.solcott.activitytracker.composeApp.resources.privacy_zones_hide_ends_help
import io.github.solcott.activitytracker.composeApp.resources.privacy_zones_label
import io.github.solcott.activitytracker.composeApp.resources.privacy_zones_locating
import io.github.solcott.activitytracker.composeApp.resources.privacy_zones_off
import io.github.solcott.activitytracker.composeApp.resources.privacy_zones_others_note
import io.github.solcott.activitytracker.composeApp.resources.privacy_zones_radius
import io.github.solcott.activitytracker.composeApp.resources.privacy_zones_remove
import io.github.solcott.activitytracker.composeApp.resources.privacy_zones_unnamed
import io.github.solcott.activitytracker.composeApp.resources.settings_privacy_zones
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(PrivacyZonesScreen::class, SessionScope::class)
@Composable
fun PrivacyZonesUi(state: PrivacyZonesState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    val requestPermission = rememberLocationPermissionRequest { state.eventSink(PrivacyZonesEvent.PermissionResult(it)) }
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.settings_privacy_zones),
                onBack = { state.eventSink(PrivacyZonesEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val scroll = rememberScrollState()
        ScrollbarBox(scroll, contentPadding = padding) {
            Column(
                Modifier.verticalScroll(scroll).padding(padding).readableWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(Res.string.privacy_zones_help), style = MaterialTheme.typography.bodyMedium)
                state.error?.let { error ->
                    Text(errorText(error), color = MaterialTheme.colorScheme.error)
                    if (state.zones == null) {
                        AppTextButton(onClick = { state.eventSink(PrivacyZonesEvent.Retry) }) {
                            Text(stringResource(Res.string.action_retry))
                        }
                    }
                }
                val zones = state.zones ?: return@Column
                val enabled = state.canEdit && !state.saving
                if (zones.isEmpty()) Text(stringResource(Res.string.privacy_zones_empty), style = MaterialTheme.typography.bodyMedium)
                zones.forEachIndexed { index, zone -> ZoneRow(index, zone, state.choices, enabled, state.eventSink) }
                Text(stringResource(Res.string.privacy_zones_from_activity), style = MaterialTheme.typography.bodySmall)
                if (state.canUseLocation) {
                    AppTextField(
                        state.label,
                        Modifier.fillMaxWidth(),
                        label = stringResource(Res.string.privacy_zones_label),
                        enabled = enabled && !state.locating,
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    AppSecondaryButton(
                        onClick = {
                            if (state.hasLocationPermission) state.eventSink(PrivacyZonesEvent.AddAtCurrentLocation)
                            else requestPermission()
                        },
                        enabled = enabled && !state.locating,
                    ) {
                        Text(stringResource(if (state.locating) Res.string.privacy_zones_locating else Res.string.privacy_zones_add_here))
                    }
                }
                HorizontalDivider()
                Text(stringResource(Res.string.privacy_zones_hide_ends), style = MaterialTheme.typography.titleMedium)
                DistanceField(
                    selected = state.hideEndsMeters,
                    choices = (listOf(0) + state.choices).toImmutableList(),
                    label = stringResource(Res.string.privacy_zones_hide_ends),
                    enabled = enabled,
                    onSelect = { state.eventSink(PrivacyZonesEvent.HideEndsChanged(it)) },
                )
                Text(stringResource(Res.string.privacy_zones_hide_ends_help), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text(
                    stringResource(Res.string.privacy_zones_others_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One zone: its label and a radius picker, with a remove button. Its center isn't shown: there's no map yet. */
@Composable
private fun ZoneRow(
    index: Int,
    zone: PrivacyZone,
    choices: ImmutableList<Int>,
    enabled: Boolean,
    eventSink: (PrivacyZonesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(zone.label ?: stringResource(Res.string.privacy_zones_unnamed, index + 1), style = MaterialTheme.typography.titleSmall)
            DistanceField(
                selected = zone.radiusMeters,
                choices = choices,
                label = stringResource(Res.string.privacy_zones_radius),
                enabled = enabled,
                onSelect = { eventSink(PrivacyZonesEvent.RadiusChanged(index, it)) },
            )
        }
        AppIconButton(
            AppIcon.Delete,
            contentDescription = stringResource(Res.string.privacy_zones_remove),
            onClick = { eventSink(PrivacyZonesEvent.Remove(index)) },
            enabled = enabled,
        )
    }
}

/** A dropdown of distances in meters, shown in the athlete's units; 0 reads as Off. */
@Composable
private fun DistanceField(
    selected: Int,
    choices: ImmutableList<Int>,
    label: String,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val off = stringResource(Res.string.privacy_zones_off)
    val labels = choices.map { meters -> meters to if (meters == 0) off else Format.distance(meters.toDouble()) }
    MenuField(
        shown = labels.firstOrNull { it.first == selected }?.second.orEmpty(),
        label = label,
        items = labels.map { (meters, text) -> AppMenuItem(text, checked = meters == selected) { onSelect(meters) } }.toImmutableList(),
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
    )
}
