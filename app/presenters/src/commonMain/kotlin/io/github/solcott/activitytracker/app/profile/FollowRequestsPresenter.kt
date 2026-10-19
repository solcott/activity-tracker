package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.FollowRequestsRepository
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

@CircuitInject(FollowRequestsScreen::class, SessionScope::class)
@Composable
fun FollowRequestsPresenter(navigator: Navigator, answers: FollowRequestsRepository, auth: AuthRepository): FollowRequestsState {
    var tab by rememberSaveable { mutableStateOf(FollowRequestsTab.Requests) }
    var requests by remember { mutableStateOf<ImmutableList<ProfileViewBasic>>(persistentListOf()) }
    var declined by remember { mutableStateOf<ImmutableList<ProfileViewBasic>>(persistentListOf()) }
    var truncated by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var loadKey by remember { mutableIntStateOf(0) }
    val flights = rememberSingleFlight()

    LaunchedEffect(loadKey) {
        isLoading = true
        runSuspendCatching { answers.followRequests() }
            .onSuccess { loaded ->
                requests = loaded.pending.toImmutableList()
                declined = loaded.declined.toImmutableList()
                truncated = loaded.truncated
                // Without the decline scope everyone shows as a request; declining needs the athlete to sign in again.
                error = if (loaded.needsSignIn) UiError(UiError.Kind.SignInAgain, null) else null
            }
            .onFailure { error = UiError.of(UiError.Kind.LoadFollowRequests, it) }
        isLoading = false
    }

    val all = requests + declined
    return FollowRequestsState(
        tab = tab,
        requests = requests,
        declined = declined,
        truncated = truncated,
        answering = all.map { it.did }.filter { flights.isRunning(it) }.toImmutableSet(),
        unanswerable = all.map { it.did }.filterNot { answers.canAnswer(it) }.toImmutableSet(),
        isLoading = isLoading,
        error = error,
    ) { event ->
        when (event) {
            FollowRequestsEvent.Back -> {
                navigator.pop()
            }

            is FollowRequestsEvent.SelectTab -> {
                tab = event.tab
            }

            is FollowRequestsEvent.Approve -> {
                val wasDeclined = declined.any { it.did == event.did }
                flights.launch(event.did) {
                    runSuspendCatching { answers.approve(event.did, declined = wasDeclined) }
                        .onSuccess {
                            requests = requests.without(event.did)
                            declined = declined.without(event.did)
                        }
                        .onFailure { error = uiErrorOf(UiError.Kind.AnswerFollowRequest, it) }
                }
            }

            is FollowRequestsEvent.Decline -> {
                flights.launch(event.did) {
                    runSuspendCatching { answers.decline(event.did) }
                        .onSuccess {
                            val account = requests.firstOrNull { request -> request.did == event.did }
                            requests = requests.without(event.did)
                            if (account != null) declined = (listOf(account) + declined).toImmutableList()
                        }
                        .onFailure { error = uiErrorOf(UiError.Kind.AnswerFollowRequest, it) }
                }
            }

            is FollowRequestsEvent.Open -> {
                navigator.goTo(ProfileScreen(event.did.value))
            }

            FollowRequestsEvent.Retry -> {
                loadKey++
            }

            FollowRequestsEvent.SignInAgain -> {
                flights.launch(FollowRequestsEvent.SignInAgain) { auth.signOut() }
            }
        }
    }
}

private fun ImmutableList<ProfileViewBasic>.without(did: Did): ImmutableList<ProfileViewBasic> = filterNot {
    it.did == did
}
    .toImmutableList()
