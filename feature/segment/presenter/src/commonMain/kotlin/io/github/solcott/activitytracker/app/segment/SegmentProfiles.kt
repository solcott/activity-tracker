package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.lexicon.app.GetSegment
import kotlin.math.roundToInt
import kotlinx.collections.immutable.toImmutableList

/** How far either side of a point its grade is measured over, in meters: about 50 m in all, as terrain is smoothed. */
private const val GRADE_HALF_WIDTH_M = 25.0

private const val DECIMETERS_PER_METER = 10.0
private const val PERCENT = 100.0

/**
 * [output]'s terrain profile as the segment screen draws it (segments/segment-elevation): heights in meters, and each point's grade between
 * the points about [GRADE_HALF_WIDTH_M] either side, with the AppView's maximum grade in percent when it sent one. Null when the AppView
 * sent none, or one too short to draw.
 */
internal fun segmentProfileOf(output: GetSegment.Output): SegmentProfile? {
    val heights = output.elevationProfile?.heights?.map { it / DECIMETERS_PER_METER }.orEmpty()
    val distance = output.segment.distanceMeters
    if (heights.size < 2 || distance <= 0) return null
    val spacing = distance.toDouble() / (heights.size - 1)
    val reach = (GRADE_HALF_WIDTH_M / spacing).roundToInt().coerceAtLeast(1)
    val grades =
        heights.indices.map { i ->
            val behind = (i - reach).coerceAtLeast(0)
            val ahead = (i + reach).coerceAtMost(heights.lastIndex)
            (heights[ahead] - heights[behind]) / ((ahead - behind) * spacing) * PERCENT
        }
    val maxGrade = output.elevationProfile?.maxGrade?.let { it / TENTHS_PER_PERCENT }
    return SegmentProfile(heights.toImmutableList(), distance, grades.toImmutableList(), maxGrade)
}

private const val TENTHS_PER_PERCENT = 10.0
