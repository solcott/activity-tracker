package io.github.solcott.activitytracker.app.fixtures

/**
 * The names samples use for the ui/content-loading states, so the conformance test and previews can pick them out of every screen's `all`
 * map. A screen has only the kinds that apply to it.
 */
object SampleKind {
    const val LOADING = "loading"
    const val FAILED = "failed"
    const val EMPTY = "empty"
    const val LOADED = "loaded"
    const val REFRESHING = "refreshing"
    const val REFRESH_FAILED = "refreshFailed"
    const val LOADING_MORE = "loadingMore"
    const val PAGE_FAILED = "pageFailed"
    const val END = "end"

    /** A form with an input it won't save. */
    const val INVALID = "invalid"
}
