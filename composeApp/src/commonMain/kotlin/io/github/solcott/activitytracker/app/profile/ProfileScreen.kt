package io.github.solcott.activitytracker.app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.ui.ActivityCard
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import kotlinx.coroutines.launch

/** [actor] is a DID or handle. */
@CircuitSerializable(SessionScope::class) data class ProfileScreen(val actor: String) : Screen

data class ProfileState(
    val profile: ProfileView?,
    val activities: List<ActivityView>,
    val isSelf: Boolean,
    val error: String?,
    val eventSink: (ProfileEvent) -> Unit,
) : CircuitUiState

sealed interface ProfileEvent {
    data object Back : ProfileEvent

    data object ToggleFollow : ProfileEvent

    data object SignOut : ProfileEvent

    data class Open(val activity: ActivityView) : ProfileEvent

    data class ToggleKudos(val activity: ActivityView) : ProfileEvent
}

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
    val items by remember(did) { feed.authorActivities(did) }.collectAsState(emptyList())
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
                scope.launch {
                    runSuspendCatching {
                        if (event.activity.viewer?.kudos == null) {
                            activities.giveKudos(event.activity)
                        } else {
                            activities.removeKudos(event.activity)
                        }
                    }
                }
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

            // Dropping the session tears down the SessionGraph and returns to LoginScreen.
            ProfileEvent.SignOut -> {
                scope.launch { auth.signOut() }
            }
        }
    }
}

@CircuitInject(ProfileScreen::class, SessionScope::class)
@Composable
fun ProfileUi(state: ProfileState, modifier: Modifier = Modifier) {
    val profile = state.profile
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(profile?.displayName ?: profile?.handle?.value ?: "Profile") },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(ProfileEvent.Back) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (state.isSelf) {
                        TextButton(onClick = { state.eventSink(ProfileEvent.SignOut) }) { Text("Sign out") }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            profile?.let { p ->
                item {
                    Text("@${p.handle.value}", style = MaterialTheme.typography.bodyMedium)
                    p.description?.let { Text(it) }
                    Text(
                        "${p.activitiesCount ?: 0} activities · ${p.followersCount ?: 0} followers · ${p.followsCount ?: 0} following",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!state.isSelf) {
                        if (p.viewer?.following == null) {
                            Button(onClick = { state.eventSink(ProfileEvent.ToggleFollow) }) { Text("Follow") }
                        } else {
                            OutlinedButton(onClick = { state.eventSink(ProfileEvent.ToggleFollow) }) { Text("Following") }
                        }
                    }
                }
            }
            items(state.activities, key = { it.uri.value }) { activity ->
                ActivityCard(
                    activity = activity,
                    onClick = { state.eventSink(ProfileEvent.Open(activity)) },
                    onAuthorClick = {},
                    onKudos = { state.eventSink(ProfileEvent.ToggleKudos(activity)) },
                )
            }
        }
    }
}
