package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.launchAction
import io.github.solcott.activitytracker.app.mapContent
import io.github.solcott.activitytracker.app.rememberReload
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.FollowRequests
import io.github.solcott.activitytracker.data.profile.FollowRequestsRepository
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

@CircuitInject(FollowRequestsScreen::class, SessionScope::class)
@Composable
fun FollowRequestsPresenter(navigator: Navigator, answers: FollowRequestsRepository, auth: AuthRepository): FollowRequestsState {
    var tab by rememberSaveable { mutableStateOf(FollowRequestsTab.Requests) }
    val reload = rememberReload()
    // Retained, so coming back from a profile shows the requests at once; a retry reloads under them.
    val loaded = produceRetainedContentState<FollowRequests?>(null, reload.key) { answers.followRequestsOutcome() }
    // Answers given here, laid over every load for as long as the screen lives: the AppView lists a request as pending until it catches up.
    var approved by rememberRetained { mutableStateOf(emptySet<Did>()) }
    var declinedHere by rememberRetained { mutableStateOf(emptyList<ProfileViewBasic>()) }
    // Why answering failed; never a loading failure, which is [loaded]'s.
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()

    val answered = approved + declinedHere.map { it.did }
    val requests = loaded.data?.pending.orEmpty().filterNot { it.did in answered }.toImmutableList()
    val declined = (declinedHere + loaded.data?.declined.orEmpty().filterNot { it.did in answered }).toImmutableList()
    val all = requests + declined
    return FollowRequestsState(
        tab = tab,
        content = loaded.mapContent { FollowRequestsContent(requests, declined, truncated = it?.truncated == true) },
        // Without the decline scope everyone shows as a request; declining needs the athlete to sign in again.
        needsSignIn = loaded.data?.needsSignIn == true,
        answering = flights.runningAmong(all.map { it.did }),
        unanswerable = all.map { it.did }.filterNot { answers.canAnswer(it) }.toImmutableSet(),
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
                flights.launchAction(event.did, UiError.Kind.AnswerFollowRequest, { error = it }) {
                    answers.approve(event.did, declined = wasDeclined)
                    approved = approved + event.did
                    declinedHere = declinedHere.filterNot { declined -> declined.did == event.did }
                }
            }

            is FollowRequestsEvent.Decline -> {
                flights.launchAction(event.did, UiError.Kind.AnswerFollowRequest, { error = it }) {
                    answers.decline(event.did)
                    val account = requests.firstOrNull { request -> request.did == event.did }
                    if (account != null) {
                        approved = approved - event.did
                        declinedHere = listOf(account) + declinedHere.filterNot { declined -> declined.did == event.did }
                    }
                }
            }

            is FollowRequestsEvent.Open -> {
                navigator.goTo(ProfileScreen(event.did.value))
            }

            FollowRequestsEvent.Retry -> {
                reload.retry()
            }

            FollowRequestsEvent.SignInAgain -> {
                flights.launch(FollowRequestsEvent.SignInAgain) { auth.signInAgain() }
            }
        }
    }
}
