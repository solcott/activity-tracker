package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.Stat
import io.github.solcott.activitytracker.app.ui.StatsRow
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.field_title
import io.github.solcott.activitytracker.composeApp.resources.record_discard
import io.github.solcott.activitytracker.composeApp.resources.record_finish
import io.github.solcott.activitytracker.composeApp.resources.record_pause
import io.github.solcott.activitytracker.composeApp.resources.record_resume
import io.github.solcott.activitytracker.composeApp.resources.record_start
import io.github.solcott.activitytracker.composeApp.resources.record_title
import io.github.solcott.activitytracker.composeApp.resources.stat_distance
import io.github.solcott.activitytracker.composeApp.resources.stat_elevation
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.location.RecordingState
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(RecordScreen::class, SessionScope::class)
@Composable
fun RecordUi(state: RecordState, modifier: Modifier = Modifier) {
    val requestPermission = rememberLocationPermissionRequest { state.eventSink(RecordEvent.PermissionResult(it)) }
    AppScaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = stringResource(Res.string.record_title), onBack = { state.eventSink(RecordEvent.Back) }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).readableWidth().padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ActivityTypePicker(
                state.type,
                state.quickTypes,
                { state.eventSink(RecordEvent.TypeChanged(it)) },
                enabled = state.status == RecordingState.Status.Idle,
                outdoorOnly = true,
            )
            Text(Format.duration(state.elapsed), style = MaterialTheme.typography.displayLarge)
            StatsRow(
                listOfNotNull(
                        Stat(stringResource(Res.string.stat_distance), Format.distance(state.distanceMeters)),
                        Format.rateStat(state.type.sport, state.distanceMeters, state.movingSeconds),
                        Stat(stringResource(Res.string.stat_elevation), Format.elevation(state.elevationGainMeters)),
                    )
                    .toImmutableList()
            )
            AppTextField(
                state = state.title,
                label = stringResource(Res.string.field_title),
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state.status) {
                    RecordingState.Status.Idle -> {
                        AppButton(
                            onClick = {
                                if (state.hasPermission) {
                                    state.eventSink(RecordEvent.Start)
                                } else {
                                    requestPermission()
                                }
                            },
                            enabled = !state.saving,
                        ) {
                            Text(stringResource(Res.string.record_start))
                        }
                    }

                    RecordingState.Status.Recording -> {
                        AppButton(onClick = { state.eventSink(RecordEvent.Pause) }) { Text(stringResource(Res.string.record_pause)) }
                    }

                    RecordingState.Status.Paused -> {
                        AppButton(onClick = { state.eventSink(RecordEvent.Resume) }) { Text(stringResource(Res.string.record_resume)) }
                        AppButton(onClick = { state.eventSink(RecordEvent.Finish) }) { Text(stringResource(Res.string.record_finish)) }
                        AppSecondaryButton(onClick = { state.eventSink(RecordEvent.Discard) }) {
                            Text(stringResource(Res.string.record_discard))
                        }
                    }
                }
            }
        }
    }
}
