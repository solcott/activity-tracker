package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.UnitSystemPicker
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.profile_sign_out
import io.github.solcott.activitytracker.composeApp.resources.settings_saving
import io.github.solcott.activitytracker.composeApp.resources.settings_title
import io.github.solcott.activitytracker.composeApp.resources.settings_units_heading
import io.github.solcott.activitytracker.composeApp.resources.settings_units_imperial_example
import io.github.solcott.activitytracker.composeApp.resources.settings_units_metric_example
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitSystem
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(SettingsScreen::class, SessionScope::class)
@Composable
fun SettingsUi(state: SettingsState, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = stringResource(Res.string.settings_title), onBack = { state.eventSink(SettingsEvent.Back) }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).readableWidth().padding(16.dp),
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
            state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
            HorizontalDivider()
            AppSecondaryButton(onClick = { state.eventSink(SettingsEvent.SignOut) }) { Text(stringResource(Res.string.profile_sign_out)) }
        }
    }
}
