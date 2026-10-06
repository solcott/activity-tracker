package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
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
): ProfileState {
    var profile by remember { mutableStateOf<ProfileView?>(null) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var followPending by remember { mutableStateOf(false) }
    val did = profile?.did?.value ?: screen.actor
    val items by remember(did) { feed.authorActivities(did).map { it.toImmutableList() } }.collectAsState(persistentListOf())
    val scope = rememberCoroutineScope()

    LaunchedEffect(screen.actor) {
        runSuspendCatching {
            profile = profiles.profile(screen.actor)
            feed.refreshAuthor(screen.actor)
        }
            .onSuccess { error = null }
            .onFailure { error = UiError.of(UiError.Kind.LoadProfile, it) }
    }

    val isSelf = did == profiles.did.value
    return ProfileState(profile, items, isSelf, followPending, error) { event ->
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
                val current = profile
                if (current != null && !followPending) {
                    followPending = true
                    scope.launch {
                        runSuspendCatching {
                            if (current.viewer?.following == null) profiles.follow(current) else profiles.unfollow(current)
                        }
                            .onSuccess {
                                profile = it
                                error = null
                            }
                            .onFailure { error = UiError.of(UiError.Kind.UpdateFollow, it) }
                        followPending = false
                    }
                }
            }

            ProfileEvent.OpenFollowers -> {
                navigator.goTo(FollowListScreen(did, FollowListKind.Followers))
            }

            ProfileEvent.OpenFollowing -> {
                navigator.goTo(FollowListScreen(did, FollowListKind.Following))
            }

            ProfileEvent.OpenSettings -> {
                navigator.goTo(SettingsScreen)
            }

            ProfileEvent.EditProfile -> {
                navigator.goTo(EditProfileScreen)
            }
        }
    }
}
