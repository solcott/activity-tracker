package io.github.solcott.activitytracker.app.invites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.mapContent
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.invites.ActivityInvitesRepository
import io.github.solcott.activitytracker.data.invites.People
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.InviteeView
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.collections.immutable.toImmutableList

@CircuitInject(AddPeopleScreen::class, SessionScope::class)
@Composable
fun AddPeoplePresenter(
    screen: AddPeopleScreen,
    navigator: Navigator,
    invites: ActivityInvitesRepository,
    auth: AuthRepository,
): AddPeopleState {
    val activity = AtUri(screen.activity)
    var loadKey by remember { mutableIntStateOf(0) }
    val loaded = produceRetainedContentState<People?>(null, loadKey) { invites.peopleOutcome(activity) }
    // Changes made here, laid over every load: the AppView catches up with the athlete's `self` space a moment later.
    var added by rememberRetained { mutableStateOf(emptyList<InviteeView>()) }
    var removed by rememberRetained { mutableStateOf(emptySet<Did>()) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()

    val addedDids = added.map { it.invitee.did }.toSet()
    val invitees =
        (loaded.data?.invitees.orEmpty().filterNot { it.invitee.did in addedDids } + added)
            .filterNot { it.invitee.did in removed }
            .toImmutableList()
    val invited = invitees.map { it.invitee.did }.toSet()
    val followers = loaded.data?.followers.orEmpty().filterNot { it.did in invited }.toImmutableList()
    return AddPeopleState(
        content = loaded.mapContent { AddPeopleContent(invitees, followers) },
        canInvite = invites.canInvite,
        busy = flights.runningAmong(invited + followers.map { it.did }),
        error = error,
    ) { event ->
        when (event) {
            AddPeopleEvent.Back -> {
                navigator.pop()
            }

            is AddPeopleEvent.Add -> {
                val follower = followers.firstOrNull { it.did == event.did }
                if (follower != null)
                    flights.launch(event.did) {
                        error = null
                        runSuspendCatching { invites.add(activity, follower) }
                            .onSuccess { invitee ->
                                removed = removed - event.did
                                added = added.filterNot { other -> other.invitee.did == event.did } + invitee
                            }
                            .onFailure { error = uiErrorOf(UiError.Kind.UpdateInvitees, it) }
                    }
            }

            is AddPeopleEvent.Remove -> {
                val did = event.invitee.invitee.did
                flights.launch(did) {
                    error = null
                    runSuspendCatching { invites.cancel(event.invitee.uri) }
                        .onSuccess {
                            added = added.filterNot { invitee -> invitee.invitee.did == did }
                            removed = removed + did
                        }
                        .onFailure { error = uiErrorOf(UiError.Kind.UpdateInvitees, it) }
                }
            }

            AddPeopleEvent.Retry -> {
                loadKey++
            }

            AddPeopleEvent.SignInAgain -> {
                flights.launch(AddPeopleEvent.SignInAgain) { auth.signInAgain() }
            }
        }
    }
}
