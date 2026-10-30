package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.BlockSource
import io.github.solcott.dataresult.Outcome
import io.github.solcott.dataresult.mapData
import io.github.solcott.uistate.Page
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import io.github.solcott.uistate.pageOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.flow

@CircuitInject(BlockListScreen::class, SessionScope::class)
@Composable
fun BlockListPresenter(screen: BlockListScreen, navigator: Navigator, blocks: BlockRepository): BlockListState {
    val accounts =
        rememberRetainedPaging(screen, idOf = { account: BlockListAccount -> account.profile.did }) { cursor ->
            flow { emit(blocks.page(screen.kind, cursor)) }
        }
    var actionError by rememberRetained { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()

    return BlockListState(
        kind = screen.kind,
        accounts = accounts.state,
        removing = accounts.state.items.map { it.profile.did }.filter { flights.isRunning(it) }.toImmutableSet(),
        actionError = actionError,
    ) { event ->
        when (event) {
            BlockListEvent.Back -> {
                navigator.pop()
            }

            BlockListEvent.LoadMore -> {
                accounts.loadMore()
            }

            BlockListEvent.Retry -> {
                accounts.retry()
            }

            is BlockListEvent.Open -> {
                navigator.goTo(ProfileScreen(event.did.value))
            }

            is BlockListEvent.Remove -> {
                val account = accounts.state.items.firstOrNull { it.profile.did == event.did }?.takeIf { it.removable }
                if (account != null) {
                    actionError = null
                    flights.launch(event.did) {
                        runSuspendCatching { blocks.remove(screen.kind, event.did) }
                            .onSuccess { accounts.update { list -> list.afterRemoving(event.did) } }
                            .onFailure {
                                val kind = if (screen.kind == BlockListKind.Blocked) UiError.Kind.UpdateBlock else UiError.Kind.UpdateMute
                                actionError = uiErrorOf(kind, it)
                            }
                    }
                }
            }
        }
    }
}

/** A page of the athlete's blocks or mutes, each block marked removable only when the app holds it. */
private suspend fun BlockRepository.page(kind: BlockListKind, cursor: String?): Outcome<Page<BlockListAccount, Unit>> =
    when (kind) {
        BlockListKind.Blocked ->
            blocksPage(cursor).mapData { page ->
                val listed =
                    page.items.map {
                        BlockListAccount(it.actor, removable = BlockSource.APP in it.sources, onBluesky = BlockSource.BLUESKY in it.sources)
                    }
                pageOf(listed, page.next)
            }
        BlockListKind.Muted -> mutesPage(cursor).mapData { page -> pageOf(page.items.map { BlockListAccount(it) }, page.next) }
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
