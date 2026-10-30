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
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.combine_suggestion_combine
import io.github.solcott.activitytracker.composeApp.resources.combine_suggestion_dont
import io.github.solcott.activitytracker.composeApp.resources.combine_suggestion_member_message
import io.github.solcott.activitytracker.composeApp.resources.combine_suggestion_message
import io.github.solcott.activitytracker.composeApp.resources.combine_suggestion_not_now
import io.github.solcott.activitytracker.lexicon.app.activityType
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

@Composable
private fun suggestionMessage(suggestion: CombineSuggestionState): String {
    val record = suggestion.other.record
    val start = startTime(FigureValue.StartTime(record.startedAt.epochSeconds, record.utcOffset, dateOnly = false))
    val name = record.title ?: activityTypeLabel(record.activityType)
    return stringResource(
        if (record.combined != null) Res.string.combine_suggestion_member_message else Res.string.combine_suggestion_message,
        name,
        start,
    )
}

/** Asked right after a save: Combine, or Not now, which leaves the suggestion on the page as a banner. */
@Composable
internal fun CombineSuggestionPrompt(suggestion: CombineSuggestionState, eventSink: (ActivityDetailEvent) -> Unit) {
    AppAlertDialog(
        title = combineCueLabel(suggestion.cue),
        message = suggestionMessage(suggestion),
        actions = persistentListOf(AppDialogAction(stringResource(Res.string.combine_suggestion_combine)) { eventSink(CombineSuggested) }),
        cancelLabel = stringResource(Res.string.combine_suggestion_not_now),
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
            Text(combineCueLabel(suggestion.cue), style = MaterialTheme.typography.titleSmall)
            Text(suggestionMessage(suggestion), style = MaterialTheme.typography.bodyMedium)
            suggestion.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(onClick = { eventSink(CombineSuggested) }, enabled = !suggestion.busy) {
                    Text(stringResource(Res.string.combine_suggestion_combine))
                }
                AppTextButton(onClick = { eventSink(DontCombineSuggestion) }, enabled = !suggestion.busy) {
                    Text(stringResource(Res.string.combine_suggestion_dont))
                }
            }
        }
    }
}
