package io.github.solcott.activitytracker.app.search

import io.github.solcott.activitytracker.app.text.AppText

/** What the Search tab says that follows a rule, chosen here for the shared and the native UI (dev/shared-display-text). */
object AthleteSearchTexts {
    /** The tab's title: finding athletes, or the tab's own name while it browses segments. */
    fun title(mode: SearchMode): AppText =
        AppText.named(
            when (mode) {
                SearchMode.Athletes -> "search_title"
                SearchMode.Segments -> "search_tab_title"
            },
        )

    /** A mode's name in the switch between them. */
    fun mode(mode: SearchMode): AppText =
        AppText.named(
            when (mode) {
                SearchMode.Athletes -> "search_mode_athletes"
                SearchMode.Segments -> "search_mode_segments"
            },
        )

    /** What to type, while nothing is typed; null once there is a query. */
    fun prompt(state: AthleteSearchState): AppText? = if (state.query.text.isBlank()) AppText.named("search_prompt") else null

    /** That nothing matched the query, as typed but trimmed, for when the results are empty; null before a query, which shows [prompt]. */
    fun empty(state: AthleteSearchState): AppText? {
        val query = state.query.text.trim().toString()
        return if (query.isEmpty()) null else AppText.named("search_no_results", AppText.written(query))
    }
}
