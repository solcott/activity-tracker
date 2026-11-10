package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Immutable
import io.github.solcott.uistate.LoadStatus
import io.github.solcott.uistate.PagedState
import io.github.solcott.uistate.hasAnswer

/**
 * What a paged list shows around its items (ui/content-loading), decided once for the shared and the native UIs, as [ContentShown] is for
 * loaded content. Before the first answer only [below] is set: progress, or why the first page failed ([firstPage]). Once answered: [above]
 * while a refresh or a new key's first page loads or after it failed, [showsEmpty] when the list holds nothing and isn't reloading, [below]
 * while the next page loads or after it failed, and [atEnd] once every page is listed and there is something listed.
 */
@Immutable
data class PagedShown(
    val above: Status?,
    val showsEmpty: Boolean,
    val below: Status?,
    /** Whether [below] is about the first page, which has the list to itself, rather than a next page under the items. */
    val firstPage: Boolean,
    val atEnd: Boolean,
) {
    /** A load being shown: one in progress, or one that failed, with a way to try it again. */
    @Immutable
    sealed interface Status {
        data object Progress : Status

        /** The load failed: [error] says what and why. */
        @Immutable data class Failure(val error: UiError) : Status
    }

    companion object {
        /** What to show around [state]'s items, whose failures are of [errorKind]. */
        fun of(state: PagedState<*, *>, errorKind: UiError.Kind): PagedShown =
            of(state.hasAnswer, state.items.isEmpty(), state.refresh, state.append, state.endReached, errorKind)

        /** What to show around a paged list taken apart, for a list that lays its own items over the pages. */
        @Suppress("LongParameterList") // A paged list's statuses, taken apart.
        fun of(
            hasAnswer: Boolean,
            isEmpty: Boolean,
            refresh: LoadStatus,
            append: LoadStatus,
            endReached: Boolean,
            errorKind: UiError.Kind,
        ): PagedShown =
            if (!hasAnswer) {
                PagedShown(above = null, showsEmpty = false, below = status(refresh, errorKind), firstPage = true, atEnd = false)
            } else {
                PagedShown(
                    above = status(refresh, errorKind),
                    showsEmpty = isEmpty && refresh !is LoadStatus.Loading,
                    below = status(append, errorKind),
                    firstPage = false,
                    atEnd = endReached && !isEmpty && append is LoadStatus.Idle,
                )
            }

        private fun status(load: LoadStatus, errorKind: UiError.Kind): Status? =
            when (load) {
                is LoadStatus.Failed -> Status.Failure(UiError(errorKind, load.error))
                LoadStatus.Loading -> Status.Progress
                LoadStatus.Idle -> null
            }
    }
}
