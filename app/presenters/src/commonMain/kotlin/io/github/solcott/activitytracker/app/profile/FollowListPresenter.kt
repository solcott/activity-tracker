package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.CursorPage
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberCursorPaging
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.FollowRequestsRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import kotlinx.collections.immutable.toImmutableSet

@CircuitInject(FollowListScreen::class, SessionScope::class)
@Composable
fun FollowListPresenter(
    screen: FollowListScreen,
    navigator: Navigator,
    profiles: ProfileRepository,
    followRequests: FollowRequestsRepository,
): FollowListState {
    var subject by remember { mutableStateOf<ProfileViewBasic?>(null) }
    val accounts =
        rememberCursorPaging(screen, idOf = ProfileViewBasic::did) { cursor ->
            // A follow can be re-indexed between pages and move; the paging keeps its first occurrence.
            profiles.page(screen, cursor).let {
                subject = it.subject
                CursorPage(it.accounts, it.cursor)
            }
        }
    var removeError by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    val canRemove = rememberCanRemove(screen.kind, subject, profiles)
    val loadError =
        accounts.failure?.let {
            UiError.of(
                when (screen.kind) {
                    FollowListKind.Followers -> UiError.Kind.LoadFollowers
                    FollowListKind.Following -> UiError.Kind.LoadFollowing
                },
                it,
            )
        }

    return FollowListState(
        kind = screen.kind,
        subject = subject,
        accounts = accounts.items,
        isLoading = accounts.isLoading,
        // A failed removal stops paging too, until Retry.
        canLoadMore = accounts.canLoadMore && removeError == null,
        canRemove = canRemove,
        removing = accounts.items.map { it.did }.filter { flights.isRunning(it) }.toImmutableSet(),
        error = removeError ?: loadError,
    ) { event ->
        when (event) {
            FollowListEvent.Back -> {
                navigator.pop()
            }

            FollowListEvent.LoadMore -> {
                if (removeError == null) accounts.loadMore()
            }

            FollowListEvent.Retry -> {
                removeError = null
                accounts.retry()
            }

            is FollowListEvent.Open -> {
                navigator.goTo(ProfileScreen(event.did.value))
            }

            is FollowListEvent.Remove -> {
                flights.launch(event.did) {
                    runSuspendCatching { followRequests.removeFollower(event.did) }
                        .onSuccess { accounts.update { list -> list.filterNot { account -> account.did == event.did } } }
                        .onFailure { removeError = UiError.of(UiError.Kind.RemoveFollower, it) }
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

private class Page(val subject: ProfileViewBasic, val accounts: List<ProfileViewBasic>, val cursor: String?)

private suspend fun ProfileRepository.page(screen: FollowListScreen, cursor: String?): Page =
    when (screen.kind) {
        FollowListKind.Followers -> followers(screen.actor, cursor).let { Page(it.subject, it.followers, it.cursor) }
        FollowListKind.Following -> follows(screen.actor, cursor).let { Page(it.subject, it.follows, it.cursor) }
    }
