package io.github.solcott.activitytracker.app.ui

/**
 * Stable test anchors on the shared loading pieces and the top bar's back button (dev/ui-test-harness), applied with `Modifier.testTag`.
 * They don't depend on the text shown or its language, and mean nothing beyond tests: screen readers still read the pieces' own text.
 */
object TestTags {
    /** The name sheet's Share to feed switch (feed/posts). */
    const val SHARE_TO_FEED = "shareToFeed"

    /** A post card's up and down vote buttons (feed/posts). */
    const val POST_VOTE_UP = "postVoteUp"
    const val POST_LIKE = "postLike"
    const val POST_REPOST = "postRepost"
    const val POST_REPOSTED_BY = "postRepostedBy"
    const val POST_QUOTE = "postQuote"
    const val POST_QUOTE_COUNT = "postQuoteCount"
    const val QUOTED_POST = "quotedPost"
    const val QUOTED_POST_LINK = "quotedPostLink"
    const val QUOTED_POST_UNAVAILABLE = "quotedPostUnavailable"
    const val QUOTE_TEXT = "quoteText"
    const val POST_VOTE_DOWN = "postVoteDown"

    /** The first-load spinner: `ContentStateLayout`'s, or a paged list's before it answers. */
    const val LOADING = "loading"

    /** Why the first load failed. */
    const val LOAD_ERROR = "loadError"

    /** The first load's retry. */
    const val RETRY = "retry"

    /** The spinner over loaded content while it refreshes. */
    const val REFRESHING = "refreshing"

    /** Why a refresh of loaded content failed, with its retry. */
    const val REFRESH_ERROR = "refreshError"

    /** A paged list's spinner below its items while the next page loads. */
    const val PAGE_LOADING = "pageLoading"

    /** Why a paged list's next page failed. */
    const val PAGE_ERROR = "pageError"

    /** The failed page's retry. */
    const val PAGE_RETRY = "pageRetry"

    /** Below a paged list's items once every page is listed. */
    const val LIST_END = "listEnd"

    /** The top bar's back button. */
    const val BACK = "back"

    /** A section's row on the Trail page, which collapses or expands it (segments/path-page). */
    const val PATH_SECTION = "pathSection"

    /** The Trail page's control that expands or collapses every section. */
    const val PATH_ALL_SECTIONS = "pathAllSections"

    /** The Trail page's Trail details control, which unfolds and folds its cards (segments/trail-attributes). */
    const val PATH_DETAILS_TOGGLE = "pathDetailsToggle"

    /** The Trail page's search field (segments/path-filters). */
    const val PATH_SEARCH = "pathSearch"

    /** The Trail page's clear-filters control, when nothing matches. */
    const val PATH_CLEAR_FILTERS = "pathClearFilters"

    /** The Trail page's elevation profile (segments/path-profile). */
    const val PATH_PROFILE = "pathProfile"

    /** A map's rating legend (segments/trail-ratings). */
    const val TRAIL_RATING_LEGEND = "trailRatingLegend"

    /** A rated row's or legend line's rating swatch (segments/trail-ratings). */
    const val TRAIL_RATING_SWATCH = "trailRatingSwatch"
}
