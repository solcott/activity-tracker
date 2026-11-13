package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
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
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.sport_zones_threshold_power
import io.github.solcott.activitytracker.composeApp.resources.zones_max_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_threshold_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_thresholds_heading
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.training.ZoneKind
import io.github.solcott.activitytracker.textfield.TextFieldState
import org.jetbrains.compose.resources.stringResource

@CircuitInject(SportZonesScreen::class, SessionScope::class)
@Composable
fun SportZonesUi(state: SportZonesState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = SportZonesTexts.title(state.sport).string(),
                onBack = { state.eventSink(SportZonesEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ContentStateLayout(state.content, UiError.Kind.LoadProfile, { state.eventSink(SportZonesEvent.Retry) }, contentPadding = padding) {
            ReadableScrollColumn(padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.zones_thresholds_heading), style = MaterialTheme.typography.titleMedium)
                Text(SportZonesTexts.help(state.sport).string(), style = MaterialTheme.typography.bodySmall)
                if (state.offers(ZoneKind.Speed)) {
                    SportThresholdField(
                        state,
                        state.thresholdPace,
                        ZoneThreshold.Pace,
                        SportZonesTexts.paceLabel(state.paceUnit).string(),
                        keyboard = KeyboardType.Text,
                    )
                }
                SportThresholdField(
                    state,
                    state.thresholdHeartRate,
                    ZoneThreshold.ThresholdHeartRate,
                    stringResource(Res.string.zones_threshold_heart_rate),
                )
                SportThresholdField(state, state.maxHeartRate, ZoneThreshold.MaxHeartRate, stringResource(Res.string.zones_max_heart_rate))
                if (state.offers(ZoneKind.Power)) {
                    SportThresholdField(
                        state,
                        state.functionalThresholdPower,
                        ZoneThreshold.Power,
                        stringResource(Res.string.sport_zones_threshold_power),
                    )
                }
                if (state.offers(ZoneKind.Speed)) {
                    HorizontalDivider()
                    ZoneSection(
                        kind = ZoneKind.Speed,
                        model = state.speedModel,
                        models = state.speedModels,
                        zones = state.speedZones,
                        enabled = state.loaded,
                        onPick = { state.eventSink(SportZonesEvent.SpeedModelChanged(it)) },
                        paceUnit = state.paceUnit,
                    )
                }
                HorizontalDivider()
                ZoneSection(
                    kind = ZoneKind.HeartRate,
                    model = state.heartRateModel,
                    models = state.heartRateModels,
                    zones = state.heartRateZones,
                    enabled = state.loaded,
                    onPick = { state.eventSink(SportZonesEvent.HeartRateModelChanged(it)) },
                )
                if (state.offers(ZoneKind.Power)) {
                    HorizontalDivider()
                    ZoneSection(
                        kind = ZoneKind.Power,
                        model = state.powerModel,
                        models = state.powerModels,
                        zones = state.powerZones,
                        enabled = state.loaded,
                        onPick = { state.eventSink(SportZonesEvent.PowerModelChanged(it)) },
                    )
                }
                state.error?.let { ErrorText(it) }
                AppButton(onClick = { state.eventSink(SportZonesEvent.Save) }, enabled = state.canSave) {
                    Text(LabelText.save(state.saving).string())
                }
                if (state.saved) {
                    AppSecondaryButton(onClick = { state.eventSink(SportZonesEvent.Remove) }, enabled = !state.saving) {
                        Text(SportZonesTexts.remove(state.sport).string())
                    }
                }
            }
        }
    }
}

/** A threshold field; blank, it shows the default it falls back to. */
@Composable
private fun SportThresholdField(
    state: SportZonesState,
    field: TextFieldState,
    threshold: ZoneThreshold,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Number,
) {
    AppTextField(
        field,
        modifier = modifier.fillMaxWidth(),
        enabled = state.loaded,
        label = label,
        placeholder = SportZonesTexts.defaultHint(state, threshold)?.string(),
        isError = !state.isValid(threshold, field.text.toString()),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        lineLimits = TextFieldLineLimits.SingleLine,
    )
}
