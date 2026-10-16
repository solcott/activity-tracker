package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.Axis
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
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.CartesianMarker
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
 * value until the next point, for values like gears. [pointLabels] write values above their points. [backdrop] is drawn as a grey
 * silhouette behind the line on a scale of its own, as Strava draws elevation behind its charts; it has no axis and the marker skips it.
 * Hovering, tapping or dragging across the chart shows [markerLabel] for the nearest point. [inverted] puts zero at the top and larger
 * values further down, filled up to the top, as a dive's depth reads. [others] are more lines on the same scale, unfilled and in their own
 * colours (another tank, or a dive's ceiling under its depth). [events] mark moments with a labelled vertical line, like a gas switch.
 */
@Composable
internal fun TimeSeriesChart(
    points: ImmutableList<SeriesPoint>,
    xRange: ClosedFloatingPointRange<Double>,
    description: String,
    yLabel: (Double) -> String,
    xLabel: ((Double) -> String)?,
    markerLabel: (x: Double, y: Double) -> String,
    modifier: Modifier = Modifier,
    average: Double? = null,
    step: Boolean = false,
    xLabelsAt: ImmutableList<Double>? = null,
    pointLabels: ImmutableList<SeriesLabel> = persistentListOf(),
    backdrop: ImmutableList<SeriesPoint>? = null,
    inverted: Boolean = false,
    others: ImmutableList<ImmutableList<SeriesPoint>> = persistentListOf(),
    events: ImmutableList<SeriesLabel> = persistentListOf(),
    height: Dp = 140.dp,
) {
    if (points.size < 2) return
    val colors = chartColors(FILL_ALPHA)
    val extra = remember(others) { others.filter { it.size >= 2 } }
    val behind = backdrop?.takeIf { it.size >= 2 }
    // An inverted chart plots each value below zero; flip turns a plotted y back into the value (0.0 - so the surface isn't "-0").
    val flip: (Double) -> Double = if (inverted) { y -> 0.0 - y } else { y -> y }
    val plotted = remember(points, inverted) { if (inverted) points.map { it.copy(y = flip(it.y)) } else points }
    val plottedExtra = remember(extra, inverted) { extra.map { line -> if (inverted) line.map { it.copy(y = flip(it.y)) } else line } }
    // Line layers take their models in order, so the backdrop's comes first and draws underneath.
    val model =
        remember(plotted, plottedExtra, behind) {
            CartesianChartModel(listOfNotNull(behind?.let(::lineModel), linesModel(listOf(plotted) + plottedExtra)))
        }
    val yAxis =
        remember(points, extra, pointLabels.isEmpty(), inverted) { yAxisOf(points + extra.flatten(), pointLabels.isNotEmpty(), inverted) }
    val minY = yAxis.min
    val maxY = yAxis.max
    val line =
        LineCartesianLayer.rememberLine(
            fill = remember(colors) { LineCartesianLayer.LineFill.single(Fill(colors.line)) },
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.5.dp, cap = StrokeCap.Round),
            areaFill =
                remember(colors, minY) { LineCartesianLayer.AreaFill.single(Fill(colors.area), splitY = { if (inverted) maxY else minY }) },
            pointConnector = if (step) StepConnector else LineCartesianLayer.PointConnector.Sharp,
        )
    val otherLines = rememberOtherLines(extra.size)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val label = rememberTextComponent(labelStyle)
    val grid = rememberLineComponent(Fill(colors.grid), thickness = 1.dp)
    val averageLine = rememberLineComponent(Fill(colors.baseline), thickness = 1.dp)
    val yFormatter = remember(yLabel, inverted) { CartesianValueFormatter { _, value, _ -> yLabel(flip(value)) } }
    val xFormatter = remember(xLabel) { CartesianValueFormatter { _, value, _ -> xLabel?.invoke(value).orEmpty() } }
    val xStep = if (xLabelsAt != null) xRange.endInclusive - xRange.start else timeStep(xRange.endInclusive - xRange.start)
    val labelsAbove = remember(pointLabels, label) { PointLabels(pointLabels.map { PointLabel(it.x, it.y, it.text, label) }, gap = 4.dp) }
    val eventLine = rememberLineComponent(Fill(MaterialTheme.colorScheme.tertiary), thickness = 1.dp)
    val eventMarks = remember(events, label, eventLine) { EventMarks(events.map { EventMark(it.x, it.text) }, eventLine, label) }
    val chart =
        rememberCartesianChart(
            layers =
                listOfNotNull(
                        behind?.let { rememberBackdropLayer(it, xRange) },
                        rememberLineCartesianLayer(
                            LineCartesianLayer.LineProvider.series(listOf(line) + otherLines),
                            rangeProvider =
                                remember(minY, maxY, xRange) {
                                    CartesianLayerRangeProvider.fixed(
                                        minX = xRange.start,
                                        maxX = xRange.endInclusive,
                                        minY = minY,
                                        maxY = maxY,
                                    )
                                },
                            // Its own scale, apart from the backdrop's.
                            verticalAxisPosition = Axis.Position.Vertical.Start,
                        ),
                    )
                    .toTypedArray(),
            startAxis =
                VerticalAxis.rememberStart(
                    line = null,
                    label = label,
                    valueFormatter = yFormatter,
                    tick = null,
                    guideline = grid,
                    // Labels on whole steps, so each gridline is at the value its label shows.
                    itemPlacer = remember(yAxis.step) { VerticalAxis.ItemPlacer.step({ yAxis.step }) },
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
                remember(average, averageLine, labelsAbove, eventMarks, inverted) {
                    listOfNotNull(
                        average?.let { avg ->
                            HorizontalLine(y = { flip(avg) }, line = averageLine, verticalAxisPosition = Axis.Position.Vertical.Start)
                        },
                        labelsAbove,
                        eventMarks.takeIf { events.isNotEmpty() },
                    )
                },
            marker = rememberValueMarker(remember(markerLabel, inverted) { { x, y -> markerLabel(x, flip(y)) } }),
            getXStep = { _, _, _ -> xStep },
            markerController = rememberScrubController(),
        )
    StaticChart(chart, model, description, height, modifier)
}

/**
 * A little headroom over [values] (more when [labelled] points carry labels above them), and a floor at zero for values that start there,
 * like power or speed; [inverted] for a chart plotted below zero.
 */
private fun yAxisOf(values: List<SeriesPoint>, labelled: Boolean, inverted: Boolean): YAxis {
    val low = values.minOf { it.y }
    val high = values.maxOf { it.y }
    val pad = ((high - low) * Y_PADDING).coerceAtLeast(1.0)
    val top = if (labelled) (high - low) * LABELLED_Y_PADDING else pad
    val axis = YAxis.around(if (low >= 0 && low - pad < 0) 0.0 else low - pad, high + top)
    return if (inverted) YAxis(-axis.max, -axis.min, axis.step) else axis
}

private const val FILL_ALPHA = 0.18f
private const val BACKDROP_ALPHA = 0.12f
private const val BACKDROP_HEIGHT = 0.6
private const val MIN_BACKDROP_SPAN = 20.0

private fun lineModel(points: List<SeriesPoint>) = linesModel(listOf(points))

private fun linesModel(lines: List<List<SeriesPoint>>) = LineCartesianLayerModel.build {
    lines.forEach { points -> series(points.map { it.x }, points.map { it.y }) }
}

/** [count] more lines, unfilled, each in the next of the theme's other colours. */
@Composable
private fun rememberOtherLines(count: Int): List<LineCartesianLayer.Line> {
    val palette = listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.error)
    return remember(count, palette) {
        List(count) { i ->
            LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(palette[i % palette.size])),
                stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.5.dp, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * [points] as a grey area on the end axis's scale, filling about [BACKDROP_HEIGHT] of the chart from the bottom so the line in front stays
 * readable. A span of at least [MIN_BACKDROP_SPAN] keeps a flat route flat instead of stretching its noise into hills.
 */
@Composable
private fun rememberBackdropLayer(points: ImmutableList<SeriesPoint>, xRange: ClosedFloatingPointRange<Double>): LineCartesianLayer {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(points, xRange, color) {
        val low = points.minOf { it.y }
        val span = (points.maxOf { it.y } - low).coerceAtLeast(MIN_BACKDROP_SPAN)
        val line =
            LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 0.dp),
                areaFill = LineCartesianLayer.AreaFill.single(Fill(color.copy(alpha = BACKDROP_ALPHA)), splitY = { low }),
            )
        BackdropLayer(
            line,
            CartesianLayerRangeProvider.fixed(
                minX = xRange.start,
                maxX = xRange.endInclusive,
                minY = low,
                maxY = low + span / BACKDROP_HEIGHT,
            ),
        )
    }
}

/** A line layer on the end axis's scale with no marker targets, so the marker's dot and readout stay on the line in front. */
private class BackdropLayer(line: LineCartesianLayer.Line, rangeProvider: CartesianLayerRangeProvider) :
    LineCartesianLayer(LineProvider.series(line), rangeProvider = rangeProvider, verticalAxisPosition = Axis.Position.Vertical.End) {
    override val markerTargets: Map<Double, List<CartesianMarker.Target>> = emptyMap()
}

private const val Y_PADDING = 0.08
private const val LABELLED_Y_PADDING = 0.25
private const val Y_LABELS = 4
private const val X_LABELS = 4

/**
 * A y range from [min] to [max] on whole multiples of [step], a round 1, 2 or 5 times a power of ten (at least 1, since labels are whole
 * numbers), with at most [Y_LABELS] gaps.
 */
private data class YAxis(val min: Double, val max: Double, val step: Double) {
    companion object {
        fun around(low: Double, high: Double): YAxis {
            var magnitude = 1.0
            while (true) {
                for (multiple in NICE_STEPS) {
                    val step = multiple * magnitude
                    val min = floor(low / step) * step
                    val max = ceil(high / step) * step
                    if ((max - min) / step <= Y_LABELS) return YAxis(min, max.coerceAtLeast(min + step), step)
                }
                magnitude *= DECADE
            }
        }

        private val NICE_STEPS = listOf(1.0, 2.0, 5.0)
        private const val DECADE = 10
    }
}

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
