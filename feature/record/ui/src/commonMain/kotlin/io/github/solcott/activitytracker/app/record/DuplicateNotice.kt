package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.duplicate_action_combine
import io.github.solcott.activitytracker.composeApp.resources.duplicate_action_ignore
import io.github.solcott.activitytracker.composeApp.resources.duplicate_action_import_anyway
import io.github.solcott.activitytracker.composeApp.resources.import_combine_explained
import io.github.solcott.activitytracker.composeApp.resources.import_combine_uploading
import io.github.solcott.activitytracker.composeApp.resources.import_duplicate_existing
import io.github.solcott.activitytracker.composeApp.resources.import_duplicate_in_file
import io.github.solcott.activitytracker.composeApp.resources.import_duplicate_shifted
import io.github.solcott.activitytracker.composeApp.resources.import_duplicate_untitled
import io.github.solcott.activitytracker.composeApp.resources.import_duplicates_checking
import io.github.solcott.activitytracker.composeApp.resources.import_duplicates_existing
import io.github.solcott.activitytracker.composeApp.resources.import_duplicates_failed
import io.github.solcott.activitytracker.composeApp.resources.import_duplicates_in_file
import io.github.solcott.activitytracker.composeApp.resources.import_duplicates_unchecked
import kotlin.math.abs
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Why an activity looks like a duplicate ("Already in your log: Blue Hole · 2024-05-12 10:02 · Garmin Descent Mk3") and what to do with it.
 * [inFileNumber] numbers the activity [DuplicateInfo.inFileIndex] names, as the list shows it. Combine is left out, with the reason, while
 * the logged activity is still uploading.
 */
@Composable
internal fun DuplicateWarning(
    info: DuplicateInfo,
    onAction: (DuplicateAction) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    inFileNumber: Int? = info.inFileIndex?.plus(1),
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(warningText(info, inFileNumber), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        val actions = info.actions.filter { it != DuplicateAction.Combine || !info.stillUploading }
        AppSegmentedControl(
            actions.map { actionLabel(it) }.toImmutableList(),
            selected = actions.indexOf(info.action),
            onSelect = { onAction(actions[it]) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
        val explained =
            when {
                info.stillUploading -> stringResource(Res.string.import_combine_uploading)
                info.action == DuplicateAction.Combine -> stringResource(Res.string.import_combine_explained)
                else -> null
            }
        explained?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** A single file's activity: the search of the athlete's log while it runs, then what to do if the activity is already there. */
@Composable
internal fun PreviewDuplicate(state: ImportActivityState, preview: ImportPreview, modifier: Modifier = Modifier) {
    if (state.duplicateCheck == DuplicateCheck.Checking) DuplicatesBanner(state.duplicateCheck, existing = 0, inFile = 0, modifier)
    preview.duplicate?.let { duplicate ->
        DuplicateWarning(duplicate, { state.eventSink(ImportActivityEvent.DuplicateActionChanged(0, it)) }, !state.saving, modifier)
    }
}

/**
 * An ignored duplicate has nothing to save; nor has an activity until the athlete's log has been checked for it, nor a recording that can't
 * be added to the activity it's for (too far from it, or that activity still loading).
 */
internal fun ImportActivityState.canSave(preview: ImportPreview): Boolean =
    !saving &&
        !reading &&
        preview.duplicate?.action != DuplicateAction.Ignore &&
        duplicateCheck != DuplicateCheck.Checking &&
        !tooFarFromTarget &&
        (!addsRecording || addingTo != null)

/** Above a logbook's dives: how many are duplicates, or that the search for them is running or failed. */
@Composable
internal fun DuplicatesBanner(check: DuplicateCheck, existing: Int, inFile: Int, modifier: Modifier = Modifier) {
    val lines =
        listOfNotNull(
            stringResource(Res.string.import_duplicates_checking).takeIf { check == DuplicateCheck.Checking },
            stringResource(Res.string.import_duplicates_failed).takeIf { check == DuplicateCheck.Failed },
            pluralStringResource(Res.plurals.import_duplicates_existing, existing, existing).takeIf { existing > 0 },
            pluralStringResource(Res.plurals.import_duplicates_in_file, inFile, inFile).takeIf { inFile > 0 },
            stringResource(Res.string.import_duplicates_unchecked).takeIf { existing + inFile > 0 },
        )
    if (lines.isEmpty()) return
    Text(
        lines.joinToString(" "),
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium).padding(12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun warningText(info: DuplicateInfo, inFileNumber: Int?): String {
    val existing = info.existing
    if (existing == null) return stringResource(Res.string.import_duplicate_in_file, inFileNumber ?: 0)
    val title = existing.title ?: stringResource(Res.string.import_duplicate_untitled)
    val details = listOfNotNull(title, existing.start, existing.device).joinToString(" · ")
    return if (info.hoursApart == 0) stringResource(Res.string.import_duplicate_existing, details)
    else stringResource(Res.string.import_duplicate_shifted, details, abs(info.hoursApart))
}

@Composable
private fun actionLabel(action: DuplicateAction): String =
    when (action) {
        DuplicateAction.Ignore -> stringResource(Res.string.duplicate_action_ignore)
        DuplicateAction.ImportAnyway -> stringResource(Res.string.duplicate_action_import_anyway)
        DuplicateAction.Combine -> stringResource(Res.string.duplicate_action_combine)
    }
