package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.detail.ITEM_GAP
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.ActivityVisibilityForm
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppCheckbox
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChip
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.import_batch_visibility_heading
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

/**
 * Several activities to import: what was read, then one row per activity grouped by month (a lazy list, for an account export's thousands),
 * then the choices for the whole batch and Save. Tapping a row opens it to look at and retitle.
 */
@Composable
internal fun BatchContent(state: ImportActivityState, batch: ImportBatch, padding: PaddingValues, onPick: () -> Unit) {
    val listState = rememberLazyListState()
    val enabled = !state.saving
    val months = remember(batch) { ImportActivityTexts.months(batch) }
    ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding) {
            item(key = "pick") { PickSection(state, onPick, Modifier.padding(vertical = 16.dp)) }
            item(key = "summary") { BatchSummary(state, batch, enabled) }
            item(key = "filters") { BatchFilters(state, batch, enabled) }
            months.forEach { month ->
                item(key = "month-${month.month}") {
                    Text(
                        month.heading.string(),
                        Modifier.padding(top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(month.rows, key = { it.index }) { row -> BatchRow(row, batch, enabled, state.eventSink) }
            }
            item(key = "settings") { BatchSettings(state, batch, enabled, Modifier.padding(vertical = 16.dp)) }
        }
    }
    batch.preview?.let { ItemSheet(it, batch.rows.getOrNull(it.index), enabled, state.eventSink) }
}

/** How many were read, what they came in, and what wasn't read or kept. */
@Composable
private fun BatchSummary(state: ImportActivityState, batch: ImportBatch, enabled: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
        Text(ImportActivityTexts.found(batch).string(), style = MaterialTheme.typography.titleMedium)
        // One note per export, so a Strava and a Garmin export picked together each say what they bring.
        batch.exports.forEach { export ->
            Text(ImportActivityTexts.exportNote(export).string(), style = MaterialTheme.typography.bodyMedium)
        }
        batch.clocks.forEach { file ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (batch.clocks.size > 1) Text(file.fileName, style = MaterialTheme.typography.labelLarge)
                TimeZoneNotice(
                    file.clock,
                    { state.eventSink(ImportActivityEvent.FileZoneChanged(file.key, it)) },
                    enabled = enabled,
                    fileName = file.fileName,
                )
            }
        }
        DuplicatesBanner(
            ImportActivityTexts.batchDuplicateBanner(state, batch),
            state.duplicateCheck,
            onRetry = { state.eventSink(ImportActivityEvent.RetryDuplicateCheck) },
        )
        ImportActivityTexts.skipped(batch)?.let {
            Text(it.string(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        ImportActivityTexts.ignored(batch)?.let {
            Text(it.string(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ImportActivityTexts.originalsTooLarge(batch)?.let { Text(it.string(), style = MaterialTheme.typography.bodySmall) }
    }
}

/** All, New or Duplicates, then how many are selected and Select all or Deselect all for the rows shown. */
@Composable
private fun BatchFilters(state: ImportActivityState, batch: ImportBatch, enabled: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImportActivityTexts.filters.forEach { (filter) ->
                AppFilterChip(
                    selected = batch.filter == filter,
                    onClick = { state.eventSink(ImportActivityEvent.FilterChanged(filter)) },
                    label = { Text(ImportActivityTexts.filter(filter).string()) },
                    enabled = enabled,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(ImportActivityTexts.selected(batch).string(), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            ImportActivityTexts.selectAll(batch)?.let { label ->
                AppTextButton(
                    onClick = { state.eventSink(ImportActivityEvent.AllShownSelected(!batch.allShownSelected)) },
                    enabled = enabled,
                ) {
                    Text(label.string())
                }
            }
        }
        if (ImportActivityTexts.offersBulkActions(batch)) {
            Text(ImportActivityTexts.bulkHeading.string(), style = MaterialTheme.typography.labelLarge)
            // Buttons, not a segmented control: the shown duplicates can each be set differently, so none is "selected".
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImportActivityTexts.bulkChoices.forEach { (action) ->
                    AppSecondaryButton(
                        onClick = { state.eventSink(ImportActivityEvent.ShownDuplicatesAction(action)) },
                        enabled = enabled,
                    ) {
                        Text(actionLabel(action))
                    }
                }
            }
        }
    }
}

/**
 * "Morning Ride", then "May 12, 2024 at 7:02 AM · Ride · 42.1 km · 1:32:10 · Edge 840" and its file; the checkbox selects it, the rest
 * opens it. A duplicate says what it repeats and offers what to do with it.
 */
@Composable
private fun BatchRow(
    row: ImportRow,
    batch: ImportBatch,
    enabled: Boolean,
    eventSink: (ImportActivityEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppCheckbox(row.selected, onCheckedChange = { eventSink(ImportActivityEvent.ItemToggled(row.index)) }, enabled = enabled)
            Column(Modifier.weight(1f).clickable(enabled = enabled) { eventSink(ImportActivityEvent.ItemOpened(row.index)) }) {
                Text(ImportActivityTexts.rowTitle(row).string(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    ImportActivityTexts.rowDetails(row, LocalUnitSystem.current).string(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(row.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ImportActivityTexts.narrowed(row)?.let { Text(it.string(), style = MaterialTheme.typography.bodySmall) }
            }
        }
        row.duplicate?.let { duplicate ->
            DuplicateWarning(
                duplicate,
                onAction = { eventSink(ImportActivityEvent.DuplicateActionChanged(row.index, it)) },
                enabled = enabled,
                modifier = Modifier.padding(start = 36.dp, bottom = 8.dp),
                // The earlier copy by its dive computer's number where it has one, else its place in the batch.
                inFileNumber = ImportActivityTexts.inFileNumber(batch, row),
            )
        }
    }
}

/** The batch's gear, devices and visibility, then Save with its progress. */
@Composable
private fun BatchSettings(state: ImportActivityState, batch: ImportBatch, enabled: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
        Text(
            ImportActivityTexts.detailsHint.string(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // One choice for every activity in the batch.
        state.gear?.let {
            GearPicker(it, enabled = enabled)
            GearConflicts(it.conflicts)
        }
        NewDevicesForm(state.newDevices, enabled = enabled)
        ActivityVisibilityForm(state.visibility, enabled = enabled, heading = Res.string.import_batch_visibility_heading)
        ImportActivityTexts.originalsSize(batch, LocalUiPlatform.current.isMobile)?.let {
            Text(it.string(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Words for each untitled row's default title, worked out once per distinct title.
        val labels = batch.rows.map { it.defaultTitle }.distinct().associateWith { defaultTitleLabel(it) }
        val defaultTitles =
            remember(batch.rows, labels) { ImportActivityTexts.defaultTitles(batch) { labels.getValue(it) }.toImmutableMap() }
        // A save that stopped short, and anything else saving refused, by Save where the athlete is looking (feed/batch-import).
        ImportActivityTexts.saveFailure(state, batch)?.let { Text(it.string(), color = MaterialTheme.colorScheme.error) }
        AppButton(
            onClick = { state.eventSink(ImportActivityEvent.SaveBatch(defaultTitles)) },
            Modifier.fillMaxWidth(),
            enabled = state.canSaveBatch,
        ) {
            Text(ImportActivityTexts.saveBatch(state, batch).string())
        }
    }
}

/** One of the batch's activities opened: its type and title, the only things to change here, then its page as it would publish. */
@Composable
private fun ItemSheet(item: ImportItemPreview, row: ImportRow?, enabled: Boolean, eventSink: (ImportActivityEvent) -> Unit) {
    val close = { eventSink(ImportActivityEvent.ItemClosed) }
    AppBottomSheet(onDismiss = close, sheetState = rememberAppSheetState()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(ImportActivityTexts.itemTitle.string(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            AppTextButton(onClick = close) { Text(ImportActivityTexts.itemDone.string()) }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
        ) {
            ActivityTypePicker(item.type, enabled = enabled)
            AppTextField(
                item.title,
                Modifier.fillMaxWidth(),
                label = ImportActivityTexts.itemTitleLabel.string(),
                placeholder = defaultTitleLabel(item.defaultTitle),
                enabled = enabled,
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            item.error?.let { ErrorText(it) }
            val matched = item.matched
            val duplicate = row?.duplicate
            if (row != null && matched != null && duplicate != null) Comparison(row, matched, duplicate)
            val preview = item.preview
            when {
                preview != null -> {
                    Text(preview.fileName, style = MaterialTheme.typography.titleSmall)
                    PreviewPage(preview, item.page, medium = false)
                }
                item.error == null -> AppProgressIndicator()
            }
        }
    }
}

/** A duplicate beside what it repeats: sport, start, length, distance and device, one column each, and whether it's an exact copy. */
@Composable
private fun Comparison(row: ImportRow, matched: ExistingActivityPreview, duplicate: DuplicateInfo) {
    val units = LocalUnitSystem.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (duplicate.exactCopy) Text(ImportActivityTexts.compareExact.string(), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CompareColumn(
                ImportActivityTexts.compareThis,
                ImportActivityTexts.compareRow(row, units).toImmutableList(),
                Modifier.weight(1f),
            )
            CompareColumn(
                ImportActivityTexts.compareOther(duplicate),
                ImportActivityTexts.compareMatched(matched, units).toImmutableList(),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CompareColumn(heading: AppText, values: ImmutableList<AppText>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(heading.string(), style = MaterialTheme.typography.labelLarge)
        values.forEach { value ->
            Text(value.string(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** How a saved batch uploads: phones keep going in the background (data/outbox), desktop and web only while the app is open. */
@Composable internal fun uploadingNote(): String = ImportActivityTexts.uploadingNote(LocalUiPlatform.current.isMobile).string()
