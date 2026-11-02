package io.github.solcott.activitytracker.app.invites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_sign_in_again
import io.github.solcott.activitytracker.composeApp.resources.activity_invites_decline
import io.github.solcott.activitytracker.composeApp.resources.activity_invites_declined_help
import io.github.solcott.activitytracker.composeApp.resources.activity_invites_none
import io.github.solcott.activitytracker.composeApp.resources.activity_invites_none_declined
import io.github.solcott.activitytracker.composeApp.resources.activity_invites_tab_declined
import io.github.solcott.activitytracker.composeApp.resources.activity_invites_tab_pending
import io.github.solcott.activitytracker.composeApp.resources.activity_invites_title
import io.github.solcott.activitytracker.composeApp.resources.activity_invites_view
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.InviteView
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

@CircuitInject(ActivityInvitesScreen::class, SessionScope::class)
@Composable
fun ActivityInvitesUi(state: ActivityInvitesState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.activity_invites_title),
                onBack = { state.eventSink(ActivityInvitesEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val retry = { state.eventSink(ActivityInvitesEvent.Retry) }
        ContentStateLayout(state.content, UiError.Kind.LoadActivityInvites, retry, contentPadding = padding) { content ->
            ReadableList(listState, Modifier.fillMaxSize(), padding, minSidePadding = 0.dp) { listPadding ->
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding + PaddingValues(vertical = 8.dp)) {
                    item(key = "tabs") {
                        val tabs = ActivityInvitesTab.entries
                        AppSegmentedControl(
                            labels =
                                persistentListOf(
                                    stringResource(Res.string.activity_invites_tab_pending),
                                    stringResource(Res.string.activity_invites_tab_declined),
                                ),
                            selected = tabs.indexOf(state.tab),
                            onSelect = { state.eventSink(ActivityInvitesEvent.SelectTab(tabs[it])) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    item(key = "refresh") {
                        RefreshStatus(state.content, UiError.Kind.LoadActivityInvites, retry, Modifier.padding(horizontal = 16.dp))
                    }
                    errorItem(state)
                    val invites = if (state.tab == ActivityInvitesTab.Pending) content.pending else content.declined
                    if (state.tab == ActivityInvitesTab.Declined && invites.isNotEmpty()) {
                        item(key = "declinedHelp") { Note(stringResource(Res.string.activity_invites_declined_help)) }
                    }
                    items(invites, key = { it.uri.value }) { invite ->
                        InviteRow(invite, state)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (invites.isEmpty()) emptyItem(state.tab)
                }
            }
        }
    }
}

/** An invite: the activity and who added the athlete, with View, and Decline while it's pending. */
@Composable
private fun InviteRow(invite: InviteView, state: ActivityInvitesState) {
    val enabled = invite.uri !in state.answering
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        InviteSummary(invite, Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.tab == ActivityInvitesTab.Pending && !state.needsSignIn) {
                AppSecondaryButton(onClick = { state.eventSink(ActivityInvitesEvent.Decline(invite.uri)) }, enabled = enabled) {
                    Text(stringResource(Res.string.activity_invites_decline))
                }
            }
            AppButton(onClick = { state.eventSink(ActivityInvitesEvent.Open(invite.uri)) }, enabled = enabled) {
                Text(stringResource(Res.string.activity_invites_view))
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Sign in again for a session that can't decline, and why declining failed. */
private fun LazyListScope.errorItem(state: ActivityInvitesState) {
    val error = state.error
    if (!state.needsSignIn && error == null) return
    item(key = "error") {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(errorText(error ?: UiError(UiError.Kind.SignInAgain)), color = MaterialTheme.colorScheme.error)
            if (state.needsSignIn || error?.kind == UiError.Kind.SignInAgain) {
                AppSecondaryButton(onClick = { state.eventSink(ActivityInvitesEvent.SignInAgain) }) {
                    Text(stringResource(Res.string.action_sign_in_again))
                }
            }
        }
    }
}

private fun LazyListScope.emptyItem(tab: ActivityInvitesTab) {
    item(key = "empty") {
        val text = if (tab == ActivityInvitesTab.Pending) Res.string.activity_invites_none else Res.string.activity_invites_none_declined
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text(stringResource(text)) }
    }
}
