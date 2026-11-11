package io.github.solcott.activitytracker.app.invites

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.EmptyListText
import io.github.solcott.activitytracker.app.ui.ListNote
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.app.ui.signInAgainItem
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.activity_invites_decline
import io.github.solcott.activitytracker.composeApp.resources.activity_invites_declined_help
import io.github.solcott.activitytracker.composeApp.resources.activity_invites_title
import io.github.solcott.activitytracker.composeApp.resources.activity_invites_view
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.InviteView
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

@CircuitInject(ActivityInvitesScreen::class, SessionScope::class)
@Composable
fun ActivityInvitesUi(state: ActivityInvitesState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val retry = { state.eventSink(ActivityInvitesEvent.Retry) }
    ListScreen(
        title = stringResource(Res.string.activity_invites_title),
        onBack = { state.eventSink(ActivityInvitesEvent.Back) },
        content = state.content,
        errorKind = UiError.Kind.LoadActivityInvites,
        onRetry = retry,
        modifier = modifier,
        listState = listState,
        edgeToEdge = true,
    ) { _ ->
        item(key = "tabs") {
            val tabs = ActivityInvitesTab.entries
            AppSegmentedControl(
                labels = tabs.map { ActivityInvitesTexts.tab(it).string() }.toImmutableList(),
                selected = tabs.indexOf(state.tab),
                onSelect = { state.eventSink(ActivityInvitesEvent.SelectTab(tabs[it])) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item(key = "refresh") {
            RefreshStatus(state.content, UiError.Kind.LoadActivityInvites, retry, Modifier.padding(horizontal = 16.dp))
        }
        signInAgainItem(state.error, state.needsSignIn) { state.eventSink(ActivityInvitesEvent.SignInAgain) }
        val invites = state.invites
        if (state.showsDeclinedNote) {
            item(key = "declinedHelp") { ListNote(stringResource(Res.string.activity_invites_declined_help)) }
        }
        items(invites, key = { it.uri.value }) { invite ->
            InviteRow(invite, state)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (invites.isEmpty()) emptyItem(state.tab)
    }
}

/** An invite: the activity and who added the athlete, with View, and Decline while it's pending. */
@Composable
private fun InviteRow(invite: InviteView, state: ActivityInvitesState) {
    val enabled = state.canAnswer(invite)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        InviteSummary(invite, Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.offersDecline) {
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

private fun LazyListScope.emptyItem(tab: ActivityInvitesTab) {
    item(key = "empty") { EmptyListText(ActivityInvitesTexts.empty(tab).string()) }
}
