package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.Stat
import io.github.solcott.activitytracker.app.ui.StatsRow
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_back
import io.github.solcott.activitytracker.composeApp.resources.action_save
import io.github.solcott.activitytracker.composeApp.resources.action_saving
import io.github.solcott.activitytracker.composeApp.resources.field_title
import io.github.solcott.activitytracker.composeApp.resources.import_hint
import io.github.solcott.activitytracker.composeApp.resources.import_pick
import io.github.solcott.activitytracker.composeApp.resources.import_pick_another
import io.github.solcott.activitytracker.composeApp.resources.import_reading
import io.github.solcott.activitytracker.composeApp.resources.import_recorded_with
import io.github.solcott.activitytracker.composeApp.resources.import_title
import io.github.solcott.activitytracker.composeApp.resources.stat_distance
import io.github.solcott.activitytracker.composeApp.resources.stat_elevation
import io.github.solcott.activitytracker.composeApp.resources.stat_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.stat_time
import io.github.solcott.activitytracker.composeApp.resources.unit_bpm
import io.github.solcott.activitytracker.composeApp.resources.value_missing
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(ImportActivityScreen::class, SessionScope::class)
@Composable
fun ImportActivityUi(state: ImportActivityState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    // Unfiltered: Android and iOS have no MIME type or UTType for .fit, so an extension filter hides the files. The presenter checks it.
    val pick =
        rememberFilePickerLauncher(type = FileKitType.File()) { file ->
            file ?: return@rememberFilePickerLauncher
            scope.launch {
                runSuspendCatching { file.readBytes() }
                    .onSuccess { state.eventSink(ImportActivityEvent.FilePicked(file.name, ByteString(it))) }
            }
        }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(ImportActivityEvent.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val preview = state.preview
            if (preview == null) Text(stringResource(Res.string.import_hint))
            OutlinedButton(onClick = { pick.launch() }, enabled = !state.reading && !state.saving) {
                Text(stringResource(if (preview == null) Res.string.import_pick else Res.string.import_pick_another))
            }
            if (state.reading) {
                CircularProgressIndicator()
                Text(stringResource(Res.string.import_reading))
            }
            state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
            if (preview != null) PreviewForm(state, preview)
        }
    }
}

@Composable
private fun PreviewForm(state: ImportActivityState, preview: ImportPreview, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(preview.fileName, style = MaterialTheme.typography.titleMedium)
        preview.device?.let { Text(stringResource(Res.string.import_recorded_with, it), style = MaterialTheme.typography.bodySmall) }
        StatsRow(
            persistentListOf(
                Stat(stringResource(Res.string.stat_distance), Format.distance(preview.distanceMeters)),
                Stat(stringResource(Res.string.stat_time), Format.duration(preview.movingSeconds ?: preview.elapsed.inWholeSeconds)),
                Stat(stringResource(Res.string.stat_elevation), Format.elevation(preview.elevationGainMeters)),
                Stat(
                    stringResource(Res.string.stat_heart_rate),
                    preview.avgHeartRate?.let { stringResource(Res.string.unit_bpm, it) } ?: stringResource(Res.string.value_missing),
                ),
            )
        )
        preview.polyline?.let { ActivityMap(it) }
        SportPicker(state.sport, { state.eventSink(ImportActivityEvent.SportChanged(it)) }, enabled = !state.saving)
        OutlinedTextField(
            state.title,
            { state.eventSink(ImportActivityEvent.TitleChanged(it)) },
            label = { Text(stringResource(Res.string.field_title)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { state.eventSink(ImportActivityEvent.Save) }, enabled = !state.saving && !state.reading) {
            Text(stringResource(if (state.saving) Res.string.action_saving else Res.string.action_save))
        }
    }
}
