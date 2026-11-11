package io.github.solcott.activitytracker.app.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.AccountActionRow
import io.github.solcott.activitytracker.app.ui.AccountRow
import io.github.solcott.activitytracker.app.ui.EmptyListText
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.pagedRows
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.follow_list_remove
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.stringResource

@CircuitInject(FollowListScreen::class, SessionScope::class)
@Composable
fun FollowListUi(state: FollowListState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState, state.accounts.canLoadMore) { state.eventSink(FollowListEvent.LoadMore) }

    ListScreen(
        title = FollowListTexts.title(state.kind).string(),
        onBack = { state.eventSink(FollowListEvent.Back) },
        modifier = modifier,
        listState = listState,
        subtitle = FollowListTexts.subtitle(state.subject)?.string(),
        edgeToEdge = true,
    ) {
        state.actionError?.let { error -> item(key = "action-error") { ErrorText(error, Modifier.fillMaxWidth().padding(16.dp)) } }
        pagedRows(
            state.accounts,
            state.loadErrorKind,
            onRetry = { state.eventSink(FollowListEvent.Retry) },
            key = { it.did.value },
            empty = { EmptyListText(FollowListTexts.empty(state.kind).string()) },
        ) { account ->
            if (state.canRemove) {
                AccountActionRow(account, onClick = { state.eventSink(FollowListEvent.Open(account.did)) }) {
                    AppSecondaryButton(
                        onClick = { state.eventSink(FollowListEvent.Remove(account.did)) },
                        enabled = !state.isRemoving(account),
                    ) {
                        Text(stringResource(Res.string.follow_list_remove))
                    }
                }
            } else {
                AccountRow(account, onClick = { state.eventSink(FollowListEvent.Open(account.did)) })
            }
        }
    }
}
