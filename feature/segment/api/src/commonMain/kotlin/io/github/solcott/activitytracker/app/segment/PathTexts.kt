package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.PathKind
import io.github.solcott.activitytracker.lexicon.app.PathView
import kotlinx.collections.immutable.persistentListOf

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

    /** A section's row: the section's name, as the importer names it ("Through the Trees Trail"). */
    fun name(section: PathListItem.Section): AppText = AppText.written(section.name)

    /**
     * A section's line under its name, as a segment group's row reads, then how many rows it holds: "4.16 mi · Ride · Downhill only · 4
     * segments".
     */
    fun details(section: PathListItem.Section, units: UnitSystem): AppText =
        AppText.named(
            "segment_browse_details",
            SegmentBrowseTexts.details(section.summary, units),
            AppText.Plural("path_segment_count", section.rows.size, persistentListOf(AppText.written(formatWhole(section.rows.size)))),
        )

    /** What a section's row does when tapped, for assistive technology: "Collapse Through the Trees Trail", "Expand …". */
    fun toggleLabel(section: PathListItem.Section, expanded: Boolean): AppText =
        AppText.named(if (expanded) "path_section_collapse" else "path_section_expand", AppText.written(section.name))

    /** The all-sections control: "Collapse all" while every section is expanded, else "Expand all". */
    fun allSections(allExpanded: Boolean): AppText =
        AppText.named(if (allExpanded) "path_sections_collapse_all" else "path_sections_expand_all")

    /** No segment on the path that the athlete may see. */
    val noneHere: AppText = AppText.named("path_none_here")

    /** Lists every sport group's segments, while narrowed to one. */
    val showAllSports: AppText = AppText.named("path_show_all_sports")
}
