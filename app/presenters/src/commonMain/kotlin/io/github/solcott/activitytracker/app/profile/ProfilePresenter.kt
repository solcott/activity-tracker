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
import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.activitylist.rememberActivityListEventSink
import io.github.solcott.activitytracker.app.moderation.rememberRevealed
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.report.rememberReporting
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.data.profile.FollowRequests
import io.github.solcott.activitytracker.data.profile.FollowRequestsRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map

@CircuitInject(ProfileScreen::class, SessionScope::class)
@Composable
@Suppress("CyclomaticComplexMethod") // One branch per profile event; each hands off to a helper or the navigator.
fun ProfilePresenter(
    screen: ProfileScreen,
    navigator: Navigator,
    profiles: ProfileRepository,
    feed: FeedRepository,
    activities: ActivityRepository,
    followRequests: FollowRequestsRepository,
    blocks: BlockRepository,
    reports: ReportRepository,
): ProfileState {
    var profile by remember { mutableStateOf<ProfileView?>(null) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var requests by remember { mutableStateOf<FollowRequests?>(null) }
    val did = profile?.did?.value ?: screen.actor
    val items by remember(did) { feed.authorActivities(did).map { it.toImmutableList() } }.collectAsState(persistentListOf())
    val flights = rememberSingleFlight()
    val activityEvents = rememberActivityListEventSink(navigator, activities) { error = it }

    LaunchedEffect(screen.actor) {
        runSuspendCatching {
            profile = blocks.applyTo(profiles.profile(screen.actor))
            feed.refreshAuthor(screen.actor)
        }
            .onSuccess { error = null }
            .onFailure { error = UiError.of(UiError.Kind.LoadProfile, it) }
    }

    val viewerDid = profiles.did.value
    val isSelf = did == viewerDid
    val toggleBlockOrMute = { event: ProfileEvent ->
        flights.toggleBlockOrMute(
            event,
            profile.takeUnless { isSelf },
            blocks,
            onDone = {
                profile = it
                error = null
            },
        ) {
            error = it
        }
    }
    // The report sheet offers Block only while the viewer doesn't block them, so its block is the menu's Block, after the same
    // confirmation.
    val reporting =
        rememberReporting(reports) { _, _ -> if (profile?.viewer?.blocking == null) toggleBlockOrMute(ProfileEvent.ToggleBlock) }
    val approves = isSelf && profile?.followPolicy == FollowPolicy.APPROVE
    // Counted afresh each time the athlete's own profile shows, so answering requests and coming back updates it.
    LaunchedEffect(approves) { requests = if (approves) runSuspendCatching { followRequests.followRequests() }.getOrNull() else null }
    return ProfileState(
        profile = profile,
        activities = items,
        isSelf = isSelf,
        viewerDid = viewerDid,
        followPending = flights.isRunning(ProfileEvent.ToggleFollow),
        blocking = profile.blockedBy(isSelf),
        blockedOnBluesky = profile.blockedOnBluesky(isSelf),
        muting = profile.mutedBy(isSelf),
        followRequestCount = requests?.pending?.size,
        moreFollowRequests = requests?.truncated == true,
        error = error,
        reporting = reporting,
        revealed = rememberRevealed(),
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

            ProfileEvent.ToggleBlock,
            ProfileEvent.ToggleMute -> {
                toggleBlockOrMute(event)
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

/**
 * Blocks or unblocks ([ProfileEvent.ToggleBlock]), or mutes or unmutes, [current]'s athlete (nothing for none, or the athlete's own), and
 * hands [onDone] the profile as it now shows ([BlockRepository.applyTo]); a failure goes to [onError], a session that can't write there
 * asked to sign in again.
 */
private fun SingleFlight.toggleBlockOrMute(
    event: ProfileEvent,
    current: ProfileView?,
    blocks: BlockRepository,
    onDone: (ProfileView) -> Unit,
    onError: (UiError) -> Unit,
) {
    val block = event == ProfileEvent.ToggleBlock
    // Nothing here can remove a Bluesky block, so a block held only by one stays.
    if (current == null || (block && current.blockedOnBluesky(isSelf = false))) return
    launch(event) {
        runSuspendCatching {
            if (block) blocks.setBlocked(current.did, current.viewer?.blocking == null)
            else blocks.setMuted(current.did, current.viewer?.muting == null)
        }
            .onSuccess { onDone(blocks.applyTo(current)) }
            .onFailure { onError(uiErrorOf(if (block) UiError.Kind.UpdateBlock else UiError.Kind.UpdateMute, it)) }
    }
}

/** Whether the viewer blocks this athlete; never on their own profile. */
private fun ProfileView?.blockedBy(isSelf: Boolean): Boolean = !isSelf && this?.viewer?.blocking != null

/** Whether the viewer's only block of this athlete is an `app.bsky.graph.block` (`viewer.blocking` names the app's one first). */
private fun ProfileView?.blockedOnBluesky(isSelf: Boolean): Boolean =
    blockedBy(isSelf) && this?.viewer?.blocking?.collection == Nsid.BSKY_GRAPH_BLOCK

/** Whether the viewer mutes this athlete; never on their own profile. */
private fun ProfileView?.mutedBy(isSelf: Boolean): Boolean = !isSelf && this?.viewer?.muting != null
