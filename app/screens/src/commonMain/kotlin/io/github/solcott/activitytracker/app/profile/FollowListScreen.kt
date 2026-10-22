package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.uistate.PagedState
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.serialization.Serializable

@Serializable
enum class FollowListKind {
    /** Athletes who follow the actor. */
    Followers,

    /** Athletes the actor follows. */
    Following,
}

/** Who follows [actor], or who [actor] follows. [actor] is a DID or handle. */
@CircuitSerializable(SessionScope::class) data class FollowListScreen(val actor: String, val kind: FollowListKind) : Screen

@Immutable
data class FollowListState(
    val kind: FollowListKind,
    /** The accounts loaded so far, headed by the actor whose list this is, and how loading them stands (ui/content-loading). */
    val accounts: PagedState<ProfileViewBasic, ProfileViewBasic>,
    /** The athlete's own followers list while they approve followers, so each follower can be removed. */
    val canRemove: Boolean = false,
    /** Followers whose removal is being written. */
    val removing: ImmutableSet<Did> = persistentSetOf(),
    /** Why the last removal failed; the next one clears it. */
    val actionError: UiError? = null,
    val eventSink: (FollowListEvent) -> Unit,
) : CircuitUiState {
    /** The actor whose list this is; null until the first page loads. */
    val subject: ProfileViewBasic?
        get() = accounts.meta
}

sealed interface FollowListEvent {
    data object Back : FollowListEvent

    /** Loads the next page; ignored while a page is loading, after a failed page, or when there are no more. */
    data object LoadMore : FollowListEvent

    /** Loads again what failed to load, and only that: the first page, or the page that failed. */
    data object Retry : FollowListEvent

    data class Open(val did: Did) : FollowListEvent

    /** Removes [did] from the athlete's followers: their follow waits for approval again. Only when [FollowListState.canRemove]. */
    data class Remove(val did: Did) : FollowListEvent
}
