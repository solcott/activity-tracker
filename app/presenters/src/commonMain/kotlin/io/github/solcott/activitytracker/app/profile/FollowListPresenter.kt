package io.github.solcott.activitytracker.app.profile

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
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.FollowRequestsRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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
    var accounts by remember { mutableStateOf<ImmutableList<ProfileViewBasic>>(persistentListOf()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var loadKey by remember { mutableIntStateOf(0) }
    val flights = rememberSingleFlight()
    val canRemove = rememberCanRemove(screen.kind, subject, profiles)

    // Callers set isLoading before launching, so a second LoadMore can't start the same page twice.
    suspend fun load(after: String?) {
        runSuspendCatching { profiles.page(screen, after) }
            .onSuccess { page ->
                subject = page.subject
                // A follow can be re-indexed between pages and move; keep the first occurrence.
                accounts = (if (after == null) page.accounts else (accounts + page.accounts).distinctBy { it.did }).toImmutableList()
                cursor = page.cursor
                error = null
            }
            .onFailure {
                error =
                    UiError.of(
                        when (screen.kind) {
                            FollowListKind.Followers -> UiError.Kind.LoadFollowers
                            FollowListKind.Following -> UiError.Kind.LoadFollowing
                        },
                        it,
                    )
            }
        isLoading = false
    }

    LaunchedEffect(loadKey) {
        isLoading = true
        load(after = null)
    }

    return FollowListState(
        kind = screen.kind,
        subject = subject,
        accounts = accounts,
        isLoading = isLoading,
        canLoadMore = cursor != null && error == null,
        canRemove = canRemove,
        removing = accounts.map { it.did }.filter { flights.isRunning(it) }.toImmutableSet(),
        error = error,
    ) { event ->
        when (event) {
            FollowListEvent.Back -> {
                navigator.pop()
            }

            FollowListEvent.LoadMore -> {
                val after = cursor
                if (!isLoading && after != null && error == null) {
                    isLoading = true
                    flights.launch(FollowListEvent.LoadMore) { load(after) }
                }
            }

            FollowListEvent.Retry -> {
                loadKey++
            }

            is FollowListEvent.Open -> {
                navigator.goTo(ProfileScreen(event.did.value))
            }

            is FollowListEvent.Remove -> {
                flights.launch(event.did) {
                    runSuspendCatching { followRequests.removeFollower(event.did) }
                        .onSuccess { accounts = accounts.filterNot { account -> account.did == event.did }.toImmutableList() }
                        .onFailure { error = UiError.of(UiError.Kind.RemoveFollower, it) }
                }
            }
        }
    }
}

/**
 * Whether the athlete can remove followers from this list: it's their own followers and they approve followers. Their policy comes from
 * their record, since the AppView may lag a change they just made.
 */
@Composable
private fun rememberCanRemove(kind: FollowListKind, subject: ProfileViewBasic?, profiles: ProfileRepository): Boolean {
    val ownFollowers = kind == FollowListKind.Followers && subject?.did == profiles.did
    var approves by remember { mutableStateOf(false) }
    LaunchedEffect(ownFollowers) {
        if (ownFollowers)
            approves = runSuspendCatching { profiles.ownProfileRecord()?.followPolicy == FollowPolicy.APPROVE }.getOrDefault(false)
    }
    return ownFollowers && approves
}

private class Page(val subject: ProfileViewBasic, val accounts: List<ProfileViewBasic>, val cursor: String?)

private suspend fun ProfileRepository.page(screen: FollowListScreen, cursor: String?): Page =
    when (screen.kind) {
        FollowListKind.Followers -> followers(screen.actor, cursor).let { Page(it.subject, it.followers, it.cursor) }
        FollowListKind.Following -> follows(screen.actor, cursor).let { Page(it.subject, it.follows, it.cursor) }
    }
