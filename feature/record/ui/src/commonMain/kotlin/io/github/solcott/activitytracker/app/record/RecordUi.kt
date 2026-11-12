package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ActivityVisibilityForm
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.Stat
import io.github.solcott.activitytracker.app.ui.StatsRow
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.field_title
import io.github.solcott.activitytracker.composeApp.resources.record_title
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

@CircuitInject(RecordScreen::class, SessionScope::class)
@Composable
fun RecordUi(state: RecordState, modifier: Modifier = Modifier) {
    val requestPermission = rememberLocationPermissionRequest { state.eventSink(RecordEvent.PermissionResult(it)) }
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.record_title),
                onBack = { state.eventSink(RecordEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        // Scrolls: the visibility pickers don't fit a small phone's height with everything else.
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding).readableWidth().padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ActivityTypePicker(
                state.type,
                enabled = RecordTexts.typeChangeable(state),
                outdoorOnly = true,
            )
            Text(RecordTexts.elapsed(state).string(), style = MaterialTheme.typography.displayLarge)
            StatsRow(
                RecordTexts.stats(state, LocalUnitSystem.current).map { Stat(it.label.string(), it.value.string()) }.toImmutableList(),
            )
            AppTextField(
                state = state.title,
                label = stringResource(Res.string.field_title),
                modifier = Modifier.fillMaxWidth(),
            )
            GearPicker(state.gear, enabled = !state.saving)
            ActivityVisibilityForm(
                state.visibility,
                enabled = !state.saving,
            )
            if (state.recovered) Text(RecordTexts.recovered.string())
            if (state.saveFailed) Text(RecordTexts.saveFailed.string(), color = MaterialTheme.colorScheme.error)
            state.error?.let { ErrorText(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RecordTexts.actions(state).forEach { action ->
                    val onClick = {
                        when (action) {
                            RecordAction.Start -> if (state.hasPermission) state.eventSink(RecordEvent.Start) else requestPermission()
                            RecordAction.Pause -> state.eventSink(RecordEvent.Pause)
                            RecordAction.Resume -> state.eventSink(RecordEvent.Resume)
                            RecordAction.Finish -> state.eventSink(RecordEvent.Finish)
                            RecordAction.Discard -> state.eventSink(RecordEvent.Discard)
                        }
                    }
                    val label = RecordTexts.label(action).string()
                    if (action == RecordAction.Discard) {
                        AppSecondaryButton(onClick = onClick, enabled = !state.saving) { Text(label) }
                    } else {
                        AppButton(onClick = onClick, enabled = !state.saving) { Text(label) }
                    }
                }
            }
        }
    }
}
