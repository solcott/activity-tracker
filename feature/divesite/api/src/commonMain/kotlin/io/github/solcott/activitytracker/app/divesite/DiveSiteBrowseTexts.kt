package io.github.solcott.activitytracker.app.divesite

import io.github.solcott.activitytracker.app.browse.BrowseList
import io.github.solcott.activitytracker.app.browse.MapBrowseTexts
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView

/** What dive site browse says (dive-sites/site-browse, dev/shared-display-text), chosen here for the shared and the native UI. */
object DiveSiteBrowseTexts {
    val searchLabel: AppText = AppText.named("dive_site_browse_search_label")

    /** A name result's line under its name: its radius ("Within 150 m of its centre"). */
    fun details(site: DiveSiteView, units: UnitSystem): AppText = DiveSiteTexts.radius(site, units)

    /** An area site's line under its name: its radius and how far its centre is from the map's centre. */
    fun details(browsed: BrowsedSite, units: UnitSystem): AppText =
        AppText.named(
            "segment_browse_details",
            DiveSiteTexts.radius(browsed.site, units),
            AppText.named("segment_browse_away", StatText.distance(browsed.metersAway.toDouble(), units)),
        )

    /** What the list says in place of sites or results ([BrowseList.Names] and [BrowseList.Area] show those); null for those two. */
    fun message(list: BrowseList): AppText? =
        when (list) {
            BrowseList.WebPrompt -> AppText.named("dive_site_browse_web_prompt")
            BrowseList.Locating -> MapBrowseTexts.locating
            BrowseList.Nowhere -> AppText.named("dive_site_browse_nowhere")
            BrowseList.TooWide -> AppText.named("dive_site_browse_zoom_in")
            BrowseList.Names,
            BrowseList.Area -> null
        }

    /** Over the area's sites when it holds more than were read. */
    val capped: AppText = AppText.named("dive_site_browse_capped")

    /** The area has no sites. */
    val noneHere: AppText = AppText.named("dive_site_browse_none_here")

    /** No site matches the searched name: here, when This area was searched, or anywhere. */
    fun noResults(state: DiveSiteBrowseState): AppText =
        AppText.named(
            if (state.browse.offersSearchEverywhere) "dive_site_browse_no_results_here" else "dive_site_browse_no_results",
            AppText.written(state.browse.nameQuery),
        )
}
