package io.github.solcott.activitytracker.app.invites

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.mapContent
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.rememberNewActivityVisibility
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityDefaults
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.invites.ActivityInvitesRepository
import io.github.solcott.activitytracker.data.invites.copyOf
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.GetActivityInvite
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
    var loadKey by remember { mutableIntStateOf(0) }
    val loaded = produceRetainedContentState<GetActivityInvite.Output?>(null, loadKey) { invites.inviteOutcome(uri) }
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
                    flights.launch(AcceptInviteEvent.Accept) {
                        error = null
                        runSuspendCatching { invites.accept(current, visibility.current, title.text.toString()) }
                            .onSuccess { navigator.pop() }
                            .onFailure { error = uiErrorOf(UiError.Kind.AnswerActivityInvite, it) }
                    }
            }

            AcceptInviteEvent.Decline -> {
                flights.launch(AcceptInviteEvent.Decline) {
                    error = null
                    runSuspendCatching { invites.decline(uri) }
                        .onSuccess { navigator.pop() }
                        .onFailure { error = uiErrorOf(UiError.Kind.AnswerActivityInvite, it) }
                }
            }

            AcceptInviteEvent.Retry -> {
                loadKey++
            }

            AcceptInviteEvent.SignInAgain -> {
                flights.launch(AcceptInviteEvent.SignInAgain) { auth.signInAgain() }
            }
        }
    }
}
