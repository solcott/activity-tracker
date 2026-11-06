package io.github.solcott.activitytracker.app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.AccountActionRow
import io.github.solcott.activitytracker.app.ui.EmptyListText
import io.github.solcott.activitytracker.app.ui.ListNote
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.app.ui.signInAgainItem
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.follow_requests_approve
import io.github.solcott.activitytracker.composeApp.resources.follow_requests_decline
import io.github.solcott.activitytracker.composeApp.resources.follow_requests_declined_help
import io.github.solcott.activitytracker.composeApp.resources.follow_requests_none
import io.github.solcott.activitytracker.composeApp.resources.follow_requests_none_declined
import io.github.solcott.activitytracker.composeApp.resources.follow_requests_tab_declined
import io.github.solcott.activitytracker.composeApp.resources.follow_requests_tab_requests
import io.github.solcott.activitytracker.composeApp.resources.follow_requests_title
import io.github.solcott.activitytracker.composeApp.resources.follow_requests_truncated
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

@CircuitInject(FollowRequestsScreen::class, SessionScope::class)
@Composable
fun FollowRequestsUi(state: FollowRequestsState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val retry = { state.eventSink(FollowRequestsEvent.Retry) }
    ListScreen(
        title = stringResource(Res.string.follow_requests_title),
        onBack = { state.eventSink(FollowRequestsEvent.Back) },
        content = state.content,
        errorKind = UiError.Kind.LoadFollowRequests,
        onRetry = retry,
        modifier = modifier,
        listState = listState,
        edgeToEdge = true,
    ) { content ->
        item(key = "tabs") {
            val tabs = FollowRequestsTab.entries
            AppSegmentedControl(
                labels =
                    persistentListOf(
                        stringResource(Res.string.follow_requests_tab_requests),
                        stringResource(Res.string.follow_requests_tab_declined),
                    ),
                selected = tabs.indexOf(state.tab),
                onSelect = { state.eventSink(FollowRequestsEvent.SelectTab(tabs[it])) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item(key = "refresh") { RefreshStatus(state.content, UiError.Kind.LoadFollowRequests, retry, Modifier.padding(horizontal = 16.dp)) }
        signInAgainItem(state.error, state.needsSignIn) { state.eventSink(FollowRequestsEvent.SignInAgain) }
        val accounts = if (state.tab == FollowRequestsTab.Requests) content.requests else content.declined
        if (state.tab == FollowRequestsTab.Declined && accounts.isNotEmpty()) {
            item(key = "declinedHelp") { ListNote(stringResource(Res.string.follow_requests_declined_help)) }
        }
        items(accounts, key = { it.did.value }) { account ->
            RequestRow(account, state)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (state.tab == FollowRequestsTab.Requests && content.truncated) {
            item(key = "truncated") { ListNote(stringResource(Res.string.follow_requests_truncated)) }
        }
        if (accounts.isEmpty()) emptyItem(state.tab)
    }
}

/** An account with Approve, and Decline when it's still a request. */
@Composable
private fun RequestRow(account: ProfileViewBasic, state: FollowRequestsState) {
    val enabled = account.did !in state.answering && account.did !in state.unanswerable
    AccountActionRow(account, onClick = { state.eventSink(FollowRequestsEvent.Open(account.did)) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.tab == FollowRequestsTab.Requests) {
                AppSecondaryButton(onClick = { state.eventSink(FollowRequestsEvent.Decline(account.did)) }, enabled = enabled) {
                    Text(stringResource(Res.string.follow_requests_decline))
                }
            }
            AppButton(onClick = { state.eventSink(FollowRequestsEvent.Approve(account.did)) }, enabled = enabled) {
                Text(stringResource(Res.string.follow_requests_approve))
            }
        }
    }
}

/** The empty state for [tab]. */
private fun LazyListScope.emptyItem(tab: FollowRequestsTab) {
    item(key = "empty") {
        val text = if (tab == FollowRequestsTab.Requests) Res.string.follow_requests_none else Res.string.follow_requests_none_declined
        EmptyListText(stringResource(text))
    }
}
