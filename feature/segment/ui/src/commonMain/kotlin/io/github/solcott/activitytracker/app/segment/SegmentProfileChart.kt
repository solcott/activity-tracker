package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.chart.GradeBands
import io.github.solcott.activitytracker.app.ui.chart.SeriesPoint
import io.github.solcott.activitytracker.app.ui.chart.TimeSeriesChart
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.segment_profile_description
import io.github.solcott.activitytracker.composeApp.resources.segment_profile_readout
import io.github.solcott.activitytracker.composeApp.resources.unit_distance_km
import io.github.solcott.activitytracker.composeApp.resources.unit_distance_mi
import io.github.solcott.activitytracker.composeApp.resources.unit_elevation_ft
import io.github.solcott.activitytracker.composeApp.resources.unit_elevation_m
import io.github.solcott.activitytracker.composeApp.resources.unit_percent
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatDecimal
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/** The test tag on a segment's elevation profile. */
const val SEGMENT_PROFILE_TAG = "segmentProfile"

/**
 * A segment's terrain [profile] (segments/segment-elevation): elevation over distance along it, both in the viewer's units, the line and
 * the area under it tinted by grade ([GradeBands]). Pressing or dragging across it reads out the distance, elevation and grade there.
 */
@Composable
internal fun SegmentProfileChart(profile: SegmentProfile, modifier: Modifier = Modifier) {
    val units = LocalUnitSystem.current
    val metric = units == UnitSystem.Metric
    val distanceTemplate = stringResource(if (metric) Res.string.unit_distance_km else Res.string.unit_distance_mi, "%s")
    val elevationTemplate = stringResource(if (metric) Res.string.unit_elevation_m else Res.string.unit_elevation_ft, "%s")
    val percentTemplate = stringResource(Res.string.unit_percent, "%s")
    val readoutTemplate = stringResource(Res.string.segment_profile_readout, "%1", "%2", "%3")
    val colors = GradeBands.colors()
    val drawn =
        remember(profile, units, colors) {
            val length = UnitConversions.toDistanceUnit(profile.distanceMeters.toDouble(), units)
            val step = if (profile.heightsM.size > 1) length / (profile.heightsM.size - 1) else 0.0
            val points =
                profile.heightsM.mapIndexed { i, h -> SeriesPoint(i * step, UnitConversions.toElevationUnit(h, units)) }.toImmutableList()
            val stops = GradeBands.stops(points.map { (it.x / length).toFloat() }, profile.gradeBands)
            Drawn(
                points = points,
                length = length,
                labelsAt = distanceLabels(length).toImmutableList(),
                line = GradeBands.brush(stops, colors),
                area = GradeBands.brush(stops, colors, alpha = AREA_ALPHA),
            )
        }
    fun distance(value: Double) = distanceTemplate.replace("%s", formatDecimal(value, distanceDecimals(drawn.length)))
    fun elevation(value: Double) = elevationTemplate.replace("%s", formatDecimal(value, 0))
    val description =
        stringResource(
            Res.string.segment_profile_description,
            Format.distance(profile.distanceMeters.toDouble()),
            Format.elevation(profile.heightsM.first()),
            Format.elevation(profile.heightsM.last()),
        )
    TimeSeriesChart(
        points = drawn.points,
        xRange = 0.0..drawn.length,
        description = description,
        yLabel = ::elevation,
        xLabel = ::distance,
        markerLabel = { x, y ->
            val i = if (drawn.length > 0) (x / drawn.length * (profile.gradesPercent.size - 1)).roundToInt() else 0
            val grade = profile.gradesPercent.getOrElse(i.coerceIn(0, profile.gradesPercent.lastIndex)) { 0.0 }
            readoutTemplate
                .replace("%1", distance(x))
                .replace("%2", elevation(y))
                .replace("%3", percentTemplate.replace("%s", formatDecimal(grade, 1)))
        },
        modifier = modifier.testTag(SEGMENT_PROFILE_TAG),
        xLabelsAt = drawn.labelsAt,
        lineBrush = drawn.line,
        areaBrush = drawn.area,
    )
}

/** What the chart draws, worked out once per profile, units and theme. */
private class Drawn(
    val points: ImmutableList<SeriesPoint>,
    val length: Double,
    val labelsAt: ImmutableList<Double>,
    val line: Brush,
    val area: Brush,
)

/** Distance labels from 0 at a round step that gives at most [MAX_LABELS] of them along [length], in the viewer's distance unit. */
internal fun distanceLabels(length: Double): List<Double> {
    if (length <= 0) return listOf(0.0)
    val step = LABEL_STEPS.firstOrNull { length / it <= MAX_LABELS } ?: LABEL_STEPS.last()
    return (0..floor(length / step).toInt()).map { it * step }
}

/** How many decimals a distance label needs at the step [distanceLabels] picks for [length]. */
private fun distanceDecimals(length: Double): Int {
    val step = LABEL_STEPS.firstOrNull { length / it <= MAX_LABELS } ?: LABEL_STEPS.last()
    return when {
        step >= 1 -> 0
        (step * TENTHS).roundToLong() / TENTHS == step -> 1
        else -> 2
    }
}

private val LABEL_STEPS = listOf(0.1, 0.2, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0)
private const val MAX_LABELS = 5
private const val TENTHS = 10.0
private const val AREA_ALPHA = 0.45f
