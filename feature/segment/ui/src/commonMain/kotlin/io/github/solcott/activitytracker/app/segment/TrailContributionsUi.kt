package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChip
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSheetHeader
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.HighlightPlacement
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.highlight
import io.github.solcott.activitytracker.app.ui.adaptive.highlightInset
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.string

// Keeping a segment's trail details true (segments/trail-detail-suggestions): the sheet of one detail, the suggest picker, the quick
// check after a recent ride, and the regular's ways to add a missing detail.

private fun text(name: String, vararg args: AppText) = AppText.named(name, *args)

/** The open sheet or picker of [fields], each a modal sheet; nothing while neither is open. */
@Composable
internal fun TrailContributionSheets(fields: TrailFields) {
    fields.sheet?.let { sheet -> TrailDetailSheet(sheet, fields) }
    fields.picker?.let { picker -> TrailPickerSheet(picker, fields.eventSink) }
}

@Composable
private fun TrailDetailSheet(sheet: TrailSheet, fields: TrailFields) {
    val send = fields.eventSink
    AppBottomSheet(onDismiss = { send(TrailEvent.Close) }, sheetState = rememberAppSheetState()) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(sheet.label.string(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                sheet.value?.let { Text(it.string(), style = MaterialTheme.typography.headlineSmall) }
                sheet.source?.let {
                    Text(it.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (sheet.canConfirm) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text("trail_was_it_right").string(), style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // The chosen answer is the filled one; choosing it again takes it back.
                        Answer(text("trail_still_right"), sheet.stillRight, { send(TrailEvent.Confirm(still = true)) }, Modifier.weight(1f))
                        Answer(text("trail_changed"), sheet.changed, { send(TrailEvent.Confirm(still = false)) }, Modifier.weight(1f))
                    }
                }
            }
            if (sheet.suggestions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text("trail_suggested_heading").string(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    sheet.suggestions.forEach { SuggestionCard(it, send) }
                    Text(
                        text("trail_votes_private").string(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            fields.error?.let { ErrorText(it) }
            if (sheet.canSuggest) {
                AppTextButton(onClick = { send(TrailEvent.Suggest(sheet.key)) }) {
                    PlatformIcon(AppIcon.ManualEntry, null, size = 18.dp)
                    Text(text("trail_suggest_different").string(), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun Answer(label: AppText, chosen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (chosen) AppButton(onClick, modifier) { Text(label.string()) } else AppSecondaryButton(onClick, modifier) { Text(label.string()) }
}

@Composable
private fun SuggestionCard(row: SuggestionRow, send: (TrailEvent) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.value.string(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        row.author.string(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(row.totals.string(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            // Nobody votes on their own suggestion.
            if (!row.own) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFilterChip(
                        selected = row.agreed,
                        onClick = { send(TrailEvent.Vote(row.uri, up = true)) },
                        label = { Text(text("trail_agree").string()) },
                    )
                    AppFilterChip(
                        selected = row.disagreed,
                        onClick = { send(TrailEvent.Vote(row.uri, up = false)) },
                        label = { Text(text("trail_disagree").string()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrailPickerSheet(picker: TrailPicker, send: (TrailEvent) -> Unit) {
    AppBottomSheet(onDismiss = { send(TrailEvent.ClosePicker) }, sheetState = rememberAppSheetState()) {
        AppSheetHeader(
            title = picker.title.string(),
            cancelLabel = text("action_cancel").string(),
            onCancel = { send(TrailEvent.ClosePicker) },
            confirmLabel = text("trail_suggest_send").string(),
            onConfirm = { send(TrailEvent.Send) },
            confirmEnabled = picker.canSend,
        )
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            picker.current?.let { Text(it.string(), Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium) }
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    picker.options.forEachIndexed { index, option ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        PickRow(option, selected = picker.selected == index) { send(TrailEvent.Pick(index)) }
                    }
                }
            }
            Text(
                text("trail_suggest_public").string(),
                Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PickRow(option: TrailPickOption, selected: Boolean, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .highlight(placement = HighlightPlacement.Gutter)
            .selectable(selected = selected, enabled = !option.current, role = Role.RadioButton, onClick = onPick)
            .highlightInset(clickable = !option.current)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                option.text.string(),
                style = MaterialTheme.typography.bodyLarge,
                color = if (option.current) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (option.suggested) {
                Text(
                    text("trail_suggest_already").string(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            option.current ->
                Text(
                    text("trail_suggest_current").string(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            selected -> PlatformIcon(AppIcon.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** The quick check after a recent ride: each detail with Still right and It's changed, then All still right and Not now. */
@Composable
internal fun QuickCheckCard(check: QuickCheck, send: (TrailEvent) -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text("trail_quick_title").string(), style = MaterialTheme.typography.titleMedium)
            Text(
                text("trail_quick_body").string(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            check.rows.forEach { row ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val value = row.value.string()
                Row(Modifier.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    AppIconButton(
                        AppIcon.Check,
                        text("trail_answer_still", row.value).string(),
                        onClick = { send(TrailEvent.Answer(row.key, still = true)) },
                        tint = if (row.stillRight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AppIconButton(
                        AppIcon.Close,
                        text("trail_answer_changed", row.value).string(),
                        onClick = { send(TrailEvent.Answer(row.key, still = false)) },
                        tint = if (row.changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                AppTextButton(onClick = { send(TrailEvent.NotNow) }) { Text(text("trail_quick_later").string()) }
                AppSecondaryButton(onClick = { send(TrailEvent.AllStillRight) }) { Text(text("trail_quick_all").string()) }
            }
        }
    }
}

/**
 * The regular's ways to add details: the invitation in place of the cards when the segment has none ([invite]), else an "Add a missing
 * detail" control under them, each missing detail a chip that opens its picker.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MissingDetails(fields: TrailFields, invite: Boolean, modifier: Modifier = Modifier) {
    if (fields.missing.isEmpty()) return
    val chips: @Composable () -> Unit = {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fields.missing.forEach { detail ->
                AppFilterChip(
                    selected = false,
                    onClick = { fields.eventSink(TrailEvent.Suggest(detail.key)) },
                    label = { Text(detail.label.string()) },
                    leadingIcon = { PlatformIcon(AppIcon.Add, null, size = 16.dp) },
                )
            }
        }
    }
    if (invite) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(TrailTexts.title.string(), style = MaterialTheme.typography.titleMedium)
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text("trail_empty_title").string(), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text("trail_empty_body").string(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    chips()
                }
            }
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text("trail_add_missing").string(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            chips()
        }
    }
}
