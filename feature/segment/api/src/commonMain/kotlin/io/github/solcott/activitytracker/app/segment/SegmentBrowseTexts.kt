package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.browse.BrowseList
import io.github.solcott.activitytracker.app.browse.MapBrowseTexts
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.PathView
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.location.SegmentDirection

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

    /** A name result's line under its name: its length and type ("1.2 mi · Ride"). */
    fun details(segment: SegmentView, units: UnitSystem): AppText = joined(segment, null, units)

    /**
     * A segment group's line under its name (segments/segment-groups): its length, its runs' types, and which ways each goes: Both ways,
     * one way only ("Uphill only"), or, for a mixed group, each wording after its sport groups ("Run, Hike both ways · Ride uphill only").
     * "5.40 mi · Ride, Run, Hike · Both ways".
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

    /**
     * A trail or road's [sports] and which of them go it one way only (segments/segment-browse): "Ride · Downhill only" when every one goes
     * the same one way, else the others plain and then each one-way wording after its sport groups ("Run, Hike · Ride uphill only"); just
     * the sports when none does.
     */
    fun pathSports(path: PathView, sports: List<String>): AppText? =
        pathSportParts(path, sports).reduceOrNull { line, next -> AppText.named("segment_browse_details", line, next) }

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
            BrowseList.Locating -> MapBrowseTexts.locating
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
            if (state.browse.offersSearchEverywhere) "segment_browse_no_results_here" else "segment_browse_no_results",
            AppText.written(state.browse.nameQuery),
        )
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
                listOf(SegmentBrowseTexts.kind(row), StatText.distance(row.path.lengthMeters.toDouble(), units)) +
                    pathSportParts(row.path, sports) +
                    listOfNotNull(
                        AppText.count("path_segment_count", count),
                        metersAway?.let { AppText.named("segment_browse_away", StatText.distance(it.toDouble(), units)) },
                    )
            parts.reduce { line, next -> AppText.named("segment_browse_details", line, next) }
        }
    }

/** [SegmentBrowseTexts.pathSports] as the parts a line joins. */
internal fun pathSportParts(path: PathView, sports: List<String>): List<AppText> {
    val oneWays = path.oneWays.associate { it.sport to SegmentDirection.ofWord(it.direction) }
    val ways = sports.map { SportWay(it, oneWays[it], bothWays = false) }
    return sportsAndWays(ways, plainFirst = true) { AppText.joined(sports.map(SegmentTexts::sportGroup)) }
}

/**
 * [ways]' sport groups with how each goes, as the parts of a line: [types] then the wording once when they all go one way, or every phrase
 * in order joined as one part (both ways, then one way by direction, with the groups that say nothing first when [plainFirst], else last).
 */
private fun sportsAndWays(ways: List<SportWay>, plainFirst: Boolean, types: () -> AppText?): List<AppText> {
    val phrases = ways.groupBy { it.bothWays to it.direction }
    if (phrases.size <= 1) {
        val way = ways.firstOrNull()
        val wording =
            when {
                way == null -> null
                way.bothWays -> AppText.named("segment_browse_both_ways")
                else -> way.direction?.let { AppText.named("segment_one_way_${it.word}") }
            }
        return listOfNotNull(types(), wording)
    }
    val plain = if (plainFirst) Int.MIN_VALUE else Int.MAX_VALUE
    val ordered =
        phrases.entries.sortedBy { (key, _) ->
            val (bothWays, direction) = key
            when {
                bothWays -> -1
                direction != null -> direction.ordinal
                else -> plain
            }
        }
    val parts = ordered.map { (key, phrase) ->
        val sports = checkNotNull(AppText.joined(phrase.map { SegmentTexts.sportGroup(it.sport) }))
        val (bothWays, direction) = key
        when {
            bothWays -> AppText.named("segment_sports_both_ways", sports)
            direction != null -> AppText.named("segment_sports_one_way_${direction.word}", sports)
            else -> sports
        }
    }
    return listOf(parts.reduce { line, next -> AppText.named("segment_browse_details", line, next) })
}

private fun joined(group: SegmentGroupRow, metersAway: Int?, units: UnitSystem): AppText {
    val shown = group.shown
    val length = StatText.distance(shown.distanceMeters.toDouble(), units)
    val types = {
        if (group.sports.size == 1) SegmentTexts.type(shown.record.sport, shown.record.kind)
        else AppText.joined(group.sports.map(SegmentTexts::sportGroup))
    }
    // A name showing ↔ already says both ways, so a lone Both ways isn't said again (segments/segment-groups).
    fun said(way: AppText?) = way.takeUnless { it == BOTH_WAYS && '↔' in SegmentGroupRow.displayName(shown.currentName, group.bothWays) }
    // Rows built before ways were known: the sports, then Both ways when they go both ways.
    if (group.ways.isEmpty()) {
        return joined(length, types(), said(BOTH_WAYS.takeIf { group.bothWays }), metersAway, units)
    }
    // The sports then one wording, or, mixed, each wording after its sport groups, the groups that say nothing last.
    val parts = sportsAndWays(group.ways, plainFirst = false, types)
    return joined(length, parts.getOrNull(0), said(parts.getOrNull(1)), metersAway, units)
}

private val BOTH_WAYS = AppText.named("segment_browse_both_ways")

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
