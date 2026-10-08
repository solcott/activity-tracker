package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.multiplatform.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.multiplatform.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.CartesianLayerDimensions
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.multiplatform.common.Fill
import com.patrykandpatrick.vico.multiplatform.common.component.rememberLineComponent
import com.patrykandpatrick.vico.multiplatform.common.component.rememberTextComponent
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Values over an activity, filled beneath, with a few y labels from [yLabel] and, when [xLabel] is set, time labels at whole-minute steps
 * across [xRange] (seconds), or exactly at [xLabelsAt] when the x axis isn't time. [average] draws a reference line. [step] holds each
 * value until the next point, for values like gears. [pointLabels] write values above their points.
 */
@Composable
internal fun TimeSeriesChart(
    points: ImmutableList<SeriesPoint>,
    xRange: ClosedFloatingPointRange<Double>,
    description: String,
    yLabel: (Double) -> String,
    xLabel: ((Double) -> String)?,
    modifier: Modifier = Modifier,
    average: Double? = null,
    step: Boolean = false,
    xLabelsAt: ImmutableList<Double>? = null,
    pointLabels: ImmutableList<SeriesLabel> = persistentListOf(),
    height: Dp = 140.dp,
) {
    if (points.size < 2) return
    val colors = chartColors(FILL_ALPHA)
    val model = remember(points) { CartesianChartModel(LineCartesianLayerModel.build { series(points.map { it.x }, points.map { it.y }) }) }
    // A little headroom (more for labels above the points), and a floor at zero for values that start there, like power or speed.
    val (minY, maxY) =
        remember(points, pointLabels.isEmpty()) {
            val low = points.minOf { it.y }
            val high = points.maxOf { it.y }
            val pad = ((high - low) * Y_PADDING).coerceAtLeast(1.0)
            val top = if (pointLabels.isEmpty()) pad else (high - low) * LABELLED_Y_PADDING
            val floor = if (low >= 0 && low - pad < 0) 0.0 else floor(low - pad)
            floor to ceil(high + top)
        }
    val line =
        LineCartesianLayer.rememberLine(
            fill = remember(colors) { LineCartesianLayer.LineFill.single(Fill(colors.line)) },
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.5.dp, cap = StrokeCap.Round),
            areaFill = remember(colors, minY) { LineCartesianLayer.AreaFill.single(Fill(colors.area), splitY = { minY }) },
            pointConnector = if (step) StepConnector else LineCartesianLayer.PointConnector.Sharp,
        )
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val label = rememberTextComponent(labelStyle)
    val grid = rememberLineComponent(Fill(colors.grid), thickness = 1.dp)
    val averageLine = rememberLineComponent(Fill(colors.baseline), thickness = 1.dp)
    val yFormatter = remember(yLabel) { CartesianValueFormatter { _, value, _ -> yLabel(value) } }
    val xFormatter = remember(xLabel) { CartesianValueFormatter { _, value, _ -> xLabel?.invoke(value).orEmpty() } }
    val xStep = if (xLabelsAt != null) xRange.endInclusive - xRange.start else timeStep(xRange.endInclusive - xRange.start)
    val labelsAbove = remember(pointLabels, label) { PointLabels(pointLabels.map { PointLabel(it.x, it.y, it.text, label) }, gap = 4.dp) }
    val chart =
        rememberCartesianChart(
            rememberLineCartesianLayer(
                LineCartesianLayer.LineProvider.series(line),
                rangeProvider =
                    remember(minY, maxY, xRange) {
                        CartesianLayerRangeProvider.fixed(minX = xRange.start, maxX = xRange.endInclusive, minY = minY, maxY = maxY)
                    },
            ),
            startAxis =
                VerticalAxis.rememberStart(
                    line = null,
                    label = label,
                    valueFormatter = yFormatter,
                    tick = null,
                    guideline = grid,
                    itemPlacer = remember { VerticalAxis.ItemPlacer.count({ Y_LABELS }) },
                ),
            bottomAxis =
                HorizontalAxis.rememberBottom(
                    line = rememberLineComponent(Fill(colors.baseline), thickness = 1.dp),
                    label = label.takeIf { xLabel != null },
                    valueFormatter = xFormatter,
                    tick = null,
                    guideline = null,
                    itemPlacer =
                        remember(xLabelsAt) {
                            xLabelsAt?.let(::FixedXLabels) ?: HorizontalAxis.ItemPlacer.aligned(addExtremeLabelPadding = false)
                        },
                ),
            decorations =
                remember(average, averageLine, labelsAbove) {
                    listOfNotNull(average?.let { avg -> HorizontalLine(y = { avg }, line = averageLine) }, labelsAbove)
                },
            getXStep = { _, _, _ -> xStep },
        )
    StaticChart(chart, model, description, height, modifier)
}

private const val FILL_ALPHA = 0.18f
private const val Y_PADDING = 0.08
private const val LABELLED_Y_PADDING = 0.25
private const val Y_LABELS = 4
private const val X_LABELS = 4

/** About [X_LABELS] labels across [seconds], on a whole number of minutes or hours. */
private fun timeStep(seconds: Double): Double =
    TIME_STEPS.firstOrNull { it * X_LABELS >= seconds } ?: (ceil(seconds / X_LABELS / HOUR) * HOUR)

private const val HOUR = 3600.0
private val TIME_STEPS = listOf(60.0, 120.0, 300.0, 600.0, 900.0, 1200.0, 1800.0, 3600.0, 7200.0)

/** Labels exactly at [values], with half a label of room at each end so one at the edge isn't cut off. */
private class FixedXLabels(private val values: List<Double>) : HorizontalAxis.ItemPlacer {
    override fun getLabelValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> = values.filter { it in fullXRange }

    override fun getWidthMeasurementLabelValues(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
    ): List<Double> = values.filter { it in fullXRange }

    override fun getHeightMeasurementLabelValues(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> = values.filter { it in fullXRange }

    override fun getLineValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> = emptyList()

    override fun getStartLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = maxLabelWidth / 2

    override fun getEndLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = maxLabelWidth / 2
}
