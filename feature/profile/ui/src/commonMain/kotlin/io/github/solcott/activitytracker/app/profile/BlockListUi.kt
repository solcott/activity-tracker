package io.github.solcott.activitytracker.app.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.AccountActionRow
import io.github.solcott.activitytracker.app.ui.EmptyListText
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.pagedRows
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.data.di.SessionScope

@CircuitInject(BlockListScreen::class, SessionScope::class)
@Composable
fun BlockListUi(state: BlockListState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState, state.accounts.canLoadMore) { state.eventSink(BlockListEvent.LoadMore) }

    ListScreen(
        title = BlockListTexts.title(state.kind).string(),
        onBack = { state.eventSink(BlockListEvent.Back) },
        modifier = modifier,
        listState = listState,
        edgeToEdge = true,
    ) {
        state.actionError?.let { error -> item(key = "action-error") { ErrorText(error, Modifier.fillMaxWidth().padding(16.dp)) } }
        pagedRows(
            state.accounts,
            state.loadErrorKind,
            onRetry = { state.eventSink(BlockListEvent.Retry) },
            key = { it.profile.did.value },
            empty = { EmptyListText(BlockListTexts.empty(state.kind).string()) },
        ) { account ->
            val did = account.profile.did
            AccountActionRow(
                account.profile,
                onClick = { state.eventSink(BlockListEvent.Open(did)) },
                supporting =
                    BlockListTexts.note(account)?.let { note ->
                        @Composable {
                            Text(
                                note.string(),
                                Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
            ) {
                if (account.removable) {
                    AppSecondaryButton(
                        onClick = { state.eventSink(BlockListEvent.Remove(did)) },
                        enabled = !state.isRemoving(account),
                    ) {
                        Text(BlockListTexts.remove(state.kind).string())
                    }
                }
            }
        }
    }
}
