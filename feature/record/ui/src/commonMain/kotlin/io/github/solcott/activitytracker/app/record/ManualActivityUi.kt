package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.ui.ActivityVisibilityForm
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.UnitSystemPicker
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.field_title
import io.github.solcott.activitytracker.composeApp.resources.manual_duration_label
import io.github.solcott.activitytracker.composeApp.resources.manual_title
import io.github.solcott.activitytracker.data.di.SessionScope
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
        ReadableScrollColumn(padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ActivityTypePicker(state.type)
            AppTextField(
                state.title,
                label = stringResource(Res.string.field_title),
                modifier = Modifier.fillMaxWidth(),
            )
            UnitSystemPicker(state.units, { state.eventSink(ManualActivityEvent.UnitsChanged(it)) }, Modifier.fillMaxWidth())
            AppTextField(
                state.distance,
                label = ManualActivityTexts.distanceLabel(state.units).string(),
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
                label = ManualActivityTexts.temperatureLabel(state.units).string(),
                // Not Decimal: some numeric keyboards have no minus key.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
            GearPicker(state.gear, enabled = !state.saving)
            ActivityVisibilityForm(
                state.visibility,
                enabled = !state.saving,
            )
            state.error?.let { ErrorText(it) }
            AppButton(
                onClick = { state.eventSink(ManualActivityEvent.Save) },
                enabled = state.valid && !state.saving,
            ) {
                Text(LabelText.save(state.saving).string())
            }
        }
    }
}
