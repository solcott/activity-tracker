package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.app.ui.adaptive.AppSheetHeader
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.export_action
import io.github.solcott.activitytracker.composeApp.resources.export_format_fit
import io.github.solcott.activitytracker.composeApp.resources.export_format_gpx
import io.github.solcott.activitytracker.composeApp.resources.export_format_tcx
import io.github.solcott.activitytracker.composeApp.resources.export_format_uddf
import io.github.solcott.activitytracker.composeApp.resources.export_note
import io.github.solcott.activitytracker.composeApp.resources.export_note_with_laps
import io.github.solcott.activitytracker.composeApp.resources.export_preparing
import io.github.solcott.activitytracker.composeApp.resources.export_title
import io.github.solcott.activitytracker.data.activity.ExportFormat
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The owner's export: the formats the activity's data allows, the note that the file is rebuilt (and holds the untrimmed route), and the
 * build's progress or error. The file itself is handed off by the detail screen once the presenter has it.
 */
@Composable
internal fun ExportSheet(export: ActivityExportState, eventSink: (ActivityDetailEvent) -> Unit, onDismiss: () -> Unit) {
    var selected by remember(export.formats) { mutableIntStateOf(0) }
    AppBottomSheet(onDismiss = onDismiss, sheetState = rememberAppSheetState(halfHeight = true)) {
        AppSheetHeader(
            title = stringResource(Res.string.export_title),
            cancelLabel = stringResource(Res.string.action_cancel),
            onCancel = onDismiss,
            confirmLabel = stringResource(Res.string.export_action),
            onConfirm = { if (!export.busy) eventSink(ActivityDetailEvent.Export(export.formats[selected])) },
        )
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (export.formats.size > 1) {
                val labels = export.formats.map { stringResource(it.label) }.toImmutableList()
                AppSegmentedControl(labels, selected, onSelect = { selected = it }, enabled = !export.busy)
            } else {
                Text(stringResource(export.formats.single().label), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(if (export.hasLaps) Res.string.export_note_with_laps else Res.string.export_note),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (export.busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppProgressIndicator(small = true)
                    Text(stringResource(Res.string.export_preparing), style = MaterialTheme.typography.bodySmall)
                }
            }
            export.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
        }
    }
}

private val ExportFormat.label: StringResource
    get() =
        when (this) {
            ExportFormat.GPX -> Res.string.export_format_gpx
            ExportFormat.TCX -> Res.string.export_format_tcx
            ExportFormat.FIT -> Res.string.export_format_fit
            ExportFormat.UDDF -> Res.string.export_format_uddf
        }
