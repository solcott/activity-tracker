package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
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
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.MenuField
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSwitch
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.zones_auto_update
import io.github.solcott.activitytracker.composeApp.resources.zones_auto_update_help
import io.github.solcott.activitytracker.composeApp.resources.zones_default_help
import io.github.solcott.activitytracker.composeApp.resources.zones_default_title
import io.github.solcott.activitytracker.composeApp.resources.zones_ftp
import io.github.solcott.activitytracker.composeApp.resources.zones_max_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_model
import io.github.solcott.activitytracker.composeApp.resources.zones_resting_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_threshold_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_thresholds_heading
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.data.training.Zone
import io.github.solcott.activitytracker.data.training.ZoneKind
import io.github.solcott.activitytracker.data.training.ZoneModel
import io.github.solcott.activitytracker.textfield.TextFieldState
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
            ReadableScrollColumn(padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.zones_default_help), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.zones_thresholds_heading), style = MaterialTheme.typography.titleMedium)
                ThresholdField(state, state.thresholdHeartRate, ZoneThreshold.ThresholdHeartRate, Res.string.zones_threshold_heart_rate)
                ThresholdField(state, state.maxHeartRate, ZoneThreshold.MaxHeartRate, Res.string.zones_max_heart_rate)
                ThresholdField(state, state.restingHeartRate, ZoneThreshold.RestingHeartRate, Res.string.zones_resting_heart_rate)
                ThresholdField(state, state.functionalThresholdPower, ZoneThreshold.Power, Res.string.zones_ftp)
                Text(stringResource(Res.string.zones_auto_update_help), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                ZoneSection(
                    kind = ZoneKind.HeartRate,
                    model = state.heartRateModel,
                    models = state.heartRateModels,
                    zones = state.heartRateZones,
                    enabled = state.loaded,
                    onPick = { state.eventSink(ZonesEvent.HeartRateModelChanged(it)) },
                )
                HorizontalDivider()
                ZoneSection(
                    kind = ZoneKind.Power,
                    model = state.powerModel,
                    models = state.powerModels,
                    zones = state.powerZones,
                    enabled = state.loaded,
                    onPick = { state.eventSink(ZonesEvent.PowerModelChanged(it)) },
                )
                state.error?.let { ErrorText(it) }
                AppButton(onClick = { state.eventSink(ZonesEvent.Save) }, enabled = state.canSave) {
                    Text(LabelText.save(state.saving).string())
                }
            }
        }
    }
}

/** A threshold's field, and whether imported activities may update it. */
@Composable
private fun ThresholdField(
    state: ZonesState,
    field: TextFieldState,
    threshold: ZoneThreshold,
    label: StringResource,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        AppTextField(
            field,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.loaded,
            label = stringResource(label),
            isError = !state.isValid(threshold, field.text.toString()),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        val checked = state.autoUpdates(threshold)
        Row(
            Modifier.fillMaxWidth()
                .toggleable(checked, enabled = state.loaded, role = Role.Switch) {
                    state.eventSink(ZonesEvent.AutoUpdateChanged(threshold.token, it))
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
 * A kind of zone's model picker, and the zones it gives (read-only), or what's missing to work them out. A speed zone reads as paces per
 * [paceUnit].
 */
@Composable
internal fun ZoneSection(
    kind: ZoneKind,
    model: ZoneModel,
    models: ImmutableList<ZoneModel>,
    zones: ImmutableList<Zone>?,
    enabled: Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    paceUnit: PaceUnit = PaceUnit.PerKilometer,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(ZoneTexts.heading(kind).string(), style = MaterialTheme.typography.titleMedium)
        val items = models.map { AppMenuItem(ZoneTexts.model(it).string(), checked = it == model) { onPick(it.token) } }.toImmutableList()
        MenuField(
            ZoneTexts.model(model).string(),
            stringResource(Res.string.zones_model),
            items,
            Modifier.fillMaxWidth(),
            enabled = enabled,
        )
        ZoneList(model, zones) { ZoneTexts.range(it, kind, paceUnit).string() }
    }
}

/** What [model] is based on, then its [zones] (read-only), or what's missing to work them out. */
@Composable
internal fun ZoneList(model: ZoneModel, zones: ImmutableList<Zone>?, modifier: Modifier = Modifier, range: @Composable (Zone) -> String) {
    Column(modifier) {
        Text(ZoneTexts.basis(model.basis).string(), style = MaterialTheme.typography.bodySmall)
        if (zones == null) {
            Text(
                ZoneTexts.missing(model.basis).string(),
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            zones.forEach { ZoneRow(it, range(it)) }
        }
    }
}

@Composable
private fun ZoneRow(zone: Zone, range: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            ZoneTexts.label(zone).string(),
            Modifier.widthIn(min = ZONE_LABEL_WIDTH),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(ZoneTexts.name(zone).string(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(range, style = MaterialTheme.typography.bodyMedium)
    }
}

private val ZONE_LABEL_WIDTH = 64.dp
