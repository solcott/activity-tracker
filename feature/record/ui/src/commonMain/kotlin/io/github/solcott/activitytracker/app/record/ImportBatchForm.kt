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
import io.github.solcott.activitytracker.activityfile.ExportKind
import io.github.solcott.activitytracker.activityfile.SkipReason
import io.github.solcott.activitytracker.app.detail.ITEM_GAP
import io.github.solcott.activitytracker.app.settings.monthName
import io.github.solcott.activitytracker.app.ui.ActivityVisibilityForm
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
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
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_saving
import io.github.solcott.activitytracker.composeApp.resources.dive_number
import io.github.solcott.activitytracker.composeApp.resources.import_batch_found
import io.github.solcott.activitytracker.composeApp.resources.import_batch_garmin
import io.github.solcott.activitytracker.composeApp.resources.import_batch_garmin_no_privacy
import io.github.solcott.activitytracker.composeApp.resources.import_batch_ignored
import io.github.solcott.activitytracker.composeApp.resources.import_batch_month
import io.github.solcott.activitytracker.composeApp.resources.import_batch_narrowed_followers
import io.github.solcott.activitytracker.composeApp.resources.import_batch_narrowed_self
import io.github.solcott.activitytracker.composeApp.resources.import_batch_originals_size
import io.github.solcott.activitytracker.composeApp.resources.import_batch_originals_too_large
import io.github.solcott.activitytracker.composeApp.resources.import_batch_save
import io.github.solcott.activitytracker.composeApp.resources.import_batch_save_failed
import io.github.solcott.activitytracker.composeApp.resources.import_batch_saving
import io.github.solcott.activitytracker.composeApp.resources.import_batch_skipped
import io.github.solcott.activitytracker.composeApp.resources.import_batch_strava
import io.github.solcott.activitytracker.composeApp.resources.import_batch_strava_no_privacy
import io.github.solcott.activitytracker.composeApp.resources.import_batch_uploading
import io.github.solcott.activitytracker.composeApp.resources.import_batch_uploading_mobile
import io.github.solcott.activitytracker.composeApp.resources.import_batch_visibility_heading
import io.github.solcott.activitytracker.composeApp.resources.import_bulk_duplicates
import io.github.solcott.activitytracker.composeApp.resources.import_compare_exact
import io.github.solcott.activitytracker.composeApp.resources.import_compare_in_import
import io.github.solcott.activitytracker.composeApp.resources.import_compare_logged
import io.github.solcott.activitytracker.composeApp.resources.import_compare_this
import io.github.solcott.activitytracker.composeApp.resources.import_deselect_all
import io.github.solcott.activitytracker.composeApp.resources.import_dives_details_hint
import io.github.solcott.activitytracker.composeApp.resources.import_dives_none
import io.github.solcott.activitytracker.composeApp.resources.import_dives_selected
import io.github.solcott.activitytracker.composeApp.resources.import_filter_all
import io.github.solcott.activitytracker.composeApp.resources.import_filter_duplicates
import io.github.solcott.activitytracker.composeApp.resources.import_filter_new
import io.github.solcott.activitytracker.composeApp.resources.import_item_done
import io.github.solcott.activitytracker.composeApp.resources.import_item_title
import io.github.solcott.activitytracker.composeApp.resources.import_item_title_label
import io.github.solcott.activitytracker.composeApp.resources.import_megabytes
import io.github.solcott.activitytracker.composeApp.resources.import_select_all
import io.github.solcott.activitytracker.composeApp.resources.import_skip_damaged
import io.github.solcott.activitytracker.composeApp.resources.import_skip_too_large
import io.github.solcott.activitytracker.composeApp.resources.import_skip_unsupported
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Several activities to import: what was read, then one row per activity grouped by month (a lazy list, for an account export's thousands),
 * then the choices for the whole batch and Save. Tapping a row opens it to look at and retitle.
 */
@Composable
internal fun BatchContent(state: ImportActivityState, batch: ImportBatch, padding: PaddingValues, onPick: () -> Unit) {
    val listState = rememberLazyListState()
    val enabled = !state.saving
    val shown = batch.shown
    val months = remember(shown) { shown.groupBy { it.month } }
    ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding) {
            item(key = "pick") { PickSection(state, onPick, Modifier.padding(vertical = 16.dp)) }
            item(key = "summary") { BatchSummary(state, batch, enabled) }
            item(key = "filters") { BatchFilters(state, batch, enabled) }
            months.forEach { (month, rows) ->
                item(key = "month-$month") {
                    Text(
                        stringResource(Res.string.import_batch_month, monthName(month.month.ordinal + 1), month.year),
                        Modifier.padding(top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(rows, key = { it.index }) { row -> BatchRow(row, batch, enabled, state.eventSink) }
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
        Text(
            pluralStringResource(Res.plurals.import_batch_found, batch.rows.size, formatWhole(batch.rows.size)),
            style = MaterialTheme.typography.titleMedium,
        )
        // One note per export, so a Strava and a Garmin export picked together each say what they bring.
        batch.exports.forEach { export ->
            Text(
                stringResource(
                    when (export.kind) {
                        ExportKind.Strava if export.privacy -> Res.string.import_batch_strava
                        ExportKind.Strava -> Res.string.import_batch_strava_no_privacy
                        ExportKind.Garmin if export.privacy -> Res.string.import_batch_garmin
                        ExportKind.Garmin -> Res.string.import_batch_garmin_no_privacy
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
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
            state.duplicateCheck,
            batch.existingDuplicates,
            batch.inBatchDuplicates,
            copies = batch.exactCopies,
            onRetry = { state.eventSink(ImportActivityEvent.RetryDuplicateCheck) },
        )
        if (batch.skipped.isNotEmpty()) {
            val names = batch.skipped.map { skippedLabel(it) }
            Text(
                pluralStringResource(Res.plurals.import_batch_skipped, batch.skipped.size, formatWhole(batch.skipped.size)) +
                    " " +
                    names.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (batch.ignoredFiles > 0) {
            Text(
                pluralStringResource(Res.plurals.import_batch_ignored, batch.ignoredFiles, formatWhole(batch.ignoredFiles)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (batch.originalsTooLarge.isNotEmpty()) {
            Text(
                stringResource(Res.string.import_batch_originals_too_large, batch.originalsTooLarge.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun skippedLabel(file: SkippedFile): String =
    stringResource(
        when (file.reason) {
            SkipReason.Unsupported -> Res.string.import_skip_unsupported
            SkipReason.Damaged -> Res.string.import_skip_damaged
            SkipReason.TooLarge -> Res.string.import_skip_too_large
        },
        file.name,
    )

/** All, New or Duplicates, then how many are selected and Select all or Deselect all for the rows shown. */
@Composable
private fun BatchFilters(state: ImportActivityState, batch: ImportBatch, enabled: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BatchFilter.entries.forEach { filter ->
                AppFilterChip(
                    selected = batch.filter == filter,
                    onClick = { state.eventSink(ImportActivityEvent.FilterChanged(filter)) },
                    label = {
                        Text(
                            stringResource(
                                when (filter) {
                                    BatchFilter.All -> Res.string.import_filter_all
                                    BatchFilter.New -> Res.string.import_filter_new
                                    BatchFilter.Duplicates -> Res.string.import_filter_duplicates
                                }
                            )
                        )
                    },
                    enabled = enabled,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.import_dives_selected, formatWhole(batch.selectedCount), formatWhole(batch.rows.size)),
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
            )
            if (batch.selectable.isNotEmpty()) {
                AppTextButton(
                    onClick = { state.eventSink(ImportActivityEvent.AllShownSelected(!batch.allShownSelected)) },
                    enabled = enabled,
                ) {
                    Text(stringResource(if (batch.allShownSelected) Res.string.import_deselect_all else Res.string.import_select_all))
                }
            }
        }
        if (batch.filter == BatchFilter.Duplicates && batch.shown.isNotEmpty()) {
            Text(stringResource(Res.string.import_bulk_duplicates), style = MaterialTheme.typography.labelLarge)
            // Buttons, not a segmented control: the shown duplicates can each be set differently, so none is "selected".
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DuplicateAction.entries.forEach { action ->
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
 * "Morning Ride", then "2024-05-12 07:02 · Ride · 42.1 km · 1:32:10 · Edge 840" and its file; the checkbox selects it, the rest opens it. A
 * duplicate says what it repeats and offers what to do with it.
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
                Text(row.title ?: defaultTitleLabel(row.defaultTitle), style = MaterialTheme.typography.bodyLarge)
                val details =
                    listOfNotNull(
                        row.start,
                        activityTypeLabel(row.type),
                        row.dive?.maxDepthMeters?.let { Format.depth(it) } ?: row.distanceMeters?.let { Format.distance(it) },
                        Format.duration(row.elapsed),
                        row.dive?.diveNumber?.let { stringResource(Res.string.dive_number, it) },
                        row.device,
                    )
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(row.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                row.narrowedTo?.let { narrowed ->
                    Text(
                        stringResource(
                            if (narrowed == Visibility.SELF) Res.string.import_batch_narrowed_self
                            else Res.string.import_batch_narrowed_followers
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        row.duplicate?.let { duplicate ->
            DuplicateWarning(
                duplicate,
                onAction = { eventSink(ImportActivityEvent.DuplicateActionChanged(row.index, it)) },
                enabled = enabled,
                modifier = Modifier.padding(start = 36.dp, bottom = 8.dp),
                // The earlier copy by its dive computer's number where it has one, else its place in the batch.
                inFileNumber = duplicate.inBatchIndex?.let { batch.rows.getOrNull(it)?.dive?.diveNumber ?: (it + 1) },
            )
        }
    }
}

/** The batch's gear, devices and visibility, then Save with its progress. */
@Composable
private fun BatchSettings(state: ImportActivityState, batch: ImportBatch, enabled: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
        Text(
            stringResource(Res.string.import_dives_details_hint),
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
        if (batch.originalsBytes > 0) {
            Text(
                stringResource(
                    Res.string.import_batch_originals_size,
                    stringResource(Res.string.import_megabytes, formatWhole((batch.originalsBytes + BYTES_PER_MB - 1) / BYTES_PER_MB)),
                ) + " " + uploadingNote(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Words for each untitled row's default title, worked out once per distinct title.
        val untitled = remember(batch.rows) { batch.rows.filter { it.title == null } }
        val labels = untitled.map { it.defaultTitle }.distinct().associateWith { defaultTitleLabel(it) }
        val defaultTitles = untitled.associate { it.index to labels.getValue(it.defaultTitle) }.toImmutableMap()
        val progress = batch.progress
        // A save that stopped short, and anything else saving refused, by Save where the athlete is looking (feed/batch-import).
        val failure = batch.saveFailure
        if (failure != null) {
            Text(
                stringResource(
                    Res.string.import_batch_save_failed,
                    formatWhole(failure.saved),
                    formatWhole(failure.total),
                    errorText(failure.error),
                ),
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
        }
        AppButton(
            onClick = { state.eventSink(ImportActivityEvent.SaveBatch(defaultTitles)) },
            Modifier.fillMaxWidth(),
            enabled = enabled && !state.reading && batch.selectedCount > 0 && state.duplicateCheck != DuplicateCheck.Checking,
        ) {
            Text(
                when {
                    progress != null ->
                        stringResource(Res.string.import_batch_saving, formatWhole(progress.queued), formatWhole(progress.total))
                    state.saving -> stringResource(Res.string.action_saving)
                    batch.selectedCount == 0 -> stringResource(Res.string.import_dives_none)
                    else -> pluralStringResource(Res.plurals.import_batch_save, batch.selectedCount, formatWhole(batch.selectedCount))
                }
            )
        }
    }
}

/** One of the batch's activities opened: its type and title, the only things to change here, then its page as it would publish. */
@Composable
private fun ItemSheet(item: ImportItemPreview, row: ImportRow?, enabled: Boolean, eventSink: (ImportActivityEvent) -> Unit) {
    val close = { eventSink(ImportActivityEvent.ItemClosed) }
    AppBottomSheet(onDismiss = close, sheetState = rememberAppSheetState()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.import_item_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            AppTextButton(onClick = close) { Text(stringResource(Res.string.import_item_done)) }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
        ) {
            ActivityTypePicker(item.type, enabled = enabled)
            AppTextField(
                item.title,
                Modifier.fillMaxWidth(),
                label = stringResource(Res.string.import_item_title_label),
                placeholder = defaultTitleLabel(item.defaultTitle),
                enabled = enabled,
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            item.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (duplicate.exactCopy) Text(stringResource(Res.string.import_compare_exact), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CompareColumn(
                stringResource(Res.string.import_compare_this),
                persistentListOf(
                    activityTypeLabel(row.type),
                    row.start,
                    Format.duration(row.elapsed),
                    row.distanceMeters?.let { Format.distance(it) },
                    row.device,
                ),
                Modifier.weight(1f),
            )
            CompareColumn(
                stringResource(if (duplicate.existing != null) Res.string.import_compare_logged else Res.string.import_compare_in_import),
                persistentListOf(
                    matched.type?.let { activityTypeLabel(it) },
                    matched.start,
                    matched.elapsed?.let { Format.duration(it) },
                    matched.distanceMeters?.let { Format.distance(it) },
                    matched.device,
                ),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CompareColumn(heading: String, values: ImmutableList<String?>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(heading, style = MaterialTheme.typography.labelLarge)
        values.forEach { value ->
            Text(value ?: "–", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val BYTES_PER_MB = 1024L * 1024

/** How a saved batch uploads: phones keep going in the background (data/outbox), desktop and web only while the app is open. */
@Composable
internal fun uploadingNote(): String =
    stringResource(if (LocalUiPlatform.current.isMobile) Res.string.import_batch_uploading_mobile else Res.string.import_batch_uploading)
