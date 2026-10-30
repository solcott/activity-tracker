package io.github.solcott.activitytracker.app.profile

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.AccountRow
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.app.ui.pagedFooter
import io.github.solcott.activitytracker.app.ui.pagedRefreshStatus
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

    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(state.kind.title),
                subtitle = state.subject?.let { handleLabel(it.handle.value) },
                onBack = { state.eventSink(FollowListEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ReadableList(listState, Modifier.fillMaxSize(), padding, minSidePadding = 0.dp) { listPadding ->
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = listPadding + PaddingValues(vertical = 8.dp),
            ) {
                state.actionError?.let { error ->
                    item(key = "action-error") {
                        Text(errorText(error), Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.error)
                    }
                }
                pagedRefreshStatus(state.accounts, state.kind.loadError) { state.eventSink(FollowListEvent.Retry) }
                items(state.accounts.items, key = { it.did.value }) { account ->
                    if (state.canRemove) {
                        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            AccountRow(account, onClick = { state.eventSink(FollowListEvent.Open(account.did)) }, Modifier.weight(1f))
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                pagedFooter(state.accounts, state.kind.loadError, onRetry = { state.eventSink(FollowListEvent.Retry) }) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(state.kind.emptyText))
                    }
                }
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
