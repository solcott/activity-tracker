package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ActivityVisibilityForm
import io.github.solcott.activitytracker.app.ui.UnitSystemPicker
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_save
import io.github.solcott.activitytracker.composeApp.resources.action_saving
import io.github.solcott.activitytracker.composeApp.resources.field_title
import io.github.solcott.activitytracker.composeApp.resources.manual_distance_label_km
import io.github.solcott.activitytracker.composeApp.resources.manual_distance_label_mi
import io.github.solcott.activitytracker.composeApp.resources.manual_duration_label
import io.github.solcott.activitytracker.composeApp.resources.manual_temperature_label_c
import io.github.solcott.activitytracker.composeApp.resources.manual_temperature_label_f
import io.github.solcott.activitytracker.composeApp.resources.manual_title
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitSystem
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(ManualActivityScreen::class, SessionScope::class)
@Composable
fun ManualActivityUi(state: ManualActivityState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.manual_title),
                onBack = { state.eventSink(ManualActivityEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding).readableWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActivityTypePicker(state.type)
            AppTextField(
                state.title,
                label = stringResource(Res.string.field_title),
                modifier = Modifier.fillMaxWidth(),
            )
            UnitSystemPicker(state.units, { state.eventSink(ManualActivityEvent.UnitsChanged(it)) }, Modifier.fillMaxWidth())
            AppTextField(
                state.distance,
                label =
                    stringResource(
                        when (state.units) {
                            UnitSystem.Metric -> Res.string.manual_distance_label_km
                            UnitSystem.Imperial -> Res.string.manual_distance_label_mi
                        }
                    ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            AppTextField(
                state.durationMinutes,
                label = stringResource(Res.string.manual_duration_label),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            AppTextField(
                state.temperature,
                label =
                    stringResource(
                        when (state.units) {
                            UnitSystem.Metric -> Res.string.manual_temperature_label_c
                            UnitSystem.Imperial -> Res.string.manual_temperature_label_f
                        }
                    ),
                // Not Decimal: some numeric keyboards have no minus key.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
            ActivityVisibilityForm(
                state.visibility,
                enabled = !state.saving,
            )
            state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
            AppButton(
                onClick = { state.eventSink(ManualActivityEvent.Save) },
                enabled = state.valid && !state.saving,
            ) {
                Text(stringResource(if (state.saving) Res.string.action_saving else Res.string.action_save))
            }
        }
    }
}
