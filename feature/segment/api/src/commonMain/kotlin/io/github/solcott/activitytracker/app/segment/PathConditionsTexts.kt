package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.lexicon.app.GetPathConditions
import io.github.solcott.activitytracker.lexicon.app.TrailCondition
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.TimeZone

/** The words the Trail page's Conditions card shows (segments/trail-reports), which both UIs resolve; its lines are [TrailReportTexts]'. */
object PathConditionsTexts {
    /** How many photos the Trail card's strip shows: one more than a segment's. */
    const val STRIP = TrailReportTexts.STRIP + 1

    /** "Reports on 3 of 8 segments, last 14 days". */
    fun summary(withReports: Int, segments: Int): AppText =
        AppText.Plural("path_conditions_summary", segments, persistentListOf(AppText.whole(withReports), AppText.whole(segments)))

    /** The Trail page's Conditions card from [rollUp] at [now], or null when no segment has reports or photos. */
    fun card(rollUp: GetPathConditions.Output, now: Instant, zone: TimeZone): PathConditionsCard? {
        if (rollUp.segments.isEmpty() && rollUp.photoCount == 0) return null
        val photos = rollUp.photos.take(STRIP)
        return PathConditionsCard(
            summary = summary(rollUp.segments.size, rollUp.segmentCount),
            segments = rollUp.segments.map { row(it, now, zone) }.toImmutableList(),
            photos = photos.map(TrailReportTexts::thumb).toImmutableList(),
            morePhotos = morePhotos(rollUp.photoCount, photos.size, STRIP),
            seeAll = seeAllPhotos(rollUp.photoCount, photos.size),
        )
    }

    /** One segment's row: its two leading conditions ("Closed · Downed tree"), riders, and when it was closed. */
    private fun row(segment: GetPathConditions.SegmentConditions, now: Instant, zone: TimeZone): PathConditionRow {
        val c = segment.conditions
        val closed = c.lead == TrailCondition.CLOSED
        val shown = listOfNotNull(c.lead) + c.tags.map { it.value }.filter { it != TrailCondition.CLOSED || !closed }
        return PathConditionRow(
            uri = segment.segment.value,
            name = segment.name,
            lead =
                shown.take(2).map(TrailReportTexts::tag).reduceOrNull { first, next ->
                    AppText.named("path_segment_condition", first, next)
                },
            riders = TrailReportTexts.leadLine(c, now, zone),
            closed = closed,
            closedLine =
                c.latestAt
                    ?.takeIf { closed }
                    ?.let {
                        AppText.named(
                            "path_segment_condition",
                            TrailReportTexts.tag(TrailCondition.CLOSED),
                            TrailReportTexts.age(it, now, zone),
                        )
                    },
        )
    }
}
