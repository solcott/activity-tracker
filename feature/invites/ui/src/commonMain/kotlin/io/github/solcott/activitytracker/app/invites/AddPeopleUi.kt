package io.github.solcott.activitytracker.app.invites

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.AccountActionRow
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.ListNote
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.signInAgainItem
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.add_people_add
import io.github.solcott.activitytracker.composeApp.resources.add_people_added
import io.github.solcott.activitytracker.composeApp.resources.add_people_cancel
import io.github.solcott.activitytracker.composeApp.resources.add_people_followers
import io.github.solcott.activitytracker.composeApp.resources.add_people_full
import io.github.solcott.activitytracker.composeApp.resources.add_people_joined
import io.github.solcott.activitytracker.composeApp.resources.add_people_no_followers
import io.github.solcott.activitytracker.composeApp.resources.add_people_notice
import io.github.solcott.activitytracker.composeApp.resources.add_people_pending
import io.github.solcott.activitytracker.composeApp.resources.add_people_remove
import io.github.solcott.activitytracker.composeApp.resources.add_people_title
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.InviteStatus
import org.jetbrains.compose.resources.stringResource

@CircuitInject(AddPeopleScreen::class, SessionScope::class)
@Composable
fun AddPeopleUi(state: AddPeopleState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.add_people_title),
                onBack = { state.eventSink(AddPeopleEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val retry = { state.eventSink(AddPeopleEvent.Retry) }
        ContentStateLayout(state.content, UiError.Kind.LoadInvitees, retry, contentPadding = padding) { content ->
            ReadableList(listState, Modifier.fillMaxSize(), padding, minSidePadding = 0.dp) { listPadding ->
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding + PaddingValues(vertical = 8.dp)) {
                    item(key = "refresh") {
                        RefreshStatus(state.content, UiError.Kind.LoadInvitees, retry, Modifier.padding(horizontal = 16.dp))
                    }
                    item(key = "notice") { ListNote(stringResource(Res.string.add_people_notice)) }
                    signInAgainItem(state.error, needsSignIn = !state.canInvite) { state.eventSink(AddPeopleEvent.SignInAgain) }
                    if (content.invitees.isNotEmpty()) item(key = "addedHeading") { Heading(stringResource(Res.string.add_people_added)) }
                    items(content.invitees, key = { "invitee:" + it.invitee.did.value }) { invitee ->
                        val joined = invitee.status == InviteStatus.JOINED
                        AccountActionRow(invitee.invitee, onClick = {}) {
                            Status(stringResource(if (joined) Res.string.add_people_joined else Res.string.add_people_pending))
                            AppSecondaryButton(
                                onClick = { state.eventSink(AddPeopleEvent.Remove(invitee)) },
                                enabled = state.canInvite && invitee.invitee.did !in state.busy,
                            ) {
                                Text(stringResource(if (joined) Res.string.add_people_remove else Res.string.add_people_cancel))
                            }
                        }
                    }
                    item(key = "followersHeading") { Heading(stringResource(Res.string.add_people_followers)) }
                    if (content.full) item(key = "full") { ListNote(stringResource(Res.string.add_people_full)) }
                    if (content.followers.isEmpty())
                        item(key = "noFollowers") { ListNote(stringResource(Res.string.add_people_no_followers)) }
                    items(content.followers, key = { "follower:" + it.did.value }) { follower ->
                        AccountActionRow(follower, onClick = {}) {
                            AppButton(
                                onClick = { state.eventSink(AddPeopleEvent.Add(follower.did)) },
                                enabled = state.canInvite && !content.full && follower.did !in state.busy,
                            ) {
                                Text(stringResource(Res.string.add_people_add))
                            }
                        }
                    }
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
