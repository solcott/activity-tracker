package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ActivityTypeIcon
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_retry
import io.github.solcott.activitytracker.composeApp.resources.combine_explained
import io.github.solcott.activitytracker.composeApp.resources.combine_none
import io.github.solcott.activitytracker.composeApp.resources.combine_row
import io.github.solcott.activitytracker.composeApp.resources.combine_title
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.activityType
import org.jetbrains.compose.resources.stringResource

@CircuitInject(CombinePickerScreen::class, SessionScope::class)
@Composable
fun CombinePickerUi(state: CombinePickerState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.combine_title),
                onBack = { state.eventSink(CombinePickerEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding + PaddingValues(vertical = 8.dp)) {
                item {
                    Text(
                        stringResource(Res.string.combine_explained),
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.error?.let { error ->
                    item {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(errorText(error), color = MaterialTheme.colorScheme.error)
                            AppTextButton(onClick = { state.eventSink(CombinePickerEvent.Retry) }) {
                                Text(stringResource(Res.string.action_retry))
                            }
                        }
                    }
                }
                val candidates = state.candidates
                when {
                    candidates == null -> item { Centered { AppProgressIndicator() } }
                    candidates.isEmpty() -> item { Centered { Text(stringResource(Res.string.combine_none)) } }
                    else ->
                        items(candidates, key = { it.uri.value }) { activity ->
                            Candidate(
                                activity,
                                combining = state.combining == activity.uri,
                                enabled = state.combining == null,
                                onPick = { state.eventSink(CombinePickerEvent.Pick(activity.uri)) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                }
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
            Text(record.title ?: activityTypeLabel(record.activityType), style = MaterialTheme.typography.titleSmall)
            val start = startTime(FigureValue.StartTime(record.startedAt.epochSeconds, record.utcOffset, dateOnly = false))
            Text(
                stringResource(Res.string.combine_row, start, Format.duration(record.elapsedTime)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            record.device?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (combining) AppProgressIndicator(Modifier.size(20.dp))
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { content() }
}
