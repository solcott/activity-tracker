package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.ActivityTypeIcon
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.combine_row
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.activityType
import org.jetbrains.compose.resources.stringResource

@CircuitInject(CombinePickerScreen::class, SessionScope::class)
@Composable
fun CombinePickerUi(state: CombinePickerState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val retry = { state.eventSink(CombinePickerEvent.Retry) }
    ListScreen(
        title = CombinePickerTexts.title.string(),
        onBack = { state.eventSink(CombinePickerEvent.Back) },
        content = state.content,
        errorKind = UiError.Kind.LoadCombineCandidates,
        onRetry = retry,
        modifier = modifier,
        listState = listState,
    ) { candidates ->
        item {
            Text(
                CombinePickerTexts.explained.string(),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { RefreshStatus(state.content, UiError.Kind.LoadCombineCandidates, retry, Modifier.padding(horizontal = 16.dp)) }
        state.error?.let { error -> item { ErrorText(error, Modifier.padding(16.dp)) } }
        if (candidates.isEmpty()) {
            item { Centered { Text(CombinePickerTexts.none.string()) } }
        } else {
            items(candidates, key = { it.uri.value }) { activity ->
                Candidate(
                    activity,
                    combining = CombinePickerTexts.isCombining(state, activity),
                    enabled = CombinePickerTexts.picks(state),
                    onPick = { state.eventSink(CombinePickerTexts.pick(activity)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** One activity to combine with: its type, title, start and length, and its device. */
@Composable
private fun Candidate(activity: ActivityView, combining: Boolean, enabled: Boolean, onPick: () -> Unit, modifier: Modifier = Modifier) {
    val record = activity.record
    Row(
        modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onPick).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActivityTypeIcon(record.activityType, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(CombinePickerTexts.heading(activity).string(), style = MaterialTheme.typography.titleSmall)
            val start = startTime(FigureValue.StartTime(record.startedAt.epochSeconds, record.utcOffset, dateOnly = false))
            Text(
                stringResource(Res.string.combine_row, start, Format.duration(record.elapsedTime)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CombinePickerTexts.device(activity)?.let {
                Text(it.string(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (combining) AppProgressIndicator(Modifier.size(20.dp))
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { content() }
}
