package io.github.solcott.activitytracker.app.browse

import io.github.solcott.activitytracker.app.text.AppText

/**
 * What every map browse screen says alike (segments/segment-browse, dive-sites/site-browse, dev/shared-display-text), for the shared and
 * the native UI. What each lists and its messages are the feature's own (`SegmentBrowseTexts`, `DiveSiteBrowseTexts`).
 */
object MapBrowseTexts {
    /** Where a name search looks: "This area" or "Everywhere". */
    fun scope(scope: NameScope): AppText =
        AppText.named(
            when (scope) {
                NameScope.ThisArea -> "segment_browse_scope_area"
                NameScope.Everywhere -> "segment_browse_scope_everywhere"
            },
        )

    /** Offered when no name matches in this area. */
    val searchEverywhere: AppText = AppText.named("segment_browse_search_everywhere")

    /** While browsing finds where to open. */
    val locating: AppText = AppText.named("segment_browse_locating")

    /**
     * What tapping or clicking a row does, as a screen reader announces it: on a touch screen ([touch]) a row that isn't [selected] shows
     * its line on the map, and the selected one opens; with a pointer every row opens.
     */
    fun rowAction(selected: Boolean, touch: Boolean): AppText =
        AppText.named(if (touch && !selected) "segment_browse_show_on_map" else "segment_browse_open")
}
