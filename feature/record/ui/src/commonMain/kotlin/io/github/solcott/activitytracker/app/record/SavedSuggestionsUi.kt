package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.detail.FigureValue
import io.github.solcott.activitytracker.app.detail.ITEM_GAP
import io.github.solcott.activitytracker.app.detail.combineCueLabel
import io.github.solcott.activitytracker.app.detail.startTime
import io.github.solcott.activitytracker.app.detail.suggestedActivityLabel
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.ScrollbarBox
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.combine_suggestion_combine
import io.github.solcott.activitytracker.composeApp.resources.import_saved_suggestion_row
import io.github.solcott.activitytracker.composeApp.resources.import_saved_suggestions_done
import io.github.solcott.activitytracker.composeApp.resources.import_saved_suggestions_explained
import io.github.solcott.activitytracker.composeApp.resources.import_saved_suggestions_title
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

/** After a logbook save: the dives that likely belong with activities already logged, each opening its page to combine it. */
@Composable
internal fun SavedSuggestions(saved: ImmutableList<SavedSuggestion>, eventSink: (ImportActivityEvent) -> Unit, padding: PaddingValues) {
    val scroll = rememberScrollState()
    ScrollbarBox(scroll, contentPadding = padding) {
        Column(
            Modifier.verticalScroll(scroll).padding(padding).readableWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
        ) {
            Text(stringResource(Res.string.import_saved_suggestions_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.import_saved_suggestions_explained), style = MaterialTheme.typography.bodyMedium)
            saved.forEach { suggestion ->
                AppCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(combineCueLabel(suggestion.cue), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(
                                Res.string.import_saved_suggestion_row,
                                suggestion.title ?: startTime(FigureValue.StartTime(suggestion.startEpochSeconds, null, dateOnly = false)),
                                suggestedActivityLabel(suggestion.other),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        AppSecondaryButton(onClick = { eventSink(ImportActivityEvent.Saved.Open(suggestion.uri)) }) {
                            Text(stringResource(Res.string.combine_suggestion_combine))
                        }
                    }
                }
            }
            AppButton(onClick = { eventSink(ImportActivityEvent.Saved.Done) }, Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.import_saved_suggestions_done))
            }
        }
    }
}
