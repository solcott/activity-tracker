package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.detail.ITEM_GAP
import io.github.solcott.activitytracker.app.ui.LocalClock
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import kotlinx.collections.immutable.ImmutableList

/**
 * After a batch save: how many were saved and skipped ([result]), then the activities that likely belong with ones already logged
 * ([saved]), each opening its page to combine it.
 */
@Composable
internal fun SavedSuggestions(
    result: BatchSaved?,
    saved: ImmutableList<SavedSuggestion>?,
    eventSink: (ImportActivityEvent) -> Unit,
    padding: PaddingValues,
) {
    ReadableScrollColumn(padding, verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
        result?.let { BatchSavedSummary(it) }
        if (!saved.isNullOrEmpty()) {
            Text(ImportActivityTexts.suggestionsTitle.string(), style = MaterialTheme.typography.titleMedium)
            Text(ImportActivityTexts.suggestionsExplained.string(), style = MaterialTheme.typography.bodyMedium)
        }
        saved.orEmpty().forEach { suggestion ->
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ImportActivityTexts.suggestionCue(suggestion).string(), style = MaterialTheme.typography.titleSmall)
                    Text(
                        ImportActivityTexts.suggestionRow(suggestion, LocalClock.current.now(), LocalTimeZone.current).string(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    AppSecondaryButton(onClick = { eventSink(ImportActivityEvent.Saved.Open(suggestion.uri)) }) {
                        Text(ImportActivityTexts.suggestionCombine.string())
                    }
                }
            }
        }
        if (result != null) {
            AppSecondaryButton(onClick = { eventSink(ImportActivityEvent.Saved.ViewUploads) }, Modifier.fillMaxWidth()) {
                Text(ImportActivityTexts.viewUploads.string())
            }
        }
        AppButton(onClick = { eventSink(ImportActivityEvent.Saved.Done) }, Modifier.fillMaxWidth()) {
            Text(ImportActivityTexts.savedDone.string())
        }
    }
}

/** "40 activities saved.", and how many couldn't be read again, and how they upload. */
@Composable
private fun BatchSavedSummary(result: BatchSaved) {
    Column(verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
        Text(ImportActivityTexts.batchSavedTitle.string(), style = MaterialTheme.typography.titleMedium)
        Text(ImportActivityTexts.batchSaved(result, LocalUiPlatform.current.isMobile).string(), style = MaterialTheme.typography.bodyMedium)
    }
}
