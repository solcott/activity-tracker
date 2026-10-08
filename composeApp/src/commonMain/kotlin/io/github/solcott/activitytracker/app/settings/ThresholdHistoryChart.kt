package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.multiplatform.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.multiplatform.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.CartesianLayerPadding
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.multiplatform.common.Fill
import com.patrykandpatrick.vico.multiplatform.common.component.rememberLineComponent
import com.patrykandpatrick.vico.multiplatform.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.multiplatform.common.component.rememberTextComponent
import com.patrykandpatrick.vico.multiplatform.common.data.ExtraStore
import io.github.solcott.activitytracker.app.ui.chart.PointLabel
import io.github.solcott.activitytracker.app.ui.chart.PointLabels
import io.github.solcott.activitytracker.app.ui.chart.StaticChart
import io.github.solcott.activitytracker.app.ui.chart.StepConnector
import io.github.solcott.activitytracker.app.ui.chart.WholeRangeXStep
import io.github.solcott.activitytracker.app.ui.chart.chartColors
import kotlinx.collections.immutable.ImmutableList

/**
 * [chart] as a step line, since a threshold holds until it changes, filled beneath and running on to today at the right edge. [labels] are
 * the points' values as shown (one per point); without them it's a sparkline with no dots, for the collapsed overview.
 */
@Composable
internal fun ThresholdStepChart(
    chart: ThresholdChart,
    description: String,
    modifier: Modifier = Modifier,
    labels: ImmutableList<String>? = null,
    height: Dp = 150.dp,
) {
    val detailed = labels != null
    val colors = chartColors(FILL_ALPHA)
    // Room above the highest point for its label (or a sparkline's even inset), in the 0–1 units of the points.
    val (top, bottom) = if (detailed) DETAILED_TOP to DETAILED_BOTTOM else SPARK_INSET to SPARK_INSET
    val span = 1.0 / (1 - top - bottom)
    val minY = -bottom * span
    val maxY = 1 + top * span
    val model =
        remember(chart) {
            val points = chart.points
            // The last value holds until today, at the right edge.
            val xs = points.map { it.x.toDouble() } + if (points.last().x < 1f) listOf(1.0) else emptyList()
            val ys = points.map { it.y.toDouble() } + if (points.last().x < 1f) listOf(points.last().y.toDouble()) else emptyList()
            CartesianChartModel(LineCartesianLayerModel.build { series(xs, ys) })
        }

    val hollow = MaterialTheme.colorScheme.surfaceContainerLowest
    val currentDot =
        LineCartesianLayer.Point(
            rememberShapeComponent(Fill(colors.line), CircleShape, strokeFill = Fill(colors.line), strokeThickness = 2.dp),
            size = 14.dp,
        )
    val pastDot =
        LineCartesianLayer.Point(
            rememberShapeComponent(Fill(hollow), CircleShape, strokeFill = Fill(colors.line), strokeThickness = 2.dp),
            size = 10.dp,
        )
    val dots = remember(chart, currentDot, pastDot) { if (detailed) ThresholdDots(chart, currentDot, pastDot) else null }

    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
    val currentLabel = rememberTextComponent(labelStyle.copy(color = MaterialTheme.colorScheme.onSurface))
    val pastLabel = rememberTextComponent(labelStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
    val grid = rememberLineComponent(Fill(colors.grid), thickness = 1.dp)
    val decorations =
        remember(chart, labels, currentLabel, pastLabel, grid, minY, maxY) {
            if (labels == null) {
                emptyList()
            } else {
                GRID_LINES.map { fromTop -> HorizontalLine(y = { maxY - fromTop * (maxY - minY) }, line = grid) } +
                    PointLabels(
                        chart.points.mapIndexed { i, point ->
                            PointLabel(point.x.toDouble(), point.y.toDouble(), labels[i], if (point.current) currentLabel else pastLabel)
                        },
                        gap = 9.dp,
                    )
            }
        }

    val line =
        LineCartesianLayer.rememberLine(
            fill = remember(colors) { LineCartesianLayer.LineFill.single(Fill(colors.line)) },
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = if (detailed) 2.25.dp else 1.5.dp),
            areaFill = remember(colors, minY) { LineCartesianLayer.AreaFill.single(Fill(colors.area), splitY = { minY }) },
            pointProvider = dots,
            pointConnector = StepConnector,
        )
    // Inset from the sides so edge dots and labels aren't clipped.
    val inset = if (detailed) 10.dp else 0.dp
    val vicoChart =
        rememberCartesianChart(
            rememberLineCartesianLayer(
                LineCartesianLayer.LineProvider.series(line),
                rangeProvider =
                    remember(minY, maxY) { CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = 1.0, minY = minY, maxY = maxY) },
            ),
            layerPadding = remember(inset) { { CartesianLayerPadding(unscalableStart = inset, unscalableEnd = inset) } },
            decorations = decorations,
            getXStep = WholeRangeXStep,
        )
    StaticChart(vicoChart, model, description, height, modifier)
}

/** A filled dot on the current value and hollow ones on past values; none on the run-on to today. */
@Immutable
private data class ThresholdDots(
    private val chart: ThresholdChart,
    private val currentDot: LineCartesianLayer.Point,
    private val pastDot: LineCartesianLayer.Point,
) : LineCartesianLayer.PointProvider {
    private val current = chart.points.filter { it.current }.map { it.x.toDouble() }.toSet()
    private val past = chart.points.filterNot { it.current }.map { it.x.toDouble() }.toSet()

    override fun getPoint(entry: LineCartesianLayerModel.Entry, seriesIndex: Int, extraStore: ExtraStore) =
        when (entry.x) {
            in current -> currentDot
            in past -> pastDot
            else -> null
        }

    override fun getLargestPoint(extraStore: ExtraStore) = currentDot
}

private const val FILL_ALPHA = 0.14f
private val GRID_LINES = listOf(0.25, 0.5, 0.75)
private const val DETAILED_TOP = 0.22
private const val DETAILED_BOTTOM = 0.16
private const val SPARK_INSET = 0.12
