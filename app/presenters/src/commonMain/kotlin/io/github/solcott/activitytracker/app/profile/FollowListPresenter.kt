package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@CircuitInject(FollowListScreen::class, SessionScope::class)
@Composable
fun FollowListPresenter(
    screen: FollowListScreen,
    navigator: Navigator,
    profiles: ProfileRepository,
): FollowListState {
    var subject by remember { mutableStateOf<ProfileViewBasic?>(null) }
    var accounts by remember { mutableStateOf<ImmutableList<ProfileViewBasic>>(persistentListOf()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var loadKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

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
                    scope.launch { load(after) }
                }
            }

            FollowListEvent.Retry -> {
                loadKey++
            }

            is FollowListEvent.Open -> {
                navigator.goTo(ProfileScreen(event.did.value))
            }
        }
    }
}

private class Page(val subject: ProfileViewBasic, val accounts: List<ProfileViewBasic>, val cursor: String?)

private suspend fun ProfileRepository.page(screen: FollowListScreen, cursor: String?): Page =
    when (screen.kind) {
        FollowListKind.Followers -> followers(screen.actor, cursor).let { Page(it.subject, it.followers, it.cursor) }
        FollowListKind.Following -> follows(screen.actor, cursor).let { Page(it.subject, it.follows, it.cursor) }
    }
