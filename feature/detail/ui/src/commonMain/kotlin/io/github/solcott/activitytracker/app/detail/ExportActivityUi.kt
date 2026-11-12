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
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.app.ui.adaptive.AppSheetHeader
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.string
import kotlinx.collections.immutable.toImmutableList

/**
 * The owner's export: the original file the activity was imported from, if any, listed first and apart; then the formats the activity's
 * data allows, with the note that such a file is rebuilt (and holds the untrimmed route); and the build's progress or error. The file
 * itself is handed off by the detail screen once the presenter has it.
 */
@Composable
internal fun ExportSheet(export: ActivityExportState, eventSink: (ActivityDetailEvent) -> Unit, onDismiss: () -> Unit) {
    var selected by remember(export.formats) { mutableIntStateOf(0) }
    val original = export.original
    val rebuilt = export.formats.isNotEmpty()
    AppBottomSheet(onDismiss = onDismiss, sheetState = rememberAppSheetState(halfHeight = true)) {
        AppSheetHeader(
            title = ActivityPageTexts.exportTitle.string(),
            cancelLabel = ActivityPageTexts.cancel.string(),
            onCancel = onDismiss,
            // With only an original, the header downloads it; otherwise it exports the rebuilt format picked.
            confirmLabel = ActivityPageTexts.exportAction(export).string(),
            onConfirm = { ActivityPageTexts.exportEvent(export, selected)?.let(eventSink) },
        )
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (original != null) OriginalFileRow(original, showDownload = rebuilt, enabled = !export.busy, eventSink)
            if (rebuilt) {
                if (original != null) Text(ActivityPageTexts.exportRebuilt.string(), style = MaterialTheme.typography.titleMedium)
                if (export.formats.size > 1) {
                    val labels = export.formats.map { ActivityPageTexts.formatName(it).string() }.toImmutableList()
                    AppSegmentedControl(labels, selected, onSelect = { selected = it }, enabled = !export.busy)
                } else {
                    Text(ActivityPageTexts.formatName(export.formats.single()).string(), style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    ActivityPageTexts.exportNote(export).string(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (export.busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppProgressIndicator(small = true)
                    Text(ActivityPageTexts.exportPreparing.string(), style = MaterialTheme.typography.bodySmall)
                }
            }
            export.error?.let { ErrorText(it) }
        }
    }
}

/** The imported file, by its name; its own Download button when the header's action is for the rebuilt formats ([showDownload]). */
@Composable
private fun OriginalFileRow(
    original: OriginalFile,
    showDownload: Boolean,
    enabled: Boolean,
    eventSink: (ActivityDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(ActivityPageTexts.exportOriginal.string(), style = MaterialTheme.typography.titleMedium)
            Text(original.name, style = MaterialTheme.typography.bodyMedium)
            Text(ActivityPageTexts.exportOriginalExplained.string(), style = MaterialTheme.typography.bodySmall)
        }
        if (showDownload) {
            AppSecondaryButton(onClick = { eventSink(ActivityDetailEvent.DownloadOriginal) }, enabled = enabled) {
                Text(ActivityPageTexts.exportDownload.string())
            }
        }
    }
}
