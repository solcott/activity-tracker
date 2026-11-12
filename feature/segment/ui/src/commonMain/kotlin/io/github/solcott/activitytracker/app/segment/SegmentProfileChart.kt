package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.solcott.activitytracker.app.chart.ChartTick
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.chart.GradeBands
import io.github.solcott.activitytracker.app.ui.chart.SeriesPoint
import io.github.solcott.activitytracker.app.ui.chart.TimeSeriesChart
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.data.units.formatDecimal
import kotlin.math.abs
import kotlinx.collections.immutable.toImmutableList

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
    val colors = GradeBands.colors()
    val points = remember(chart) { chart.points.map { SeriesPoint(it.x, it.y, it.gapBefore) }.toImmutableList() }
    val labelsAt = remember(chart) { chart.xTicks.map { it.value }.toImmutableList() }
    val line = remember(chart, colors) { GradeBands.brush(chart.bands, colors) }
    val area = remember(chart, colors) { GradeBands.brush(chart.bands, colors, alpha = AREA_ALPHA) }
    val yLabels = chart.yTicks.map { it.label.string() }
    val xLabels = chart.xTicks.map { it.label.string() }
    val readouts = chart.readouts.map { it.string() }
    TimeSeriesChart(
        points = points,
        xRange = chart.xMin..chart.xMax,
        description = chart.description.string(),
        yLabel = { value -> label(chart.yTicks, yLabels, value) },
        xLabel = { value -> label(chart.xTicks, xLabels, value) },
        markerLabel = { x, _ -> readouts[chart.nearest(x)] },
        modifier = modifier.testTag(SEGMENT_PROFILE_TAG),
        xLabelsAt = labelsAt,
        lineBrush = line,
        areaBrush = area,
        axis = chart.axis,
    )
}

/** The resolved label of the tick in [ticks] at [value] (Vico asks only at the chart's own ticks), or the value itself. */
private fun label(ticks: List<ChartTick>, labels: List<String>, value: Double): String {
    val i = ticks.indexOfFirst { abs(it.value - value) < TICK_MATCH }
    return if (i >= 0) labels[i] else formatDecimal(value, 0)
}

private const val AREA_ALPHA = 0.45f
private const val TICK_MATCH = 1e-6
