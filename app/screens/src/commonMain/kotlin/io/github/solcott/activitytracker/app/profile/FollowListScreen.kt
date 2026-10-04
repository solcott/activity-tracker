package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import kotlinx.collections.immutable.ImmutableList
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
    /** The actor whose list this is; null until the first page loads. */
    val subject: ProfileViewBasic?,
    val accounts: ImmutableList<ProfileViewBasic>,
    val isLoading: Boolean,
    val canLoadMore: Boolean,
    val error: UiError?,
    val eventSink: (FollowListEvent) -> Unit,
) : CircuitUiState

sealed interface FollowListEvent {
    data object Back : FollowListEvent

    /** Loads the next page; ignored while a page is loading or when there are no more. */
    data object LoadMore : FollowListEvent

    /** Reloads from the first page after an error. */
    data object Retry : FollowListEvent

    data class Open(val did: Did) : FollowListEvent
}
