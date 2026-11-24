package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.index.TRAIL_REPORT_CURRENT_WINDOW
import io.github.solcott.activitytracker.lexicon.app.TrailCondition
import io.github.solcott.activitytracker.lexicon.app.TrailConditionTag

/** One trail report as the conditions summary reads it (segments/trail-reports): who, when it was first indexed, its tags and note. */
internal data class ReportFacts(val uri: String, val author: String, val indexedAt: Long, val tags: List<String>, val note: String?)

/**
 * A segment's conditions from its [ReportFacts], before hydration: [lead] and the riders behind it, every other current tag with its
 * riders, the newest current report with a note ([latestNote], a URI), and, with nothing current, the newest older tagged report.
 */
internal data class ConditionsSummary(
    val lead: String?,
    val leadRiders: Int?,
    val riders: Int,
    val latestAt: Long?,
    val tags: List<TrailConditionTag>,
    val latestNote: String?,
    val lastReportAt: Long?,
    val lastReportTags: List<String>,
)

/**
 * Summarises [reports] (already only those the viewer may see) at [now]: reports with tags in the last [TRAIL_REPORT_CURRENT_WINDOW] count,
 * one per rider (their newest). The lead is closed when the newest counted report names it, else the surface tag the most riders name, the
 * newest such report breaking a tie. [unlisted] riders (the viewer's mutes) still count but don't give [ConditionsSummary.latestNote].
 */
internal fun summarizeConditions(reports: List<ReportFacts>, now: Long, unlisted: Set<String> = emptySet()): ConditionsSummary {
    val since = now - TRAIL_REPORT_CURRENT_WINDOW.inWholeMilliseconds
    val newestFirst = reports.sortedWith(compareByDescending<ReportFacts> { it.indexedAt }.thenByDescending { it.uri })
    val current = newestFirst.filter { it.indexedAt >= since }
    val counted = current.filter { it.tags.isNotEmpty() }.distinctBy { it.author }
    val ridersByTag = TrailCondition.all.associateWith { tag -> counted.count { tag in it.tags } }.filterValues { it > 0 }
    val lead =
        when {
            counted.isEmpty() -> null
            TrailCondition.CLOSED in counted.first().tags -> TrailCondition.CLOSED
            else ->
                TrailCondition.surface
                    .filter { it in ridersByTag }
                    .maxWithOrNull(
                        compareBy<String> { ridersByTag.getValue(it) }.thenBy { tag -> -counted.indexOfFirst { tag in it.tags } },
                    )
        }
    val others =
        ridersByTag
            .filterKeys { it != lead }
            .map { (tag, riders) -> TrailConditionTag(tag, riders) }
            .sortedWith(compareByDescending<TrailConditionTag> { it.riders }.thenBy { TrailCondition.all.indexOf(it.value) })
    val last = if (counted.isEmpty()) newestFirst.firstOrNull { it.tags.isNotEmpty() } else null
    return ConditionsSummary(
        lead = lead,
        leadRiders = lead?.let(ridersByTag::get),
        riders = counted.size,
        latestAt = counted.firstOrNull()?.indexedAt,
        tags = others,
        latestNote = current.firstOrNull { !it.note.isNullOrBlank() && it.author !in unlisted }?.uri,
        lastReportAt = last?.indexedAt,
        lastReportTags = last?.tags.orEmpty(),
    )
}
