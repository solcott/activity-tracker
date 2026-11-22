package io.github.solcott.activitytracker.app.segment

import kotlin.math.roundToInt
import kotlinx.collections.immutable.toImmutableList

/** How far either side of a point its grade is measured over, in meters: about 50 m in all, as terrain is smoothed. */
private const val GRADE_HALF_WIDTH_M = 25.0

private const val DECIMETERS_PER_METER = 10.0
private const val PERCENT = 100.0
private const val TENTHS_PER_PERCENT = 10.0

/**
 * A terrain profile as the AppView serves one, [heightsDm] in decimeters evenly spaced over [distanceMeters], as both pages draw it
 * (segments/segment-elevation, segments/path-profile): heights in meters, each point's grade between the points about [GRADE_HALF_WIDTH_M]
 * either side, and [maxGradeTenths] in percent when the AppView sent one. Null when it is too short to draw.
 */
fun terrainProfileOf(heightsDm: List<Int>, distanceMeters: Long, maxGradeTenths: Int?): SegmentProfile? {
    val heights = heightsDm.map { it / DECIMETERS_PER_METER }
    if (heights.size < 2 || distanceMeters <= 0) return null
    val spacing = distanceMeters.toDouble() / (heights.size - 1)
    val reach = (GRADE_HALF_WIDTH_M / spacing).roundToInt().coerceAtLeast(1)
    val grades =
        heights.indices.map { i ->
            val behind = (i - reach).coerceAtLeast(0)
            val ahead = (i + reach).coerceAtMost(heights.lastIndex)
            (heights[ahead] - heights[behind]) / ((ahead - behind) * spacing) * PERCENT
        }
    return SegmentProfile(
        heights.toImmutableList(),
        distanceMeters,
        grades.toImmutableList(),
        maxGradeTenths?.let { it / TENTHS_PER_PERCENT },
    )
}
