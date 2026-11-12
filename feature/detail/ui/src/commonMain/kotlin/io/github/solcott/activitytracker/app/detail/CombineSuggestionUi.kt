package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent.CombineSuggested
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent.DontCombineSuggestion
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent.NotNowSuggestion
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.LocalClock
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.string
import kotlinx.collections.immutable.persistentListOf

@Composable
private fun suggestionMessage(suggestion: CombineSuggestionState): String =
    ActivityPageTexts.combineMessage(suggestion, LocalClock.current.now(), LocalTimeZone.current).string()

/** Asked right after a save: Combine, or Not now, which leaves the suggestion on the page as a banner. */
@Composable
internal fun CombineSuggestionPrompt(suggestion: CombineSuggestionState, eventSink: (ActivityDetailEvent) -> Unit) {
    AppAlertDialog(
        title = ActivityPageTexts.combineCue(suggestion.cue).string(),
        message = suggestionMessage(suggestion),
        actions = persistentListOf(AppDialogAction(ActivityPageTexts.combine.string()) { eventSink(CombineSuggested) }),
        cancelLabel = ActivityPageTexts.notNow.string(),
        // Picking Combine dismisses first too: the suggestion then waits on the page as a banner, busy, until the combined page opens.
        onDismiss = { eventSink(NotNowSuggestion) },
    )
}

/** On the owner's page until they decide: Combine, or Don't combine, which stops suggesting the pair. */
@Composable
internal fun CombineSuggestionBanner(
    suggestion: CombineSuggestionState,
    eventSink: (ActivityDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(ActivityPageTexts.combineCue(suggestion.cue).string(), style = MaterialTheme.typography.titleSmall)
            Text(suggestionMessage(suggestion), style = MaterialTheme.typography.bodyMedium)
            suggestion.error?.let { ErrorText(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(onClick = { eventSink(CombineSuggested) }, enabled = !suggestion.busy) {
                    Text(ActivityPageTexts.combine.string())
                }
                AppTextButton(onClick = { eventSink(DontCombineSuggestion) }, enabled = !suggestion.busy) {
                    Text(ActivityPageTexts.dontCombine.string())
                }
            }
        }
    }
}
