package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.activitylist.ActivityListEvent
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import kotlinx.collections.immutable.ImmutableList

/** [actor] is a DID or handle. */
@CircuitSerializable(SessionScope::class) data class ProfileScreen(val actor: String) : Screen

@Immutable
data class ProfileState(
    val profile: ProfileView?,
    val activities: ImmutableList<ActivityView>,
    val isSelf: Boolean,
    /** The signed-in athlete, so the UI can leave kudos off their own activities. */
    val viewerDid: String,
    /** A follow or unfollow is on its way to the PDS; the button ignores taps until it lands. */
    val followPending: Boolean,
    /** The athlete's own unanswered follow requests, when they approve followers; null otherwise or until loaded. */
    val followRequestCount: Int? = null,
    /** There are more requests than [followRequestCount] counts. */
    val moreFollowRequests: Boolean = false,
    val error: UiError?,
    val eventSink: (ProfileEvent) -> Unit,
) : CircuitUiState

sealed interface ProfileEvent {
    data object Back : ProfileEvent

    /** Follows, unfollows, or cancels a follow request. */
    data object ToggleFollow : ProfileEvent

    data object OpenFollowers : ProfileEvent

    data object OpenFollowing : ProfileEvent

    data object OpenFollowRequests : ProfileEvent

    data object OpenSettings : ProfileEvent

    data object EditProfile : ProfileEvent

    /** A tap on one of [ProfileState.activities]. */
    data class Activity(val event: ActivityListEvent) : ProfileEvent
}
