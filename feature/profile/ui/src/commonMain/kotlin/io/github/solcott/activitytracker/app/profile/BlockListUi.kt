package io.github.solcott.activitytracker.app.profile

import androidx.compose.foundation.layout.Box
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
import io.github.solcott.activitytracker.app.ui.pagedFooter
import io.github.solcott.activitytracker.app.ui.pagedRefreshStatus
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_unblock
import io.github.solcott.activitytracker.composeApp.resources.action_unmute
import io.github.solcott.activitytracker.composeApp.resources.blocked_accounts_empty
import io.github.solcott.activitytracker.composeApp.resources.blocked_on_bluesky
import io.github.solcott.activitytracker.composeApp.resources.muted_accounts_empty
import io.github.solcott.activitytracker.composeApp.resources.settings_blocked_accounts
import io.github.solcott.activitytracker.composeApp.resources.settings_muted_accounts
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@CircuitInject(BlockListScreen::class, SessionScope::class)
@Composable
fun BlockListUi(state: BlockListState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState, state.accounts.canLoadMore) { state.eventSink(BlockListEvent.LoadMore) }

    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(state.kind.title),
                onBack = { state.eventSink(BlockListEvent.Back) },
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
                pagedRefreshStatus(state.accounts, state.kind.loadError) { state.eventSink(BlockListEvent.Retry) }
                items(state.accounts.items, key = { it.profile.did.value }) { account ->
                    val did = account.profile.did
                    Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            AccountRow(account.profile, onClick = { state.eventSink(BlockListEvent.Open(did)) })
                            if (account.onBluesky && !account.removable) {
                                Text(
                                    stringResource(Res.string.blocked_on_bluesky),
                                    Modifier.padding(horizontal = 16.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (account.removable) {
                            AppSecondaryButton(
                                onClick = { state.eventSink(BlockListEvent.Remove(did)) },
                                enabled = did !in state.removing,
                            ) {
                                Text(stringResource(state.kind.removeLabel))
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                pagedFooter(state.accounts, state.kind.loadError, onRetry = { state.eventSink(BlockListEvent.Retry) }) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(state.kind.emptyText))
                    }
                }
            }
        }
    }
}

private val BlockListKind.loadError: UiError.Kind
    get() =
        when (this) {
            BlockListKind.Blocked -> UiError.Kind.LoadBlocks
            BlockListKind.Muted -> UiError.Kind.LoadMutes
        }

private val BlockListKind.title: StringResource
    get() =
        when (this) {
            BlockListKind.Blocked -> Res.string.settings_blocked_accounts
            BlockListKind.Muted -> Res.string.settings_muted_accounts
        }

private val BlockListKind.emptyText: StringResource
    get() =
        when (this) {
            BlockListKind.Blocked -> Res.string.blocked_accounts_empty
            BlockListKind.Muted -> Res.string.muted_accounts_empty
        }

private val BlockListKind.removeLabel: StringResource
    get() =
        when (this) {
            BlockListKind.Blocked -> Res.string.action_unblock
            BlockListKind.Muted -> Res.string.action_unmute
        }
