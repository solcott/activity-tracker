package io.github.solcott.activitytracker.app.invites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.mapContent
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.invites.ActivityInvitesRepository
import io.github.solcott.activitytracker.data.invites.Incoming
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.collections.immutable.toImmutableList

@CircuitInject(ActivityInvitesScreen::class, SessionScope::class)
@Composable
fun ActivityInvitesPresenter(navigator: Navigator, invites: ActivityInvitesRepository, auth: AuthRepository): ActivityInvitesState {
    var tab by rememberSaveable { mutableStateOf(ActivityInvitesTab.Pending) }
    var loadKey by remember { mutableIntStateOf(0) }
    // Retained, so coming back from an invite shows the list at once; a retry reloads under it.
    val loaded = produceRetainedContentState<Incoming?>(null, loadKey) { invites.incomingOutcome() }
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()

    // The repository lays answers given on any screen over each load, so an accepted or declined invite moves at once.
    val pending = loaded.data?.pending.orEmpty().toImmutableList()
    val declined = loaded.data?.declined.orEmpty().toImmutableList()
    return ActivityInvitesState(
        tab = tab,
        content = loaded.mapContent { ActivityInvitesContent(pending, declined) },
        needsSignIn = loaded.data?.needsSignIn == true,
        answering = flights.runningAmong(pending.map { it.uri }),
        error = error,
    ) { event ->
        when (event) {
            ActivityInvitesEvent.Back -> {
                navigator.pop()
            }

            is ActivityInvitesEvent.SelectTab -> {
                tab = event.tab
            }

            is ActivityInvitesEvent.Open -> {
                navigator.goTo(AcceptInviteScreen(event.invite.value))
            }

            is ActivityInvitesEvent.Decline -> {
                flights.launch(event.invite) {
                    runSuspendCatching { invites.decline(event.invite) }
                        .onFailure { error = uiErrorOf(UiError.Kind.AnswerActivityInvite, it) }
                }
            }

            ActivityInvitesEvent.Retry -> {
                loadKey++
            }

            ActivityInvitesEvent.SignInAgain -> {
                flights.launch(ActivityInvitesEvent.SignInAgain) { auth.signOut() }
            }
        }
    }
}
