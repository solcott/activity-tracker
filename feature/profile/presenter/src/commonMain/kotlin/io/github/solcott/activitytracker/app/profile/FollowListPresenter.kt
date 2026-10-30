package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.FollowRequestsRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.flow

@CircuitInject(FollowListScreen::class, SessionScope::class)
@Composable
fun FollowListPresenter(
    screen: FollowListScreen,
    navigator: Navigator,
    profiles: ProfileRepository,
    followRequests: FollowRequestsRepository,
): FollowListState {
    // A follow can be re-indexed between pages and move; the paging keeps its first occurrence.
    val accounts =
        rememberRetainedPaging(screen, idOf = ProfileViewBasic::did) { cursor ->
            flow {
                emit(
                    when (screen.kind) {
                        FollowListKind.Followers -> profiles.followersPage(screen.actor, cursor)
                        FollowListKind.Following -> profiles.followsPage(screen.actor, cursor)
                    }
                )
            }
        }
    var actionError by rememberRetained { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    val canRemove = rememberCanRemove(screen.kind, accounts.state.meta, profiles)

    return FollowListState(
        kind = screen.kind,
        accounts = accounts.state,
        canRemove = canRemove,
        removing = accounts.state.items.map { it.did }.filter { flights.isRunning(it) }.toImmutableSet(),
        actionError = actionError,
    ) { event ->
        when (event) {
            FollowListEvent.Back -> {
                navigator.pop()
            }

            FollowListEvent.LoadMore -> {
                accounts.loadMore()
            }

            FollowListEvent.Retry -> {
                accounts.retry()
            }

            is FollowListEvent.Open -> {
                navigator.goTo(ProfileScreen(event.did.value))
            }

            is FollowListEvent.Remove -> {
                actionError = null
                flights.launch(event.did) {
                    runSuspendCatching { followRequests.removeFollower(event.did) }
                        .onSuccess { accounts.update { list -> list.filterNot { account -> account.did == event.did } } }
                        .onFailure { actionError = UiError.of(UiError.Kind.RemoveFollower, it) }
                }
            }
        }
    }
}

/** Whether the athlete can remove followers from this list: it's their own followers and they approve followers. */
@Composable
private fun rememberCanRemove(kind: FollowListKind, subject: ProfileViewBasic?, profiles: ProfileRepository): Boolean {
    val ownFollowers = kind == FollowListKind.Followers && subject?.did == profiles.did
    return ownFollowers && rememberApprovesFollowers(profiles, enabled = ownFollowers)
}
