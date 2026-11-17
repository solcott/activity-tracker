package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import kotlinx.collections.immutable.persistentListOf

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
@Suppress("TooManyFunctions") // Every line browse shows: its choices, its rows' names and details, and its messages.
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
     * A segment group's line under its name (segments/segment-groups): its length, its runs' types, and Both ways when they run both ways
     * ("5.40 mi · Ride, Run, Hike · Both ways").
     */
    fun details(group: SegmentGroupRow, units: UnitSystem): AppText = joined(group, null, units)

    /**
     * A row's line under its name: a trail or road's kind, length, sport groups and how many segment groups it holds ("Trail · 5.40 mi ·
     * Ride, Run, Hike · 3 segments"), or a segment group's line.
     */
    fun details(row: BrowseRow, units: UnitSystem): AppText = joined(row, null, units)

    /** An area row's line under its name: as [details], then how far its line comes to the map's centre ("… · 0.1 mi away"). */
    fun details(run: BrowsedRow, units: UnitSystem): AppText = joined(run.row, run.metersAway, units)

    /** A row's name: a trail or road's, or a segment group's ([groupName]). */
    fun name(row: BrowseRow): AppText =
        when (row) {
            is BrowseRow.Path -> AppText.written(row.path.name)
            is BrowseRow.Group -> groupName(row.group)
        }

    /** What a path is: "Trail" or "Road". */
    fun kind(path: BrowseRow.Path): AppText = AppText.named(if (path.road) "path_kind_road" else "path_kind_trail")

    /**
     * A segment group's row's name (segments/segment-groups): its shown run's current name without the direction the segment importer adds,
     * and, when the row runs both ways, its one "→" read as "↔". A name that doesn't end in a direction is shown as it is.
     */
    fun groupName(group: SegmentGroupRow): AppText = AppText.written(SegmentGroupRow.displayName(group.shown.currentName, group.bothWays))

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

    /**
     * What tapping or clicking a row does, as a screen reader announces it: on a touch screen ([touch]) a row that isn't [selected] shows
     * its line on the map, and the selected one opens; with a pointer every row opens.
     */
    fun rowAction(selected: Boolean, touch: Boolean): AppText =
        AppText.named(if (touch && !selected) "segment_browse_show_on_map" else "segment_browse_open")
}

private fun joined(segment: SegmentView, metersAway: Int?, units: UnitSystem): AppText =
    joined(
        StatText.distance(segment.distanceMeters.toDouble(), units),
        SegmentTexts.type(segment.record.sport, segment.record.kind),
        null,
        metersAway,
        units,
    )

private fun joined(row: BrowseRow, metersAway: Int?, units: UnitSystem): AppText =
    when (row) {
        is BrowseRow.Group -> joined(row.group, metersAway, units)
        is BrowseRow.Path -> {
            // With one sport group chosen, the row says what that group holds where the list knows it; else the whole path's, as served.
            val sports = row.sport?.let(::listOf) ?: row.path.sports
            val count = if (row.sport != null && row.groups.isNotEmpty()) row.groups.size else row.path.groupCount
            val parts =
                listOfNotNull(
                    SegmentBrowseTexts.kind(row),
                    StatText.distance(row.path.lengthMeters.toDouble(), units),
                    AppText.joined(sports.map(SegmentTexts::sportGroup)),
                    AppText.Plural("path_segment_count", count, persistentListOf(AppText.written(formatWhole(count)))),
                    metersAway?.let { AppText.named("segment_browse_away", StatText.distance(it.toDouble(), units)) },
                )
            parts.reduce { line, next -> AppText.named("segment_browse_details", line, next) }
        }
    }

private fun joined(group: SegmentGroupRow, metersAway: Int?, units: UnitSystem): AppText {
    val shown = group.shown
    val types =
        if (group.sports.size == 1) SegmentTexts.type(shown.record.sport, shown.record.kind)
        else AppText.joined(group.sports.map(SegmentTexts::sportGroup))
    val bothWays = AppText.named("segment_browse_both_ways").takeIf { group.bothWays }
    return joined(StatText.distance(shown.distanceMeters.toDouble(), units), types, bothWays, metersAway, units)
}

private fun joined(length: AppText, types: AppText?, bothWays: AppText?, metersAway: Int?, units: UnitSystem): AppText {
    val parts =
        listOfNotNull(
            length,
            types,
            bothWays,
            metersAway?.let { AppText.named("segment_browse_away", StatText.distance(it.toDouble(), units)) },
        )
    return parts.reduce { line, next -> AppText.named("segment_browse_details", line, next) }
}
