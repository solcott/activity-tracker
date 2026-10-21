package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.multiplatform.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.multiplatform.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.multiplatform.common.Fill
import com.patrykandpatrick.vico.multiplatform.common.component.rememberLineComponent
import io.github.solcott.activitytracker.app.record.DepthPreviewPoint
import io.github.solcott.activitytracker.app.ui.chart.ChartFrame
import io.github.solcott.activitytracker.app.ui.chart.ChartReadout
import io.github.solcott.activitytracker.app.ui.chart.SeriesPoint
import io.github.solcott.activitytracker.app.ui.chart.StaticChart
import io.github.solcott.activitytracker.app.ui.chart.WholeRangeXStep
import io.github.solcott.activitytracker.app.ui.chart.chartColors
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.chart_marker
import io.github.solcott.activitytracker.composeApp.resources.unit_elevation_ft
import io.github.solcott.activitytracker.composeApp.resources.unit_elevation_m
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatDecimal
import kotlin.math.roundToLong
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * A dive's depth over time: the surface along the top, deeper further down, filled beneath the surface line. Hovering, tapping or dragging
 * reads out the time and depth there.
 */
@Composable
fun DepthProfile(profile: ImmutableList<DepthPreviewPoint>, description: String, modifier: Modifier = Modifier, height: Dp = 160.dp) {
    if (profile.size < 2) return
    val colors = chartColors(FILL_ALPHA)
    // Depth is plotted below zero, so the surface is the top of the chart and the fill reaches up to it.
    val model =
        remember(profile) {
            val first = profile.first().seconds
            CartesianChartModel(
                LineCartesianLayerModel.build { series(profile.map { it.seconds - first }, profile.map { -it.depthMeters }) }
            )
        }
    val deepest = remember(profile) { profile.maxOf { it.depthMeters }.coerceAtLeast(1.0) }
    val line =
        LineCartesianLayer.rememberLine(
            fill = remember(colors) { LineCartesianLayer.LineFill.single(Fill(colors.line)) },
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp, cap = StrokeCap.Round),
            areaFill = remember(colors) { LineCartesianLayer.AreaFill.single(Fill(colors.area)) },
        )
    val surface = rememberLineComponent(Fill(colors.baseline), thickness = 1.dp)
    val frame = remember { ChartFrame() }
    val label by rememberUpdatedState(rememberDepthLabel())
    val readout =
        remember(profile, deepest, frame) {
            val first = profile.first().seconds
            val points = profile.map { SeriesPoint((it.seconds - first).toDouble(), -it.depthMeters) }.toImmutableList()
            ChartReadout(frame, points, -deepest..0.0) { x, y -> label(x, y) }
        }
    val chart =
        rememberCartesianChart(
            rememberLineCartesianLayer(
                LineCartesianLayer.LineProvider.series(line),
                rangeProvider = remember(deepest) { CartesianLayerRangeProvider.fixed(minY = -deepest, maxY = 0.0) },
            ),
            decorations = remember(surface, frame) { listOf(HorizontalLine(y = { 0.0 }, line = surface), frame) },
            getXStep = WholeRangeXStep,
        )
    StaticChart(chart, model, description, height, modifier, readout)
}

/** The marker's text for a point plotted at -depth: the time into the dive and the depth there. */
@Composable
private fun rememberDepthLabel(): (Double, Double) -> String {
    val units = LocalUnitSystem.current
    val marker = stringResource(Res.string.chart_marker, "%1", "%2")
    val depth = stringResource(if (units == UnitSystem.Metric) Res.string.unit_elevation_m else Res.string.unit_elevation_ft, "%s")
    return remember(units, marker, depth) {
        { x, y ->
            val value = formatDecimal(UnitConversions.toElevationUnit(-y, units), DEPTH_DECIMALS)
            marker.replace("%1", Format.duration(x.roundToLong())).replace("%2", depth.replace("%s", value))
        }
    }
}

private const val FILL_ALPHA = 0.2f
private const val DEPTH_DECIMALS = 1
