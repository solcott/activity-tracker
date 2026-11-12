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
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.string
import kotlinx.collections.immutable.toImmutableList

/**
 * Why an activity looks like a duplicate ("Already in your log: Blue Hole · 2024-05-12 10:02 · Garmin Descent Mk3") and what to do with it.
 * [inFileNumber] numbers the activity [DuplicateInfo.inBatchIndex] names, as the list shows it. Combine is left out, with the reason, while
 * the logged activity is still uploading. What it says is `ImportActivityTexts`'.
 */
@Composable
internal fun DuplicateWarning(
    info: DuplicateInfo,
    onAction: (DuplicateAction) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    inFileNumber: Int? = info.inBatchIndex?.plus(1),
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            ImportActivityTexts.duplicateWarning(info, inFileNumber).string(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        val actions = ImportActivityTexts.duplicateChoices(info).map { it.action }
        AppSegmentedControl(
            actions.map { actionLabel(it) }.toImmutableList(),
            selected = actions.indexOf(info.action),
            onSelect = { onAction(actions[it]) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
        ImportActivityTexts.duplicateExplained(info)?.let {
            Text(it.string(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A single file's activity: the search of the athlete's log while it runs, then what to do if the activity is already there. */
@Composable
internal fun PreviewDuplicate(state: ImportActivityState, preview: ImportPreview, modifier: Modifier = Modifier) {
    DuplicatesBanner(
        ImportActivityTexts.duplicateBanner(state),
        state.duplicateCheck,
        modifier,
        onRetry = { state.eventSink(ImportActivityEvent.RetryDuplicateCheck) },
    )
    preview.duplicate?.let { duplicate ->
        DuplicateWarning(duplicate, { state.eventSink(ImportActivityEvent.DuplicateActionChanged(0, it)) }, !state.saving, modifier)
    }
}

/**
 * Above a logbook's dives, or a single file's preview: [banner] (how many are duplicates, or that the search for them is running or
 * failed), with [onRetry] to search again after a failure (feed/import-duplicates).
 */
@Composable
internal fun DuplicatesBanner(banner: AppText?, check: DuplicateCheck, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    if (banner == null) return
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium).padding(12.dp)) {
        Text(banner.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        if (ImportActivityTexts.offersRetry(check) && onRetry != null) {
            AppTextButton(onClick = onRetry) { Text(ImportActivityTexts.retry.string()) }
        }
    }
}

@Composable internal fun actionLabel(action: DuplicateAction): String = ImportActivityTexts.action(action).string()
