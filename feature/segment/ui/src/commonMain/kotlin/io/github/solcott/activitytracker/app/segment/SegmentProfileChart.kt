package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.chart.GradeBands
import io.github.solcott.activitytracker.app.ui.chart.LineChartUi

/** The test tag on a segment's elevation profile. */
const val SEGMENT_PROFILE_TAG = "segmentProfile"

/**
 * A segment's terrain [profile] (segments/segment-elevation), drawn from the chart both UIs draw ([SegmentProfile.chart]): elevation over
 * distance along it in the viewer's units, the line and the area under it tinted by grade ([GradeBands]). Pressing or dragging across it
 * reads out the distance, elevation and grade there.
 */
@Composable
internal fun SegmentProfileChart(profile: SegmentProfile, modifier: Modifier = Modifier) {
    val units = LocalUnitSystem.current
    val chart = remember(profile, units) { profile.chart(units) }
    LineChartUi(chart, modifier.testTag(SEGMENT_PROFILE_TAG))
}
