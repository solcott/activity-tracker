package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.ActivityVisibilityForm
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppCheckbox
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_saving
import io.github.solcott.activitytracker.composeApp.resources.dive_number
import io.github.solcott.activitytracker.composeApp.resources.import_deselect_all
import io.github.solcott.activitytracker.composeApp.resources.import_dives_details_hint
import io.github.solcott.activitytracker.composeApp.resources.import_dives_found
import io.github.solcott.activitytracker.composeApp.resources.import_dives_none
import io.github.solcott.activitytracker.composeApp.resources.import_dives_save
import io.github.solcott.activitytracker.composeApp.resources.import_dives_selected
import io.github.solcott.activitytracker.composeApp.resources.import_select_all
import kotlinx.collections.immutable.toImmutableMap
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A logbook's dives, each a row to select, then one button that imports the selected ones. */
@Composable
internal fun DiveBatchForm(state: ImportActivityState, batch: DiveBatch, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(batch.fileName, style = MaterialTheme.typography.titleMedium)
        Text(
            pluralStringResource(Res.plurals.import_dives_found, batch.dives.size, batch.dives.size),
            style = MaterialTheme.typography.bodySmall,
        )
        batch.clock?.let { clock ->
            TimeZoneNotice(clock, { state.eventSink(ImportActivityEvent.TimeZoneChanged(it)) }, enabled = !state.saving)
        }
        DuplicatesBanner(state.duplicateCheck, batch.existingDuplicates, batch.inFileDuplicates)
        Text(
            stringResource(Res.string.import_dives_details_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.import_dives_selected, batch.selectedCount, batch.dives.size),
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
            )
            if (batch.hasNonDuplicates) {
                AppTextButton(
                    onClick = { state.eventSink(ImportActivityEvent.AllDivesSelected(!batch.allSelected)) },
                    enabled = !state.saving,
                ) {
                    Text(stringResource(if (batch.allSelected) Res.string.import_deselect_all else Res.string.import_select_all))
                }
            }
        }
        batch.dives.forEach { dive ->
            DiveRowItem(
                dive,
                onToggle = { state.eventSink(ImportActivityEvent.DiveToggled(dive.index)) },
                onAction = { state.eventSink(ImportActivityEvent.DuplicateActionChanged(dive.index, it)) },
                enabled = !state.saving,
                // The earlier dive by its computer's number where it has one, as its row shows it.
                inFileNumber = dive.duplicate?.inFileIndex?.let { batch.dives.getOrNull(it)?.diveNumber ?: (it + 1) },
            )
        }
        // One choice for every dive in the batch.
        ActivityVisibilityForm(
            state.visibility,
            hasCharts = true,
            onActivityChange = { state.eventSink(ImportActivityEvent.VisibilityChanged(it)) },
            onChartsChange = { state.eventSink(ImportActivityEvent.ChartsVisibilityChanged(it)) },
            enabled = !state.saving,
        )
        val defaultTitles =
            batch.dives.filter { it.title == null }.associate { it.index to defaultTitleLabel(it.defaultTitle) }.toImmutableMap()
        AppButton(
            onClick = { state.eventSink(ImportActivityEvent.SaveDives(defaultTitles)) },
            enabled = !state.saving && !state.reading && batch.selectedCount > 0 && state.duplicateCheck != DuplicateCheck.Checking,
        ) {
            Text(
                when {
                    state.saving -> stringResource(Res.string.action_saving)
                    batch.selectedCount == 0 -> stringResource(Res.string.import_dives_none)
                    else -> pluralStringResource(Res.plurals.import_dives_save, batch.selectedCount, batch.selectedCount)
                }
            )
        }
    }
}

/**
 * "Blue Hole", then "2024-05-12 10:02 · 30.1 m · 45:30 · #12 · Shearwater Perdix"; the whole row toggles. A duplicate says what it repeats
 * and offers what to do with it.
 */
@Composable
@Suppress("LongParameterList") // A row and its duplicate's choices.
private fun DiveRowItem(
    dive: DiveRow,
    onToggle: () -> Unit,
    onAction: (DuplicateAction) -> Unit,
    enabled: Boolean,
    inFileNumber: Int?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        DiveRowSummary(dive, onToggle, enabled)
        dive.duplicate?.let { DuplicateWarning(it, onAction, enabled, Modifier.padding(start = 36.dp, bottom = 8.dp), inFileNumber) }
    }
}

@Composable
private fun DiveRowSummary(dive: DiveRow, onToggle: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(dive.selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppCheckbox(dive.selected, onCheckedChange = null, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(dive.title ?: defaultTitleLabel(dive.defaultTitle), style = MaterialTheme.typography.bodyLarge)
            val details =
                listOfNotNull(
                    dive.start,
                    dive.maxDepthMeters?.let { Format.depth(it) },
                    Format.duration(dive.elapsed),
                    dive.diveNumber?.let { stringResource(Res.string.dive_number, it) },
                    dive.device,
                )
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
