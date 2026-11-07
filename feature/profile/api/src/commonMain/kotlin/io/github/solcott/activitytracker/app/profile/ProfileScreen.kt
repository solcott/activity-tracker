package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.activitylist.ActivityListEvent
import io.github.solcott.activitytracker.app.moderation.Revealed
import io.github.solcott.activitytracker.app.posts.PostEvent
import io.github.solcott.activitytracker.app.posts.QuoteFields
import io.github.solcott.activitytracker.app.report.Reporting
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.PagedState
import io.github.solcott.uistate.pagedStateOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** [actor] is a DID or handle. */
@CircuitSerializable(SessionScope::class) data class ProfileScreen(val actor: String) : Screen

@Immutable
data class ProfileState(
    /** The profile's header, loaded (ui/content-loading); its loading and failure are the page's. Null until it first loads. */
    val content: ContentState<ProfileView?> = ContentState(null),
    /** The athlete's activities, loaded on their own below the header; empty until they first load. */
    val activities: ContentState<ImmutableList<ActivityView>> = ContentState(persistentListOf()),
    val isSelf: Boolean = false,
    /** The signed-in athlete, so the UI can leave likes off their own activities. */
    val viewerDid: String = "",
    /** A follow or unfollow is on its way to the PDS; the button ignores taps until it lands. */
    val followPending: Boolean = false,
    /** The viewer blocks this athlete ([ProfileView.viewer]'s `blocking`): Unblock shows in place of Follow, and no activities. */
    val blocking: Boolean = false,
    /** The viewer's only block of this athlete is on Bluesky, which this app can't remove: no Unblock, a note instead. */
    val blockedOnBluesky: Boolean = false,
    /** The viewer mutes this athlete: their activities leave the viewer's timeline. */
    val muting: Boolean = false,
    /** The athlete's own unanswered follow requests, when they approve followers; null otherwise or until loaded. */
    val followRequestCount: Int? = null,
    /** There are more requests than [followRequestCount] counts. */
    val moreFollowRequests: Boolean = false,
    /** The athlete's own pending invites to other athletes' activities (feed/activity-invites), less those they declined; 0 shows none. */
    val activityInviteCount: Int = 0,
    /** Why an action (a follow, block, mute or like) failed; loading failures are [content]'s and [activities]'. */
    val error: UiError? = null,
    /** Report on someone else's profile, kept when the viewer blocks them. */
    val reporting: Reporting = Reporting(),
    /** The avatar, labeled photos, or a hidden athlete's activities the viewer revealed on this screen. */
    val revealed: Revealed = Revealed(),
    /** Which of the athlete's lists shows below the header: their activities, first, or their posts (feed/posts). */
    val tab: ProfileTab = ProfileTab.Activities,
    /** The athlete's posts the viewer may see, newest first, loaded once Posts is chosen (ui/content-loading). */
    val posts: PagedState<PostView, Unit> = pagedStateOf(),
    /** The quote sheet and the delete confirmation for posts on the Posts tab (feed/quote-posts). */
    val quote: QuoteFields = QuoteFields(),
    val eventSink: (ProfileEvent) -> Unit = {},
) : CircuitUiState {
    val profile: ProfileView?
        get() = content.data
}

/** The lists a profile switches between below its header. */
enum class ProfileTab {
    Activities,
    Posts,
}

sealed interface ProfileEvent {
    data object Back : ProfileEvent

    data class SelectTab(val tab: ProfileTab) : ProfileEvent

    /** A tap on one of [ProfileState.posts]. */
    data class Post(val event: PostEvent) : ProfileEvent

    data object LoadMorePosts : ProfileEvent

    data object RetryPosts : ProfileEvent

    /** Follows, unfollows, or cancels a follow request. */
    data object ToggleFollow : ProfileEvent

    /** Blocks the athlete (the UI confirms first) or unblocks them; ignored while [ProfileState.blockedOnBluesky]. */
    data object ToggleBlock : ProfileEvent

    /** Mutes or unmutes the athlete; no confirmation, since it can be undone. */
    data object ToggleMute : ProfileEvent

    data object OpenFollowers : ProfileEvent

    data object OpenFollowing : ProfileEvent

    data object OpenFollowRequests : ProfileEvent

    data object OpenActivityInvites : ProfileEvent

    data object OpenSettings : ProfileEvent

    /** The athlete's own starred segments; ignored on anyone else's profile. */
    data object OpenStarredSegments : ProfileEvent

    data object EditProfile : ProfileEvent

    /** A tap on one of [ProfileState.activities]. */
    data class Activity(val event: ActivityListEvent) : ProfileEvent

    /** Loads the profile and its activities again after either failed to. */
    data object Retry : ProfileEvent
}
