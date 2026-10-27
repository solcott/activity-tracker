package io.github.solcott.activitytracker.app.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.sportLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.action_retry
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_activity
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_activity_named
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_block
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_comment
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_comment_hide
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_discard
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_discard_message
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_discard_named
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_discard_title
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_like
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_mute
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_other
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_refused
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_retry_named
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_segment
import io.github.solcott.activitytracker.composeApp.resources.unsent_change_segment_star
import io.github.solcott.activitytracker.composeApp.resources.unsent_changes_empty
import io.github.solcott.activitytracker.composeApp.resources.unsent_changes_title
import io.github.solcott.activitytracker.data.activity.RejectedChange
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/** The changes the athlete's server refused, each named, with Retry and Discard; discarding asks first. */
@CircuitInject(UnsentChangesScreen::class, SessionScope::class)
@Composable
fun UnsentChangesUi(state: UnsentChangesState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.unsent_changes_title),
                onBack = { state.eventSink(UnsentChangesEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val changes = state.changes ?: return@AppScaffold
        val listState = rememberLazyListState()
        ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding + PaddingValues(vertical = 8.dp)) {
                state.error?.let { error ->
                    item(key = "error") {
                        Text(errorText(error), Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error)
                    }
                }
                if (changes.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(Res.string.unsent_changes_empty),
                            Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(changes, key = { it.rkey }) { change ->
                    ChangeRow(
                        change,
                        busy = change.rkey in state.busy,
                        onRetry = { state.eventSink(UnsentChangesEvent.Retry(change.rkey)) },
                        onDiscard = { state.eventSink(UnsentChangesEvent.Discard(change.rkey)) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    state.confirmingDiscard?.let { change ->
        AppAlertDialog(
            title = stringResource(Res.string.unsent_change_discard_title),
            message = stringResource(Res.string.unsent_change_discard_message),
            actions =
                persistentListOf(
                    AppDialogAction(stringResource(Res.string.unsent_change_discard), destructive = true) {
                        state.eventSink(UnsentChangesEvent.ConfirmDiscard(change.rkey))
                    }
                ),
            cancelLabel = stringResource(Res.string.action_cancel),
            onDismiss = { state.eventSink(UnsentChangesEvent.CancelDiscard) },
        )
    }
}

@Composable
private fun ChangeRow(change: RejectedChange, busy: Boolean, onRetry: () -> Unit, onDiscard: () -> Unit) {
    val name = changeName(change)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Res.string.unsent_change_refused),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val retryDescription = stringResource(Res.string.unsent_change_retry_named, name)
        AppTextButton(onClick = onRetry, Modifier.semantics { contentDescription = retryDescription }, enabled = !busy) {
            Text(stringResource(Res.string.action_retry))
        }
        val discardDescription = stringResource(Res.string.unsent_change_discard_named, name)
        AppTextButton(onClick = onDiscard, Modifier.semantics { contentDescription = discardDescription }, enabled = !busy) {
            Text(stringResource(Res.string.unsent_change_discard))
        }
    }
}

/** An activity by its title, or its sport and day; anything else by its kind. */
@Composable
private fun changeName(change: RejectedChange): String =
    when (change.kind) {
        RejectedChange.Kind.Activity -> {
            val title = change.title
            val sport = change.sport
            val startedAt = change.startedAt
            when {
                title != null -> title
                sport != null && startedAt != null ->
                    stringResource(
                        Res.string.unsent_change_activity_named,
                        sportLabel(sport),
                        startedAt.toLocalDateTime(LocalTimeZone.current).date.toString(),
                    )
                else -> stringResource(Res.string.unsent_change_activity)
            }
        }
        RejectedChange.Kind.Like -> stringResource(Res.string.unsent_change_like)
        RejectedChange.Kind.Comment -> stringResource(Res.string.unsent_change_comment)
        RejectedChange.Kind.CommentHide -> stringResource(Res.string.unsent_change_comment_hide)
        RejectedChange.Kind.Block -> stringResource(Res.string.unsent_change_block)
        RejectedChange.Kind.Mute -> stringResource(Res.string.unsent_change_mute)
        RejectedChange.Kind.Segment -> stringResource(Res.string.unsent_change_segment)
        RejectedChange.Kind.SegmentStar -> stringResource(Res.string.unsent_change_segment_star)
        RejectedChange.Kind.Other -> stringResource(Res.string.unsent_change_other)
    }
