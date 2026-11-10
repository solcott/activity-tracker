package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.settings.SettingsPage
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.uistate.PagedState
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
@CircuitSerializable(SessionScope::class) data class BlockListScreen(val kind: BlockListKind) : SettingsPage

/** One listed account; [removable] when this app can unblock or unmute it, which a block made only on Bluesky ([onBluesky]) isn't. */
@Immutable data class BlockListAccount(val profile: ProfileViewBasic, val removable: Boolean = true, val onBluesky: Boolean = false)

@Immutable
data class BlockListState(
    val kind: BlockListKind,
    /** The accounts loaded so far, and how loading them stands (ui/content-loading). */
    val accounts: PagedState<BlockListAccount, Unit>,
    /** Accounts whose unblock or unmute is being written. */
    val removing: ImmutableSet<Did> = persistentSetOf(),
    /** Why the last unblock or unmute failed; the next one clears it. */
    val actionError: UiError? = null,
    val eventSink: (BlockListEvent) -> Unit,
) : CircuitUiState {
    /** What failed when the list can't load. */
    val loadErrorKind: UiError.Kind
        get() =
            when (kind) {
                BlockListKind.Blocked -> UiError.Kind.LoadBlocks
                BlockListKind.Muted -> UiError.Kind.LoadMutes
            }

    /** Whether [account]'s unblock or unmute is being written, while which it can't be asked for again. */
    fun isRemoving(account: BlockListAccount): Boolean = account.profile.did in removing
}

/** What the blocked and muted lists say, chosen here for the shared and the native UI (dev/shared-display-text). */
object BlockListTexts {
    /** The list's title. */
    fun title(kind: BlockListKind): AppText =
        AppText.named(
            when (kind) {
                BlockListKind.Blocked -> "settings_blocked_accounts"
                BlockListKind.Muted -> "settings_muted_accounts"
            },
        )

    /** What the list says when it lists no one. */
    fun empty(kind: BlockListKind): AppText =
        AppText.named(
            when (kind) {
                BlockListKind.Blocked -> "blocked_accounts_empty"
                BlockListKind.Muted -> "muted_accounts_empty"
            },
        )

    /** The action that takes an account off the list: Unblock or Unmute. */
    fun remove(kind: BlockListKind): AppText =
        AppText.named(
            when (kind) {
                BlockListKind.Blocked -> "action_unblock"
                BlockListKind.Muted -> "action_unmute"
            },
        )

    /** The line under an account this app can't take off the list, a block made only on Bluesky; null for any other. */
    fun note(account: BlockListAccount): AppText? =
        if (account.onBluesky && !account.removable) AppText.named("blocked_on_bluesky") else null
}

sealed interface BlockListEvent {
    data object Back : BlockListEvent

    /** Loads the next page; ignored while a page is loading, after a failed page, or when there are no more. */
    data object LoadMore : BlockListEvent

    /** Loads again what failed to load, and only that: the first page, or the page that failed. */
    data object Retry : BlockListEvent

    data class Open(val did: Did) : BlockListEvent

    /** Unblocks or unmutes [did]: it leaves the list at once. Only for a [BlockListAccount.removable] one. */
    data class Remove(val did: Did) : BlockListEvent
}
