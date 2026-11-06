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
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.AccountActionRow
import io.github.solcott.activitytracker.app.ui.AccountRow
import io.github.solcott.activitytracker.app.ui.EmptyListText
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.app.ui.pagedRows
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.follow_list_followers
import io.github.solcott.activitytracker.composeApp.resources.follow_list_following
import io.github.solcott.activitytracker.composeApp.resources.follow_list_no_followers
import io.github.solcott.activitytracker.composeApp.resources.follow_list_no_following
import io.github.solcott.activitytracker.composeApp.resources.follow_list_remove
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@CircuitInject(FollowListScreen::class, SessionScope::class)
@Composable
fun FollowListUi(state: FollowListState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState, state.accounts.canLoadMore) { state.eventSink(FollowListEvent.LoadMore) }

    ListScreen(
        title = stringResource(state.kind.title),
        onBack = { state.eventSink(FollowListEvent.Back) },
        modifier = modifier,
        listState = listState,
        subtitle = state.subject?.let { handleLabel(it.handle.value) },
        edgeToEdge = true,
    ) {
        state.actionError?.let { error -> item(key = "action-error") { ErrorText(error, Modifier.fillMaxWidth().padding(16.dp)) } }
        pagedRows(
            state.accounts,
            state.kind.loadError,
            onRetry = { state.eventSink(FollowListEvent.Retry) },
            key = { it.did.value },
            empty = { EmptyListText(stringResource(state.kind.emptyText)) },
        ) { account ->
            if (state.canRemove) {
                AccountActionRow(account, onClick = { state.eventSink(FollowListEvent.Open(account.did)) }) {
                    AppSecondaryButton(
                        onClick = { state.eventSink(FollowListEvent.Remove(account.did)) },
                        enabled = account.did !in state.removing,
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

private val FollowListKind.loadError: UiError.Kind
    get() =
        when (this) {
            FollowListKind.Followers -> UiError.Kind.LoadFollowers
            FollowListKind.Following -> UiError.Kind.LoadFollowing
        }

private val FollowListKind.title: StringResource
    get() =
        when (this) {
            FollowListKind.Followers -> Res.string.follow_list_followers
            FollowListKind.Following -> Res.string.follow_list_following
        }

private val FollowListKind.emptyText: StringResource
    get() =
        when (this) {
            FollowListKind.Followers -> Res.string.follow_list_no_followers
            FollowListKind.Following -> Res.string.follow_list_no_following
        }
