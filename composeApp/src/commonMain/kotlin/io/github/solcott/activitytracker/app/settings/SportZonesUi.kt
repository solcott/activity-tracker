package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ScrollbarBox
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.sportLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_save
import io.github.solcott.activitytracker.composeApp.resources.action_saving
import io.github.solcott.activitytracker.composeApp.resources.sport_zones_default
import io.github.solcott.activitytracker.composeApp.resources.sport_zones_help
import io.github.solcott.activitytracker.composeApp.resources.sport_zones_remove
import io.github.solcott.activitytracker.composeApp.resources.sport_zones_threshold_pace
import io.github.solcott.activitytracker.composeApp.resources.sport_zones_threshold_power
import io.github.solcott.activitytracker.composeApp.resources.sport_zones_title
import io.github.solcott.activitytracker.composeApp.resources.zones_heart_rate_heading
import io.github.solcott.activitytracker.composeApp.resources.zones_max_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_pace_faster
import io.github.solcott.activitytracker.composeApp.resources.zones_pace_range
import io.github.solcott.activitytracker.composeApp.resources.zones_pace_slower
import io.github.solcott.activitytracker.composeApp.resources.zones_power_heading
import io.github.solcott.activitytracker.composeApp.resources.zones_speed_heading
import io.github.solcott.activitytracker.composeApp.resources.zones_threshold_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_thresholds_heading
import io.github.solcott.activitytracker.composeApp.resources.zones_unit_bpm
import io.github.solcott.activitytracker.composeApp.resources.zones_unit_watts
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.training.PaceText
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.data.training.ThresholdInput
import io.github.solcott.activitytracker.data.training.Zone
import io.github.solcott.activitytracker.data.training.ZoneKind
import org.jetbrains.compose.resources.stringResource

@CircuitInject(SportZonesScreen::class, SessionScope::class)
@Composable
fun SportZonesUi(state: SportZonesState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    val sport = sportLabel(state.sport)
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.sport_zones_title, sport),
                onBack = { state.eventSink(SportZonesEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ContentStateLayout(state.content, UiError.Kind.LoadProfile, { state.eventSink(SportZonesEvent.Retry) }, contentPadding = padding) {
            val scroll = rememberScrollState()
            ScrollbarBox(scroll, contentPadding = padding) {
                Column(
                    Modifier.verticalScroll(scroll).padding(padding).readableWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(Res.string.zones_thresholds_heading), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(Res.string.sport_zones_help, sport), style = MaterialTheme.typography.bodySmall)
                    val paceUnit = paceUnitLabel(state.paceUnit)
                    if (ZoneKind.Speed in state.kinds) {
                        SportThresholdField(
                            state,
                            state.thresholdPace,
                            stringResource(Res.string.sport_zones_threshold_pace, paceUnit),
                            default = null,
                            valid = ThresholdInput.isValidPace(state.thresholdPace.text.toString(), state.paceUnit),
                            keyboard = KeyboardType.Text,
                        )
                    }
                    SportThresholdField(
                        state,
                        state.thresholdHeartRate,
                        stringResource(Res.string.zones_threshold_heart_rate),
                        state.defaults.thresholdHeartRate,
                        ThresholdInput.isValidHeartRate(state.thresholdHeartRate.text.toString()),
                    )
                    SportThresholdField(
                        state,
                        state.maxHeartRate,
                        stringResource(Res.string.zones_max_heart_rate),
                        state.defaults.maxHeartRate,
                        ThresholdInput.isValidHeartRate(state.maxHeartRate.text.toString()),
                    )
                    if (ZoneKind.Power in state.kinds) {
                        SportThresholdField(
                            state,
                            state.functionalThresholdPower,
                            stringResource(Res.string.sport_zones_threshold_power),
                            state.defaults.functionalThresholdPower,
                            ThresholdInput.isValidPower(state.functionalThresholdPower.text.toString()),
                        )
                    }
                    if (ZoneKind.Speed in state.kinds) {
                        HorizontalDivider()
                        ZoneSection(
                            heading = stringResource(Res.string.zones_speed_heading),
                            model = state.speedModel,
                            models = state.speedModels,
                            zones = state.speedZones,
                            enabled = state.loaded,
                            onPick = { state.eventSink(SportZonesEvent.SpeedModelChanged(it)) },
                        ) {
                            paceRange(it, state.paceUnit, paceUnit)
                        }
                    }
                    HorizontalDivider()
                    ZoneSection(
                        heading = stringResource(Res.string.zones_heart_rate_heading),
                        model = state.heartRateModel,
                        models = state.heartRateModels,
                        zones = state.heartRateZones,
                        enabled = state.loaded,
                        onPick = { state.eventSink(SportZonesEvent.HeartRateModelChanged(it)) },
                    ) {
                        numberRange(it, stringResource(Res.string.zones_unit_bpm))
                    }
                    if (ZoneKind.Power in state.kinds) {
                        HorizontalDivider()
                        ZoneSection(
                            heading = stringResource(Res.string.zones_power_heading),
                            model = state.powerModel,
                            models = state.powerModels,
                            zones = state.powerZones,
                            enabled = state.loaded,
                            onPick = { state.eventSink(SportZonesEvent.PowerModelChanged(it)) },
                        ) {
                            numberRange(it, stringResource(Res.string.zones_unit_watts))
                        }
                    }
                    state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
                    AppButton(
                        onClick = { state.eventSink(SportZonesEvent.Save) },
                        enabled = state.loaded && state.valid && !state.saving,
                    ) {
                        Text(stringResource(if (state.saving) Res.string.action_saving else Res.string.action_save))
                    }
                    if (state.saved) {
                        AppSecondaryButton(onClick = { state.eventSink(SportZonesEvent.Remove) }, enabled = !state.saving) {
                            Text(stringResource(Res.string.sport_zones_remove, sport))
                        }
                    }
                }
            }
        }
    }
}

/** A threshold field; blank, it shows the [default] it falls back to. */
@Composable
private fun SportThresholdField(
    state: SportZonesState,
    field: TextFieldState,
    label: String,
    default: Long?,
    valid: Boolean,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Number,
) {
    AppTextField(
        field,
        modifier = modifier.fillMaxWidth(),
        enabled = state.loaded,
        label = label,
        placeholder = default?.let { stringResource(Res.string.sport_zones_default, it.toString()) },
        isError = !valid,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        lineLimits = TextFieldLineLimits.SingleLine,
    )
}

/** A speed zone as paces, slowest first: "10:35–9:21 /mi", with open ends for the first and last zones. */
@Composable
internal fun paceRange(zone: Zone, unit: PaceUnit, unitLabel: String): String {
    val high = zone.high
    return when {
        zone.low == 0 && high != null -> stringResource(Res.string.zones_pace_slower, PaceText.format(high + 1L, unit), unitLabel)
        high == null -> stringResource(Res.string.zones_pace_faster, PaceText.format(zone.low.toLong(), unit), unitLabel)
        else ->
            stringResource(
                Res.string.zones_pace_range,
                PaceText.format(zone.low.toLong(), unit),
                PaceText.format(high + 1L, unit),
                unitLabel,
            )
    }
}
