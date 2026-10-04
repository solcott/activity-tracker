package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@CircuitInject(ProfileScreen::class, SessionScope::class)
@Composable
fun ProfilePresenter(
    screen: ProfileScreen,
    navigator: Navigator,
    profiles: ProfileRepository,
    feed: FeedRepository,
    activities: ActivityRepository,
    auth: AuthRepository,
): ProfileState {
    var profile by remember { mutableStateOf<ProfileView?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadKey by remember { mutableIntStateOf(0) }
    val did = profile?.did?.value ?: screen.actor
    val items by remember(did) { feed.authorActivities(did).map { it.toImmutableList() } }.collectAsState(persistentListOf())
    val scope = rememberCoroutineScope()

    LaunchedEffect(loadKey) {
        runSuspendCatching {
            profile = profiles.profile(screen.actor)
            feed.refreshAuthor(screen.actor)
        }
            .onSuccess { error = null }
            .onFailure { error = it.message ?: "Couldn't load profile" }
    }

    val isSelf = did == profiles.did.value
    return ProfileState(profile, items, isSelf, error) { event ->
        when (event) {
            ProfileEvent.Back -> {
                navigator.pop()
            }

            is ProfileEvent.Open -> {
                navigator.goTo(ActivityDetailScreen(event.activity.uri.value))
            }

            is ProfileEvent.ToggleKudos -> {
                scope.launch { runSuspendCatching { activities.toggleKudos(event.activity) } }
            }

            ProfileEvent.ToggleFollow -> {
                profile?.let { current ->
                    scope.launch {
                        runSuspendCatching {
                            if (current.viewer?.following == null) {
                                profiles.follow(current)
                            } else {
                                profiles.unfollow(current)
                            }
                        }
                        loadKey++
                    }
                }
            }

            ProfileEvent.OpenFollowers -> {
                navigator.goTo(FollowListScreen(did, FollowListKind.Followers))
            }

            ProfileEvent.OpenFollowing -> {
                navigator.goTo(FollowListScreen(did, FollowListKind.Following))
            }

            // Dropping the session tears down the SessionGraph and returns to LoginScreen.
            ProfileEvent.SignOut -> {
                scope.launch { auth.signOut() }
            }
        }
    }
}
