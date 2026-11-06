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
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.AccountActionRow
import io.github.solcott.activitytracker.app.ui.EmptyListText
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.pagedRows
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

    ListScreen(
        title = stringResource(state.kind.title),
        onBack = { state.eventSink(BlockListEvent.Back) },
        modifier = modifier,
        listState = listState,
        edgeToEdge = true,
    ) {
        state.actionError?.let { error -> item(key = "action-error") { ErrorText(error, Modifier.fillMaxWidth().padding(16.dp)) } }
        pagedRows(
            state.accounts,
            state.kind.loadError,
            onRetry = { state.eventSink(BlockListEvent.Retry) },
            key = { it.profile.did.value },
            empty = { EmptyListText(stringResource(state.kind.emptyText)) },
        ) { account ->
            val did = account.profile.did
            AccountActionRow(
                account.profile,
                onClick = { state.eventSink(BlockListEvent.Open(did)) },
                supporting =
                    if (account.onBluesky && !account.removable) {
                        {
                            Text(
                                stringResource(Res.string.blocked_on_bluesky),
                                Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        null
                    },
            ) {
                if (account.removable) {
                    AppSecondaryButton(
                        onClick = { state.eventSink(BlockListEvent.Remove(did)) },
                        enabled = did !in state.removing,
                    ) {
                        Text(stringResource(state.kind.removeLabel))
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
