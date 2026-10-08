package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import io.github.solcott.activitytracker.app.ui.chart.StaticChart
import io.github.solcott.activitytracker.app.ui.chart.WholeRangeXStep
import io.github.solcott.activitytracker.app.ui.chart.chartColors
import kotlinx.collections.immutable.ImmutableList

/** A dive's depth over time: the surface along the top, deeper further down, filled beneath the surface line. */
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
    val chart =
        rememberCartesianChart(
            rememberLineCartesianLayer(
                LineCartesianLayer.LineProvider.series(line),
                rangeProvider = remember(deepest) { CartesianLayerRangeProvider.fixed(minY = -deepest, maxY = 0.0) },
            ),
            decorations = remember(surface) { listOf(HorizontalLine(y = { 0.0 }, line = surface)) },
            getXStep = WholeRangeXStep,
        )
    StaticChart(chart, model, description, height, modifier)
}

private const val FILL_ALPHA = 0.2f
