package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.SegmentView

/** What segment browse's list shows (segments/segment-browse): name results, why there are no runs yet, or the area's runs. */
enum class BrowseList {
    /** A name is searched: its results. */
    Names,

    /** No map on this platform (web): a prompt to search by name. */
    WebPrompt,

    /** Finding where to open. */
    Locating,

    /** No location and no activity with a map: move the map or search. */
    Nowhere,

    /** The map shows more than segments are searched over. */
    TooWide,

    /** The area's runs. */
    Area,
}

/** What segment browse says (segments/segment-browse, dev/shared-display-text), chosen here for the shared and the native UI. */
object SegmentBrowseTexts {
    val searchLabel: AppText = AppText.named("segment_browse_search_label")

    /** The chip for every sport group. */
    val allSports: AppText = AppText.named("segment_browse_all_sports")

    /** A sport group's chip ("Ride"). */
    fun sport(sport: String): AppText = SegmentTexts.sportGroup(sport)

    /** One choice of the sport chip: a sport group's name, or All for every group (null). */
    fun sportChoice(sport: String?): AppText = sport?.let(::sport) ?: allSports

    /** Where a name search looks: "This area" or "Everywhere". */
    fun scope(scope: NameScope): AppText =
        AppText.named(
            when (scope) {
                NameScope.ThisArea -> "segment_browse_scope_area"
                NameScope.Everywhere -> "segment_browse_scope_everywhere"
            },
        )

    /** A name result's line under its name: its length and type ("1.2 mi · Ride"). */
    fun details(segment: SegmentView, units: UnitSystem): AppText = joined(segment, null, units)

    /**
     * An area run's line under its name: its length, type and how far its line comes to the map's centre ("1.2 mi · Ride · 0.1 mi away").
     */
    fun details(run: BrowsedSegment, units: UnitSystem): AppText = joined(run.segment, run.metersAway, units)

    private fun joined(segment: SegmentView, metersAway: Int?, units: UnitSystem): AppText {
        val parts =
            listOfNotNull(
                StatText.distance(segment.distanceMeters.toDouble(), units),
                SegmentTexts.type(segment.record.sport, segment.record.kind),
                metersAway?.let { AppText.named("segment_browse_away", StatText.distance(it.toDouble(), units)) },
            )
        return parts.reduce { line, next -> AppText.named("segment_browse_details", line, next) }
    }

    /** What the list says in place of runs or results ([BrowseList.Names] and [BrowseList.Area] show those); null for those two. */
    fun message(list: BrowseList): AppText? =
        when (list) {
            BrowseList.WebPrompt -> AppText.named("segment_browse_web_prompt")
            BrowseList.Locating -> AppText.named("segment_browse_locating")
            BrowseList.Nowhere -> AppText.named("segment_browse_nowhere")
            BrowseList.TooWide -> AppText.named("segment_browse_zoom_in")
            BrowseList.Names,
            BrowseList.Area -> null
        }

    /** Over the area's runs when it holds more than were read. */
    val capped: AppText = AppText.named("segment_browse_capped")

    /** The area has no runs. */
    val noneHere: AppText = AppText.named("segment_browse_none_here")

    /** No segment matches the searched name: here, when This area was searched, or anywhere. */
    fun noResults(state: SegmentBrowseState): AppText =
        AppText.named(
            if (state.offersSearchEverywhere) "segment_browse_no_results_here" else "segment_browse_no_results",
            AppText.written(state.nameQuery),
        )

    val searchEverywhere: AppText = AppText.named("segment_browse_search_everywhere")
}
