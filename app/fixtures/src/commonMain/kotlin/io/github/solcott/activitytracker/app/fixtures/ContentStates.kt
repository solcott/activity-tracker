package io.github.solcott.activitytracker.app.fixtures

import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.Origin
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.LoadStatus
import io.github.solcott.uistate.Page
import io.github.solcott.uistate.PagedState
import io.github.solcott.uistate.applyFirstPage
import io.github.solcott.uistate.applyNextPage
import io.github.solcott.uistate.pagedStateOf
import io.github.solcott.uistate.refreshing

/** A [ContentState] in each ui/content-loading state. [data] is what the screen holds before (or after) its first load. */
object ContentStates {
    /** Before the first answer. */
    fun <T> loading(data: T): ContentState<T> = ContentState(data)

    fun <T> loaded(data: T, origin: Origin = Origin.Network): ContentState<T> = ContentState(data, origin, LoadStatus.Idle)

    /** Loaded content, refreshing. */
    fun <T> refreshing(data: T): ContentState<T> = ContentState(data, Origin.Network, LoadStatus.Loading)

    /** The first load failed, offline unless [reason] says otherwise. */
    fun <T> failed(data: T, reason: DataError = DataError.Network): ContentState<T> = ContentState(data, null, LoadStatus.Failed(reason))

    /** Loaded content whose refresh failed. */
    fun <T> refreshFailed(data: T, reason: DataError = DataError.Network): ContentState<T> =
        ContentState(data, Origin.Network, LoadStatus.Failed(reason))
}

/**
 * A [PagedState] in each ui/content-loading state: first load, failed, loaded, refreshing, refresh failed, a next page loading or failed,
 * and the end.
 */
object PagedStates {
    /** The cursor of the page after a loaded list that has more. */
    const val NEXT = "2"

    fun <T, M> loading(): PagedState<T, M> = pagedStateOf()

    fun <T, M> failed(reason: DataError = DataError.Network): PagedState<T, M> =
        pagedStateOf<T, M>().applyFirstPage(Outcome.Error(reason, Origin.Network), ::id)

    /** The first page, with more after it when [next] is set. */
    fun <T, M> loaded(items: List<T>, meta: M, next: String? = null): PagedState<T, M> =
        pagedStateOf(Outcome.Data(Page(items, meta, next), Origin.Network), ::id)

    fun <T> loaded(items: List<T>, next: String? = null): PagedState<T, Unit> = loaded(items, Unit, next)

    fun <T, M> refreshing(items: List<T>, meta: M): PagedState<T, M> = loaded(items, meta).refreshing()

    fun <T, M> refreshFailed(items: List<T>, meta: M, reason: DataError = DataError.Network): PagedState<T, M> =
        loaded(items, meta).refreshing().applyFirstPage(Outcome.Error(reason, Origin.Network), ::id)

    fun <T, M> loadingMore(items: List<T>, meta: M): PagedState<T, M> = loaded(items, meta, NEXT).applyNextPage(NEXT, Outcome.Loading, ::id)

    fun <T, M> pageFailed(items: List<T>, meta: M, reason: DataError = DataError.Network): PagedState<T, M> =
        loaded(items, meta, NEXT).applyNextPage(NEXT, Outcome.Error(reason, Origin.Network), ::id)

    /** Every page listed. */
    fun <T, M> end(items: List<T>, meta: M): PagedState<T, M> = loaded(items, meta, next = null)

    private fun id(item: Any?): Any? = item
}

/** A screen's sample for each paged-list [SampleKind], listing [items] when it has any: [state] builds the screen's state around each. */
fun <T, M, S> pagedSamples(items: List<T>, meta: M, state: (PagedState<T, M>) -> S): Map<String, S> =
    mapOf(
        SampleKind.LOADING to state(PagedStates.loading()),
        SampleKind.FAILED to state(PagedStates.failed()),
        SampleKind.EMPTY to state(PagedStates.end(emptyList(), meta)),
        SampleKind.LOADED to state(PagedStates.loaded(items, meta, PagedStates.NEXT)),
        SampleKind.REFRESHING to state(PagedStates.refreshing(items, meta)),
        SampleKind.REFRESH_FAILED to state(PagedStates.refreshFailed(items, meta)),
        SampleKind.LOADING_MORE to state(PagedStates.loadingMore(items, meta)),
        SampleKind.PAGE_FAILED to state(PagedStates.pageFailed(items, meta)),
        SampleKind.END to state(PagedStates.end(items, meta)),
    )

/** A screen's sample for each [ContentState] kind: [state] builds it around [empty] before the first load and around [loaded] after. */
fun <T, S> contentSamples(empty: T, loaded: T, state: (ContentState<T>) -> S): Map<String, S> =
    mapOf(
        SampleKind.LOADING to state(ContentStates.loading(empty)),
        SampleKind.FAILED to state(ContentStates.failed(empty)),
        SampleKind.LOADED to state(ContentStates.loaded(loaded)),
        SampleKind.REFRESHING to state(ContentStates.refreshing(loaded)),
        SampleKind.REFRESH_FAILED to state(ContentStates.refreshFailed(loaded)),
    )
