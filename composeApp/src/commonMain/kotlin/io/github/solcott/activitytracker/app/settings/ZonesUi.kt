package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.MenuField
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSwitch
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ScrollbarBox
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_save
import io.github.solcott.activitytracker.composeApp.resources.action_saving
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.composeApp.resources.zone_name_active_recovery
import io.github.solcott.activitytracker.composeApp.resources.zone_name_aerobic
import io.github.solcott.activitytracker.composeApp.resources.zone_name_aerobic_capacity
import io.github.solcott.activitytracker.composeApp.resources.zone_name_anaerobic_capacity
import io.github.solcott.activitytracker.composeApp.resources.zone_name_endurance
import io.github.solcott.activitytracker.composeApp.resources.zone_name_hard
import io.github.solcott.activitytracker.composeApp.resources.zone_name_light
import io.github.solcott.activitytracker.composeApp.resources.zone_name_maximum
import io.github.solcott.activitytracker.composeApp.resources.zone_name_moderate
import io.github.solcott.activitytracker.composeApp.resources.zone_name_neuromuscular
import io.github.solcott.activitytracker.composeApp.resources.zone_name_recovery
import io.github.solcott.activitytracker.composeApp.resources.zone_name_sub_threshold
import io.github.solcott.activitytracker.composeApp.resources.zone_name_super_threshold
import io.github.solcott.activitytracker.composeApp.resources.zone_name_tempo
import io.github.solcott.activitytracker.composeApp.resources.zone_name_threshold
import io.github.solcott.activitytracker.composeApp.resources.zone_name_very_light
import io.github.solcott.activitytracker.composeApp.resources.zone_name_vo2_max
import io.github.solcott.activitytracker.composeApp.resources.zones_auto_update
import io.github.solcott.activitytracker.composeApp.resources.zones_auto_update_help
import io.github.solcott.activitytracker.composeApp.resources.zones_basis_ftp
import io.github.solcott.activitytracker.composeApp.resources.zones_basis_max_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_basis_reserve
import io.github.solcott.activitytracker.composeApp.resources.zones_basis_threshold_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_basis_threshold_pace
import io.github.solcott.activitytracker.composeApp.resources.zones_default_help
import io.github.solcott.activitytracker.composeApp.resources.zones_default_title
import io.github.solcott.activitytracker.composeApp.resources.zones_ftp
import io.github.solcott.activitytracker.composeApp.resources.zones_heart_rate_heading
import io.github.solcott.activitytracker.composeApp.resources.zones_label
import io.github.solcott.activitytracker.composeApp.resources.zones_max_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_model
import io.github.solcott.activitytracker.composeApp.resources.zones_needs_ftp
import io.github.solcott.activitytracker.composeApp.resources.zones_needs_max_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_needs_reserve
import io.github.solcott.activitytracker.composeApp.resources.zones_needs_threshold_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_needs_threshold_pace
import io.github.solcott.activitytracker.composeApp.resources.zones_power_heading
import io.github.solcott.activitytracker.composeApp.resources.zones_range
import io.github.solcott.activitytracker.composeApp.resources.zones_range_open
import io.github.solcott.activitytracker.composeApp.resources.zones_resting_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_threshold_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_thresholds_heading
import io.github.solcott.activitytracker.composeApp.resources.zones_unit_bpm
import io.github.solcott.activitytracker.composeApp.resources.zones_unit_watts
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.training.ThresholdInput
import io.github.solcott.activitytracker.data.training.Zone
import io.github.solcott.activitytracker.data.training.ZoneBasis
import io.github.solcott.activitytracker.data.training.ZoneModel
import io.github.solcott.activitytracker.data.training.ZoneName
import io.github.solcott.activitytracker.lexicon.app.Threshold
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@CircuitInject(ZonesScreen::class, SessionScope::class)
@Composable
fun ZonesUi(state: ZonesState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.zones_default_title),
                onBack = { state.eventSink(ZonesEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ContentStateLayout(state.content, UiError.Kind.LoadProfile, { state.eventSink(ZonesEvent.Retry) }, contentPadding = padding) {
            val scroll = rememberScrollState()
            ScrollbarBox(scroll, contentPadding = padding) {
                Column(
                    Modifier.verticalScroll(scroll).padding(padding).readableWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(Res.string.zones_default_help), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(Res.string.zones_thresholds_heading), style = MaterialTheme.typography.titleMedium)
                    ThresholdField(state, state.thresholdHeartRate, Threshold.THRESHOLD_HEART_RATE, Res.string.zones_threshold_heart_rate)
                    ThresholdField(state, state.maxHeartRate, Threshold.MAX_HEART_RATE, Res.string.zones_max_heart_rate)
                    ThresholdField(state, state.restingHeartRate, Threshold.RESTING_HEART_RATE, Res.string.zones_resting_heart_rate)
                    ThresholdField(state, state.functionalThresholdPower, Threshold.FUNCTIONAL_THRESHOLD_POWER, Res.string.zones_ftp)
                    Text(stringResource(Res.string.zones_auto_update_help), style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    ZoneSection(
                        heading = stringResource(Res.string.zones_heart_rate_heading),
                        model = state.heartRateModel,
                        models = state.heartRateModels,
                        zones = state.heartRateZones,
                        enabled = state.loaded,
                        onPick = { state.eventSink(ZonesEvent.HeartRateModelChanged(it)) },
                    ) {
                        numberRange(it, stringResource(Res.string.zones_unit_bpm))
                    }
                    HorizontalDivider()
                    ZoneSection(
                        heading = stringResource(Res.string.zones_power_heading),
                        model = state.powerModel,
                        models = state.powerModels,
                        zones = state.powerZones,
                        enabled = state.loaded,
                        onPick = { state.eventSink(ZonesEvent.PowerModelChanged(it)) },
                    ) {
                        numberRange(it, stringResource(Res.string.zones_unit_watts))
                    }
                    state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
                    AppButton(
                        onClick = { state.eventSink(ZonesEvent.Save) },
                        enabled = state.loaded && state.valid && !state.saving,
                    ) {
                        Text(stringResource(if (state.saving) Res.string.action_saving else Res.string.action_save))
                    }
                }
            }
        }
    }
}

/** A threshold's field, and whether imported activities may update it. [threshold] is a [Threshold] value. */
@Composable
private fun ThresholdField(
    state: ZonesState,
    field: TextFieldState,
    threshold: String,
    label: StringResource,
    modifier: Modifier = Modifier,
) {
    val text = field.text.toString()
    val valid =
        if (threshold == Threshold.FUNCTIONAL_THRESHOLD_POWER) ThresholdInput.isValidPower(text) else ThresholdInput.isValidHeartRate(text)
    Column(modifier) {
        AppTextField(
            field,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.loaded,
            label = stringResource(label),
            isError = !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        val checked = threshold in state.autoUpdate
        Row(
            Modifier.fillMaxWidth()
                .toggleable(checked, enabled = state.loaded, role = Role.Switch) {
                    state.eventSink(ZonesEvent.AutoUpdateChanged(threshold, it))
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.zones_auto_update), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            AppSwitch(checked, onCheckedChange = null, enabled = state.loaded)
        }
    }
}

/**
 * A zone model picker, and the zones it gives (read-only), or what's missing to work them out. [range] shows a zone's bounds with their
 * unit.
 */
@Composable
internal fun ZoneSection(
    heading: String,
    model: ZoneModel,
    models: ImmutableList<ZoneModel>,
    zones: ImmutableList<Zone>?,
    enabled: Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    range: @Composable (Zone) -> String,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(heading, style = MaterialTheme.typography.titleMedium)
        val items = models.map { AppMenuItem(zoneModelLabel(it), checked = it == model) { onPick(it.token) } }.toImmutableList()
        MenuField(zoneModelLabel(model), stringResource(Res.string.zones_model), items, Modifier.fillMaxWidth(), enabled = enabled)
        ZoneList(model, zones, range = range)
    }
}

/** What [model] is based on, then its [zones] (read-only), or what's missing to work them out. */
@Composable
internal fun ZoneList(model: ZoneModel, zones: ImmutableList<Zone>?, modifier: Modifier = Modifier, range: @Composable (Zone) -> String) {
    Column(modifier) {
        Text(stringResource(basisLabel(model.basis)), style = MaterialTheme.typography.bodySmall)
        if (zones == null) {
            Text(
                stringResource(missingInput(model.basis)),
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            zones.forEach { ZoneRow(it, range(it)) }
        }
    }
}

/** A zone's bpm or watts: "143–150 bpm", or "180+ bpm" for the last. */
@Composable
internal fun numberRange(zone: Zone, unit: String): String {
    val range =
        zone.high?.let { stringResource(Res.string.zones_range, zone.low, it) } ?: stringResource(Res.string.zones_range_open, zone.low)
    return "$range $unit"
}

@Composable
private fun ZoneRow(zone: Zone, range: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.zones_label, zone.label),
            Modifier.widthIn(min = ZONE_LABEL_WIDTH),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(zoneName(zone.name)), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(range, style = MaterialTheme.typography.bodyMedium)
    }
}

private val ZONE_LABEL_WIDTH = 64.dp

/** A model's name, looked up by token as `zone_model_<token>`. */
@Composable
internal fun zoneModelLabel(model: ZoneModel): String =
    Res.allStringResources["zone_model_${model.token}"]?.let { stringResource(it) } ?: model.token

private fun basisLabel(basis: ZoneBasis): StringResource =
    when (basis) {
        ZoneBasis.ThresholdHeartRate -> Res.string.zones_basis_threshold_heart_rate
        ZoneBasis.MaxHeartRate -> Res.string.zones_basis_max_heart_rate
        ZoneBasis.HeartRateReserve -> Res.string.zones_basis_reserve
        ZoneBasis.FunctionalThresholdPower -> Res.string.zones_basis_ftp
        ZoneBasis.ThresholdPace -> Res.string.zones_basis_threshold_pace
    }

private fun missingInput(basis: ZoneBasis): StringResource =
    when (basis) {
        ZoneBasis.ThresholdHeartRate -> Res.string.zones_needs_threshold_heart_rate
        ZoneBasis.MaxHeartRate -> Res.string.zones_needs_max_heart_rate
        ZoneBasis.HeartRateReserve -> Res.string.zones_needs_reserve
        ZoneBasis.FunctionalThresholdPower -> Res.string.zones_needs_ftp
        ZoneBasis.ThresholdPace -> Res.string.zones_needs_threshold_pace
    }

internal fun zoneName(name: ZoneName): StringResource =
    when (name) {
        ZoneName.Recovery -> Res.string.zone_name_recovery
        ZoneName.Aerobic -> Res.string.zone_name_aerobic
        ZoneName.Tempo -> Res.string.zone_name_tempo
        ZoneName.SubThreshold -> Res.string.zone_name_sub_threshold
        ZoneName.SuperThreshold -> Res.string.zone_name_super_threshold
        ZoneName.AerobicCapacity -> Res.string.zone_name_aerobic_capacity
        ZoneName.AnaerobicCapacity -> Res.string.zone_name_anaerobic_capacity
        ZoneName.ActiveRecovery -> Res.string.zone_name_active_recovery
        ZoneName.Endurance -> Res.string.zone_name_endurance
        ZoneName.Threshold -> Res.string.zone_name_threshold
        ZoneName.Vo2Max -> Res.string.zone_name_vo2_max
        ZoneName.Neuromuscular -> Res.string.zone_name_neuromuscular
        ZoneName.VeryLight -> Res.string.zone_name_very_light
        ZoneName.Light -> Res.string.zone_name_light
        ZoneName.Moderate -> Res.string.zone_name_moderate
        ZoneName.Hard -> Res.string.zone_name_hard
        ZoneName.Maximum -> Res.string.zone_name_maximum
    }
