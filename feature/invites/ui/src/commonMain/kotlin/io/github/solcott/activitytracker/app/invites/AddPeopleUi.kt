package io.github.solcott.activitytracker.app.invites

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.AccountActionRow
import io.github.solcott.activitytracker.app.ui.ListNote
import io.github.solcott.activitytracker.app.ui.ListScreen
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.signInAgainItem
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.add_people_add
import io.github.solcott.activitytracker.composeApp.resources.add_people_added
import io.github.solcott.activitytracker.composeApp.resources.add_people_followers
import io.github.solcott.activitytracker.composeApp.resources.add_people_full
import io.github.solcott.activitytracker.composeApp.resources.add_people_no_followers
import io.github.solcott.activitytracker.composeApp.resources.add_people_notice
import io.github.solcott.activitytracker.composeApp.resources.add_people_title
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.stringResource

@CircuitInject(AddPeopleScreen::class, SessionScope::class)
@Composable
fun AddPeopleUi(state: AddPeopleState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val retry = { state.eventSink(AddPeopleEvent.Retry) }
    ListScreen(
        title = stringResource(Res.string.add_people_title),
        onBack = { state.eventSink(AddPeopleEvent.Back) },
        content = state.content,
        errorKind = UiError.Kind.LoadInvitees,
        onRetry = retry,
        modifier = modifier,
        listState = listState,
        edgeToEdge = true,
    ) { content ->
        item(key = "refresh") { RefreshStatus(state.content, UiError.Kind.LoadInvitees, retry, Modifier.padding(horizontal = 16.dp)) }
        item(key = "notice") { ListNote(stringResource(Res.string.add_people_notice)) }
        signInAgainItem(state.error, needsSignIn = !state.canInvite) { state.eventSink(AddPeopleEvent.SignInAgain) }
        if (content.invitees.isNotEmpty()) item(key = "addedHeading") { Heading(stringResource(Res.string.add_people_added)) }
        items(content.invitees, key = { "invitee:" + it.invitee.did.value }) { invitee ->
            AccountActionRow(invitee.invitee, onClick = {}) {
                Status(AddPeopleTexts.status(invitee).string())
                AppSecondaryButton(onClick = { state.eventSink(AddPeopleEvent.Remove(invitee)) }, enabled = state.canRemove(invitee)) {
                    Text(AddPeopleTexts.remove(invitee).string())
                }
            }
        }
        item(key = "followersHeading") { Heading(stringResource(Res.string.add_people_followers)) }
        if (state.full) item(key = "full") { ListNote(stringResource(Res.string.add_people_full)) }
        if (content.followers.isEmpty()) item(key = "noFollowers") { ListNote(stringResource(Res.string.add_people_no_followers)) }
        items(content.followers, key = { "follower:" + it.did.value }) { follower ->
            AccountActionRow(follower, onClick = {}) {
                AppButton(
                    onClick = { state.eventSink(AddPeopleEvent.Add(follower.did)) },
                    enabled = state.canAdd(follower),
                ) {
                    Text(stringResource(Res.string.add_people_add))
                }
            }
        }
    }
}

/** An invitee's status, before its action. */
@Composable
private fun Status(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 8.dp),
    )
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
}
