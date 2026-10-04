package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.UnitSystemPicker
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_back
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
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.manual_title)) },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(ManualActivityEvent.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SportPicker(state.sport, { state.eventSink(ManualActivityEvent.SportChanged(it)) })
            OutlinedTextField(
                state.title,
                { state.eventSink(ManualActivityEvent.TitleChanged(it)) },
                label = { Text(stringResource(Res.string.field_title)) },
                modifier = Modifier.fillMaxWidth(),
            )
            UnitSystemPicker(state.units, { state.eventSink(ManualActivityEvent.UnitsChanged(it)) }, Modifier.fillMaxWidth())
            OutlinedTextField(
                state.distance,
                { state.eventSink(ManualActivityEvent.DistanceChanged(it)) },
                label = {
                    Text(
                        stringResource(
                            when (state.units) {
                                UnitSystem.Metric -> Res.string.manual_distance_label_km
                                UnitSystem.Imperial -> Res.string.manual_distance_label_mi
                            }
                        )
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                state.durationMinutes,
                { state.eventSink(ManualActivityEvent.DurationChanged(it)) },
                label = { Text(stringResource(Res.string.manual_duration_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                state.temperature,
                { state.eventSink(ManualActivityEvent.TemperatureChanged(it)) },
                label = {
                    Text(
                        stringResource(
                            when (state.units) {
                                UnitSystem.Metric -> Res.string.manual_temperature_label_c
                                UnitSystem.Imperial -> Res.string.manual_temperature_label_f
                            }
                        )
                    )
                },
                // Not Decimal: some numeric keyboards have no minus key.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { state.eventSink(ManualActivityEvent.Save) },
                enabled = state.valid && !state.saving,
            ) {
                Text(stringResource(if (state.saving) Res.string.action_saving else Res.string.action_save))
            }
        }
    }
}
