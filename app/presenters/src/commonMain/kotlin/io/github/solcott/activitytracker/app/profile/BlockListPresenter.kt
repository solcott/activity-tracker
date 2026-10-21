package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.CursorPage
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberCursorPaging
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.BlockSource
import kotlinx.collections.immutable.toImmutableSet

@CircuitInject(BlockListScreen::class, SessionScope::class)
@Composable
fun BlockListPresenter(screen: BlockListScreen, navigator: Navigator, blocks: BlockRepository): BlockListState {
    val accounts =
        rememberCursorPaging(screen, idOf = { account: BlockListAccount -> account.profile.did }) { blocks.page(screen.kind, it) }
    var removeError by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    val loadError =
        accounts.failure?.let {
            UiError.of(if (screen.kind == BlockListKind.Blocked) UiError.Kind.LoadBlocks else UiError.Kind.LoadMutes, it)
        }

    return BlockListState(
        kind = screen.kind,
        accounts = accounts.items,
        isLoading = accounts.isLoading,
        canLoadMore = accounts.canLoadMore && removeError == null,
        removing = accounts.items.map { it.profile.did }.filter { flights.isRunning(it) }.toImmutableSet(),
        error = removeError ?: loadError,
    ) { event ->
        when (event) {
            BlockListEvent.Back -> {
                navigator.pop()
            }

            BlockListEvent.LoadMore -> {
                if (removeError == null) accounts.loadMore()
            }

            BlockListEvent.Retry -> {
                removeError = null
                accounts.retry()
            }

            is BlockListEvent.Open -> {
                navigator.goTo(ProfileScreen(event.did.value))
            }

            is BlockListEvent.Remove -> {
                val account = accounts.items.firstOrNull { it.profile.did == event.did }?.takeIf { it.removable }
                if (account != null) {
                    flights.launch(event.did) {
                        runSuspendCatching { blocks.remove(screen.kind, event.did) }
                            .onSuccess { accounts.update { list -> list.afterRemoving(event.did) } }
                            .onFailure {
                                val kind = if (screen.kind == BlockListKind.Blocked) UiError.Kind.UpdateBlock else UiError.Kind.UpdateMute
                                removeError = uiErrorOf(kind, it)
                            }
                    }
                }
            }
        }
    }
}

/** A page of the athlete's blocks or mutes, each block marked removable only when the app holds it. */
private suspend fun BlockRepository.page(kind: BlockListKind, cursor: String?): CursorPage<BlockListAccount> =
    when (kind) {
        BlockListKind.Blocked ->
            blocks(cursor).let { page ->
                val listed =
                    page.blocks.map {
                        BlockListAccount(it.actor, removable = BlockSource.APP in it.sources, onBluesky = BlockSource.BLUESKY in it.sources)
                    }
                CursorPage(listed, page.cursor)
            }
        BlockListKind.Muted -> mutes(cursor).let { page -> CursorPage(page.mutes.map { BlockListAccount(it) }, page.cursor) }
    }

private suspend fun BlockRepository.remove(kind: BlockListKind, did: Did) {
    when (kind) {
        BlockListKind.Blocked -> setBlocked(did, blocked = false)
        BlockListKind.Muted -> setMuted(did, muted = false)
    }
}

/** The list after [did] is unblocked or unmuted: a Bluesky block still holds, so it stays, no longer removable. */
private fun List<BlockListAccount>.afterRemoving(did: Did): List<BlockListAccount> = mapNotNull { a ->
    if (a.profile.did != did) a else a.takeIf { it.onBluesky }?.copy(removable = false)
}
