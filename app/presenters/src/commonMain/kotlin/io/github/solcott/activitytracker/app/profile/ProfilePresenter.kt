package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.activitylist.rememberActivityListEventSink
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.profile.FollowRequests
import io.github.solcott.activitytracker.data.profile.FollowRequestsRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map

@CircuitInject(ProfileScreen::class, SessionScope::class)
@Composable
fun ProfilePresenter(
    screen: ProfileScreen,
    navigator: Navigator,
    profiles: ProfileRepository,
    feed: FeedRepository,
    activities: ActivityRepository,
    followRequests: FollowRequestsRepository,
): ProfileState {
    var profile by remember { mutableStateOf<ProfileView?>(null) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var requests by remember { mutableStateOf<FollowRequests?>(null) }
    val did = profile?.did?.value ?: screen.actor
    val items by remember(did) { feed.authorActivities(did).map { it.toImmutableList() } }.collectAsState(persistentListOf())
    val flights = rememberSingleFlight()
    val activityEvents = rememberActivityListEventSink(navigator, activities)

    LaunchedEffect(screen.actor) {
        runSuspendCatching {
            profile = profiles.profile(screen.actor)
            feed.refreshAuthor(screen.actor)
        }
            .onSuccess { error = null }
            .onFailure { error = UiError.of(UiError.Kind.LoadProfile, it) }
    }

    val viewerDid = profiles.did.value
    val isSelf = did == viewerDid
    val approves = isSelf && profile?.followPolicy == FollowPolicy.APPROVE
    // Counted afresh each time the athlete's own profile shows, so answering requests and coming back updates it.
    LaunchedEffect(approves) { requests = if (approves) runSuspendCatching { followRequests.followRequests() }.getOrNull() else null }
    return ProfileState(
        profile = profile,
        activities = items,
        isSelf = isSelf,
        viewerDid = viewerDid,
        followPending = flights.isRunning(ProfileEvent.ToggleFollow),
        followRequestCount = requests?.pending?.size,
        moreFollowRequests = requests?.truncated == true,
        error = error,
    ) { event ->
        when (event) {
            ProfileEvent.Back -> {
                navigator.pop()
            }

            is ProfileEvent.Activity -> {
                activityEvents(event.event)
            }

            ProfileEvent.ToggleFollow -> {
                val current = profile
                if (current != null) {
                    flights.launch(ProfileEvent.ToggleFollow) {
                        runSuspendCatching {
                            if (current.viewer?.following == null) profiles.follow(current) else profiles.unfollow(current)
                        }
                            .onSuccess {
                                profile = it
                                error = null
                            }
                            .onFailure { error = UiError.of(UiError.Kind.UpdateFollow, it) }
                    }
                }
            }

            ProfileEvent.OpenFollowers -> {
                navigator.goTo(FollowListScreen(did, FollowListKind.Followers))
            }

            ProfileEvent.OpenFollowing -> {
                navigator.goTo(FollowListScreen(did, FollowListKind.Following))
            }

            ProfileEvent.OpenFollowRequests -> {
                navigator.goTo(FollowRequestsScreen)
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
