package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel

/** One page of a cursor list: its [items], and the [cursor] of the next page, or null at the end. */
class CursorPage<T>(val items: List<T>, val cursor: String?)

/**
 * A list loaded a page at a time by cursor ([rememberCursorPaging]). Pages are de-duplicated by id, keeping the first occurrence, since an
 * index can move an item between pages. A failed page leaves [failure] set, which stops [loadMore] until [retry] reloads from the first
 * page.
 */
@Stable
class CursorPaging<T> internal constructor(initiallyLoading: Boolean) {
    var items by mutableStateOf<ImmutableList<T>>(persistentListOf())
        internal set

    var isLoading by mutableStateOf(initiallyLoading)
        internal set

    /** Why the last page failed to load; null after a page loads. */
    var failure by mutableStateOf<Throwable?>(null)
        internal set

    internal var cursor by mutableStateOf<String?>(null)
    internal var settledFor by mutableStateOf<Any?>(NotSettled)
    internal var reloads by mutableIntStateOf(0)
    internal val requests = Channel<Unit>(Channel.CONFLATED)

    // What the host passed in this composition, set before it reads [loaded]; changes to either recompose the host anyway.
    internal var key: Any? = null
    internal var enabled = initiallyLoading

    /** Whether the first page for the current key has settled, loaded or failed. Stays true while a later page or a retry loads. */
    val loaded: Boolean
        get() = enabled && settledFor == key

    val canLoadMore: Boolean
        get() = cursor != null && failure == null

    /** Loads the next page; ignored while a page loads, after a failure, and at the end. */
    fun loadMore() {
        if (isLoading || !canLoadMore) return
        isLoading = true
        requests.trySend(Unit)
    }

    /** Reloads from the first page. */
    fun retry() {
        reloads++
    }

    /** Edits the items shown, as after removing one. */
    fun update(transform: (List<T>) -> List<T>) {
        items = transform(items).toImmutableList()
    }

    internal object NotSettled
}

/**
 * A [CursorPaging] of what [load] returns for each cursor (null for the first page), identified by [idOf]. A new [key] (a query) loads its
 * first page, cancelling any page in flight; the items shown stay until it settles, and a failed first page empties them. While [enabled]
 * is false the list is empty and nothing loads. Paging is a load, so it runs in a `LaunchedEffect`, not a [SingleFlight].
 */
@Composable
fun <T> rememberCursorPaging(
    key: Any?,
    idOf: (T) -> Any,
    enabled: Boolean = true,
    load: suspend (cursor: String?) -> CursorPage<T>,
): CursorPaging<T> {
    val paging = remember { CursorPaging<T>(initiallyLoading = enabled) }
    paging.key = key
    paging.enabled = enabled
    val currentLoad by rememberUpdatedState(load)
    val currentIdOf by rememberUpdatedState(idOf)

    LaunchedEffect(paging, key, enabled, paging.reloads) {
        paging.failure = null
        // A request for the previous key's pages must not load into this one's. Conflated, it holds at most one.
        paging.requests.tryReceive()
        if (!enabled) {
            paging.items = persistentListOf()
            paging.cursor = null
            paging.settledFor = CursorPaging.NotSettled
            paging.isLoading = false
            return@LaunchedEffect
        }
        paging.isLoading = true
        runSuspendCatching { currentLoad(null) }
            .onSuccess { page ->
                paging.items = page.items.distinctBy(currentIdOf).toImmutableList()
                paging.cursor = page.cursor
            }
            .onFailure {
                paging.items = persistentListOf()
                paging.cursor = null
                paging.failure = it
            }
        paging.settledFor = key
        paging.isLoading = false

        while (true) {
            paging.requests.receive()
            val after = paging.cursor
            if (after == null) {
                paging.isLoading = false
                continue
            }
            runSuspendCatching { currentLoad(after) }
                .onSuccess { page ->
                    paging.items = (paging.items + page.items).distinctBy(currentIdOf).toImmutableList()
                    paging.cursor = page.cursor
                }
                .onFailure { paging.failure = it }
            paging.isLoading = false
        }
    }
    return paging
}
