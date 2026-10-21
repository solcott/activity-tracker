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
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.serialization.Serializable

@Serializable
enum class BlockListKind {
    /** Accounts the athlete blocks, in this app or on Bluesky. */
    Blocked,

    /** Accounts the athlete mutes. */
    Muted,
}

/** The signed-in athlete's blocked or muted accounts, from Settings. */
@CircuitSerializable(SessionScope::class) data class BlockListScreen(val kind: BlockListKind) : Screen

/** One listed account; [removable] when this app can unblock or unmute it, which a block made only on Bluesky ([onBluesky]) isn't. */
@Immutable data class BlockListAccount(val profile: ProfileViewBasic, val removable: Boolean = true, val onBluesky: Boolean = false)

@Immutable
data class BlockListState(
    val kind: BlockListKind,
    val accounts: ImmutableList<BlockListAccount>,
    val isLoading: Boolean,
    val canLoadMore: Boolean,
    /** Accounts whose unblock or unmute is being written. */
    val removing: ImmutableSet<Did> = persistentSetOf(),
    val error: UiError?,
    val eventSink: (BlockListEvent) -> Unit,
) : CircuitUiState

sealed interface BlockListEvent {
    data object Back : BlockListEvent

    /** Loads the next page; ignored while a page is loading or when there are no more. */
    data object LoadMore : BlockListEvent

    /** Reloads from the first page after an error. */
    data object Retry : BlockListEvent

    data class Open(val did: Did) : BlockListEvent

    /** Unblocks or unmutes [did]: it leaves the list at once. Only for a [BlockListAccount.removable] one. */
    data class Remove(val did: Did) : BlockListEvent
}
