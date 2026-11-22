package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.lexicon.app.GetSegment

/**
 * [output]'s terrain profile as the segment screen draws it (segments/segment-elevation), by [terrainProfileOf]. Null when the AppView sent
 * none, or one too short to draw.
 */
internal fun segmentProfileOf(output: GetSegment.Output): SegmentProfile? =
    output.elevationProfile?.let { terrainProfileOf(it.heights, output.segment.distanceMeters, it.maxGrade) }
