package io.github.solcott.activitytracker.app.invites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.launchAction
import io.github.solcott.activitytracker.app.mapContent
import io.github.solcott.activitytracker.app.rememberReload
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.rememberNewActivityVisibility
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityDefaults
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.invites.ActivityInvitesRepository
import io.github.solcott.activitytracker.data.invites.copyOf
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.GetActivityInvite
import io.github.solcott.activitytracker.textfield.rememberTextFieldState
import io.github.solcott.activitytracker.textfield.setTextAndPlaceCursorAtEnd
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.collections.immutable.toImmutableSet

@CircuitInject(AcceptInviteScreen::class, SessionScope::class)
@Composable
fun AcceptInvitePresenter(
    screen: AcceptInviteScreen,
    navigator: Navigator,
    invites: ActivityInvitesRepository,
    defaults: ActivityDefaults,
    auth: AuthRepository,
): AcceptInviteState {
    val uri = AtUri(screen.invite)
    val reload = rememberReload()
    val loaded = produceRetainedContentState<GetActivityInvite.Output?>(null, reload.key) { invites.inviteOutcome(uri) }
    val invite = loaded.data
    val title = rememberTextFieldState()
    var overlaps by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    // Only the categories the copy holds data in are offered.
    val offered = invite?.let { copyOf(it, null).heldCategories() }.orEmpty().toImmutableSet()
    val visibility = rememberNewActivityVisibility(defaults, offered)

    LaunchedEffect(invite?.invite?.uri) {
        val record = invite?.invite?.record ?: return@LaunchedEffect
        if (title.text.isEmpty()) record.title?.let { title.setTextAndPlaceCursorAtEnd(it) }
        overlaps = runSuspendCatching { invites.overlaps(record) }.getOrDefault(false)
    }

    return AcceptInviteState(
        content = loaded.mapContent { it?.invite },
        title = title,
        visibility = visibility.fields,
        overlaps = overlaps,
        accepting = flights.isRunning(AcceptInviteEvent.Accept),
        declining = flights.isRunning(AcceptInviteEvent.Decline),
        canDecline = invites.canDecline,
        error = error,
    ) { event ->
        when (event) {
            AcceptInviteEvent.Back -> {
                navigator.pop()
            }

            AcceptInviteEvent.Accept -> {
                val current = invite
                if (current != null)
                    flights.launchAction(AcceptInviteEvent.Accept, UiError.Kind.AnswerActivityInvite, { error = it }) {
                        error = null
                        invites.accept(current, visibility.current, title.text.toString())
                        navigator.pop()
                    }
            }

            AcceptInviteEvent.Decline -> {
                flights.launchAction(AcceptInviteEvent.Decline, UiError.Kind.AnswerActivityInvite, { error = it }) {
                    error = null
                    invites.decline(uri)
                    navigator.pop()
                }
            }

            AcceptInviteEvent.Retry -> {
                reload.retry()
            }

            AcceptInviteEvent.SignInAgain -> {
                flights.launch(AcceptInviteEvent.SignInAgain) { auth.signInAgain() }
            }
        }
    }
}
