package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.PathKind
import io.github.solcott.activitytracker.lexicon.app.PathView

/** What the Trail page says (segments/path-page, dev/shared-display-text), chosen here for the shared and the native UI. */
object PathTexts {
    /** The page's title before the path loads. */
    val title: AppText = AppText.named("path_title")

    /** What the path is: "Trail" or "Road". */
    fun kind(path: PathView): AppText = AppText.named(if (path.kind == PathKind.ROAD) "path_kind_road" else "path_kind_trail")

    /**
     * The line under the path's name: its kind, length and sport groups, with which go it one way only as a trail's row says
     * (segments/path-page): "Trail · 5.40 mi · Ride, Run, Hike", "Trail · 4.70 mi · Ride · Downhill only".
     */
    fun summary(path: PathView, units: UnitSystem): AppText =
        (listOf(kind(path), StatText.distance(path.lengthMeters.toDouble(), units)) + pathSportParts(path, path.sports)).reduce { line, next
            ->
            AppText.named("segment_browse_details", line, next)
        }

    /** A segment group's row: its name, as browse names it. */
    fun name(group: SegmentGroupRow): AppText = SegmentBrowseTexts.groupName(group)

    /** A segment group's line under its name, as browse shows it ("1.20 mi · Ride, Run · Both ways"). */
    fun details(group: SegmentGroupRow, units: UnitSystem): AppText = SegmentBrowseTexts.details(group, units)

    /** No segment on the path that the athlete may see. */
    val noneHere: AppText = AppText.named("path_none_here")

    /** Lists every sport group's segments, while narrowed to one. */
    val showAllSports: AppText = AppText.named("path_show_all_sports")
}
