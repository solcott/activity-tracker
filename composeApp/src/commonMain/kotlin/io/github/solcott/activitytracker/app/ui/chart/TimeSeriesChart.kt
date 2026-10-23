package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
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
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.multiplatform.common.DashedShape
import com.patrykandpatrick.vico.multiplatform.common.Fill
import com.patrykandpatrick.vico.multiplatform.common.component.rememberLineComponent
import com.patrykandpatrick.vico.multiplatform.common.component.rememberTextComponent
import io.github.solcott.activitytracker.data.units.formatDecimal
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Values over an activity, filled beneath, with a few y labels from [yLabel] and, when [xLabel] is set, time labels at whole-minute steps
 * across [xRange] (seconds), or exactly at [xLabelsAt] when the x axis isn't time. [average] draws a reference line. [step] holds each
 * value until the next point, for values like gears. [pointLabels] write values above their points. [backdrop] is drawn as a grey
 * silhouette behind the line on a scale of its own, as Strava draws elevation behind its charts; it has no axis and the marker skips it.
 * Hovering, tapping or dragging across the chart shows [markerLabel] for the nearest point, in a [ChartReadout] over the chart. [inverted]
 * puts zero at the top and larger values further down, filled up to the top, as a dive's depth reads. [others] are more lines on the same
 * scale, unfilled and in their own colours (another tank, or a dive's ceiling under its depth). [events] mark moments with a labelled
 * vertical line, like a gas switch, and [warnings] mark them in the error colour, labelled along the bottom, like a dive computer's alert.
 * Every line, its fill and the backdrop break, drawing nothing, into a point that follows a gap in the samples ([SeriesPoint.gapBefore]).
 * The backdrop, and the fill of a line with gaps, are [AreaUnderlay]s drawn under Vico's layers, so they cost little more than the line
 * itself.
 */
@Composable
fun TimeSeriesChart(
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
    fillAbove: Boolean = inverted,
    timeValues: Boolean = false,
    others: ImmutableList<ImmutableList<SeriesPoint>> = persistentListOf(),
    events: ImmutableList<SeriesLabel> = persistentListOf(),
    warnings: ImmutableList<SeriesLabel> = persistentListOf(),
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
    val gaps = remember(points) { gapsIn(points) }
    // A line with gaps draws its fill apart, under the line, with no area across each gap.
    val separateFill = gaps.isNotEmpty()
    val model = remember(plotted, plottedExtra) { CartesianChartModel(linesModel(listOf(plotted) + plottedExtra)) }
    val yAxis =
        remember(points, extra, pointLabels.isEmpty(), inverted, timeValues) {
            yAxisOf(points + extra.flatten(), pointLabels.isNotEmpty(), inverted, timeValues)
        }
    val minY = yAxis.min
    val maxY = yAxis.max
    val connector = if (step) StepConnector else LineCartesianLayer.PointConnector.Sharp
    val areaFill =
        remember(colors, minY, maxY, fillAbove) {
            LineCartesianLayer.AreaFill.single(Fill(colors.area), splitY = { if (fillAbove) maxY else minY })
        }
    val line =
        LineCartesianLayer.rememberLine(
            fill = remember(colors) { LineCartesianLayer.LineFill.single(Fill(colors.line)) },
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.5.dp, cap = StrokeCap.Round),
            areaFill = areaFill.takeIf { !separateFill },
            pointConnector = remember(connector, gaps) { if (gaps.isEmpty()) connector else GapConnector(connector, gaps) },
        )
    val rangeProvider =
        remember(minY, maxY, xRange) {
            CartesianLayerRangeProvider.fixed(minX = xRange.start, maxX = xRange.endInclusive, minY = minY, maxY = maxY)
        }
    val fillUnder =
        remember(separateFill, plotted, minY, maxY, colors, fillAbove, step) {
            if (separateFill) AreaUnderlay(plotted, minY, maxY, colors.area, fromTop = fillAbove, step = step) else null
        }
    val backdropUnder = behind?.let { rememberBackdrop(it) }
    val otherLines = rememberOtherLines(remember(extra) { extra.map(::gapsIn).toImmutableList() })
    val frame = remember { ChartFrame() }
    val currentLabel by rememberUpdatedState(markerLabel)
    val readout =
        remember(plotted, plottedExtra, minY, maxY, inverted, frame) {
            ChartReadout(
                frame,
                plotted.toImmutableList(),
                minY..maxY,
                plottedExtra.map { it.toImmutableList() }.toImmutableList(),
            ) { x, y ->
                currentLabel(x, flip(y))
            }
        }
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val label = rememberTextComponent(labelStyle)
    val grid = rememberLineComponent(Fill(colors.grid), thickness = 1.dp)
    // Dashed, so the average reads as a reference rather than another series.
    val averageLine =
        rememberLineComponent(
            Fill(colors.baseline),
            thickness = 1.dp,
            shape = remember { DashedShape(dashLength = 4.dp, gapLength = 3.dp) },
        )
    // Vico rejects an empty label, and on web a label built from a string resource is empty until resources load: show the number then.
    val yFormatter =
        remember(yLabel, inverted) {
            CartesianValueFormatter { _, value, _ -> yLabel(flip(value)).ifEmpty { formatDecimal(flip(value), 0) } }
        }
    val xFormatter =
        remember(xLabel) { CartesianValueFormatter { _, value, _ -> xLabel?.invoke(value)?.ifEmpty { null } ?: formatDecimal(value, 0) } }
    val xStep = if (xLabelsAt != null) xRange.endInclusive - xRange.start else timeStep(xRange.endInclusive - xRange.start)
    val labelsAbove = remember(pointLabels, label) { PointLabels(pointLabels.map { PointLabel(it.x, it.y, it.text, label) }, gap = 4.dp) }
    val eventLine = rememberLineComponent(Fill(MaterialTheme.colorScheme.tertiary), thickness = 1.dp)
    val eventMarks = remember(events, label, eventLine) { EventMarks(events.map { EventMark(it.x, it.text) }, eventLine, label) }
    val warningColor = MaterialTheme.colorScheme.error
    val warningLine = rememberLineComponent(Fill(warningColor), thickness = 1.dp)
    val warningLabel = rememberTextComponent(labelStyle.copy(color = warningColor))
    val warningMarks =
        remember(warnings, warningLabel, warningLine) {
            EventMarks(warnings.map { EventMark(it.x, it.text) }, warningLine, warningLabel, atBottom = true)
        }
    val chart =
        rememberCartesianChart(
            rememberLineCartesianLayer(
                LineCartesianLayer.LineProvider.series(listOf(line) + otherLines),
                rangeProvider = rangeProvider,
                verticalAxisPosition = Axis.Position.Vertical.Start,
            ),
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
                remember(backdropUnder, fillUnder, average, averageLine, labelsAbove, eventMarks, warningMarks, inverted, frame) {
                    listOfNotNull(
                        // Under the layers in this order: the backdrop, then the line's fill.
                        backdropUnder,
                        fillUnder,
                        average?.let { avg ->
                            HorizontalLine(y = { flip(avg) }, line = averageLine, verticalAxisPosition = Axis.Position.Vertical.Start)
                        },
                        labelsAbove,
                        eventMarks.takeIf { events.isNotEmpty() },
                        warningMarks.takeIf { warnings.isNotEmpty() },
                        frame,
                    )
                },
            getXStep = { _, _, _ -> xStep },
        )
    StaticChart(chart, model, description, height, modifier, readout)
}

/**
 * A little headroom over [values] (more when [labelled] points carry labels above them), and a floor at zero for values that start there,
 * like power or speed; [inverted] for a chart plotted below zero. [timeValues] (a pace) step by whole seconds or minutes and have no floor.
 */
private fun yAxisOf(values: List<SeriesPoint>, labelled: Boolean, inverted: Boolean, timeValues: Boolean): YAxis {
    val low = values.minOf { it.y }
    val high = values.maxOf { it.y }
    val pad = ((high - low) * Y_PADDING).coerceAtLeast(1.0)
    val top = if (labelled) (high - low) * LABELLED_Y_PADDING else pad
    val axis =
        if (timeValues) YAxis.aroundTime(low - pad, high + top)
        else YAxis.around(if (low >= 0 && low - pad < 0) 0.0 else low - pad, high + top)
    return if (inverted) YAxis(-axis.max, -axis.min, axis.step) else axis
}

private const val FILL_ALPHA = 0.18f
private const val BACKDROP_ALPHA = 0.12f
private const val BACKDROP_HEIGHT = 0.6
private const val MIN_BACKDROP_SPAN = 20.0

private fun linesModel(lines: List<List<SeriesPoint>>) = LineCartesianLayerModel.build {
    lines.forEach { points -> series(points.map { it.x }, points.map { it.y }) }
}

/** More lines, one breaking at each of [gaps], unfilled, each in the next of the theme's other colours. */
@Composable
private fun rememberOtherLines(gaps: ImmutableList<ImmutableList<Double>>): List<LineCartesianLayer.Line> {
    val palette = listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.error)
    return remember(gaps, palette) {
        gaps.mapIndexed { i, at ->
            LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(palette[i % palette.size])),
                stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.5.dp, cap = StrokeCap.Round),
                pointConnector =
                    if (at.isEmpty()) LineCartesianLayer.PointConnector.Sharp
                    else GapConnector(LineCartesianLayer.PointConnector.Sharp, at),
            )
        }
    }
}

/**
 * [points] as a grey area on a scale of its own, filling about [BACKDROP_HEIGHT] of the chart from the bottom so the line in front stays
 * readable. A span of at least [MIN_BACKDROP_SPAN] keeps a flat route flat instead of stretching its noise into hills.
 */
@Composable
private fun rememberBackdrop(points: ImmutableList<SeriesPoint>): AreaUnderlay {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(points, color) {
        val low = points.minOf { it.y }
        val span = (points.maxOf { it.y } - low).coerceAtLeast(MIN_BACKDROP_SPAN)
        AreaUnderlay(points, low, low + span / BACKDROP_HEIGHT, color.copy(alpha = BACKDROP_ALPHA))
    }
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

        /** Like [around], on a step a clock reads easily: 5, 10, 15 or 30 seconds, or 1, 2, 5, 10, 15 or 30 minutes. */
        fun aroundTime(low: Double, high: Double): YAxis {
            for (step in TIME_VALUE_STEPS) {
                val min = floor(low / step) * step
                val max = ceil(high / step) * step
                if ((max - min) / step <= Y_LABELS) return YAxis(min, max.coerceAtLeast(min + step), step)
            }
            return around(low, high)
        }

        private val NICE_STEPS = listOf(1.0, 2.0, 5.0)
        private val TIME_VALUE_STEPS = listOf(5.0, 10.0, 15.0, 30.0, 60.0, 120.0, 300.0, 600.0, 900.0, 1800.0)
        private const val DECADE = 10
    }
}

/** About [X_LABELS] labels across [seconds], on a whole number of minutes or hours. */
private fun timeStep(seconds: Double): Double =
    TIME_STEPS.firstOrNull { it * X_LABELS >= seconds } ?: (ceil(seconds / X_LABELS / HOUR) * HOUR)

private const val HOUR = 3600.0

/** Room each fixed x label needs, as a share of the widest label's width, so neighbours keep a gap. */
private const val LABEL_ROOM = 1.3
private val TIME_STEPS = listOf(60.0, 120.0, 300.0, 600.0, 900.0, 1200.0, 1800.0, 3600.0, 7200.0)

/**
 * Labels exactly at [values], with half a label of room at each end so one at the edge isn't cut off. When they don't all fit, every nth is
 * kept, always the first and the last, so no two overlap.
 */
private class FixedXLabels(private val values: List<Double>) : HorizontalAxis.ItemPlacer {
    override fun getLabelValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> {
        val shown = values.filter { it in fullXRange }
        val span = fullXRange.endInclusive - fullXRange.start
        val closest = shown.zipWithNext { a, b -> b - a }.minOrNull()?.takeIf { span > 0 }
        val every = closest?.let { ceil(maxLabelWidth * LABEL_ROOM / (it * context.layerBounds.width / span)).toInt() } ?: 1
        return everyNth(shown, every)
    }

    /** Every [n]th of [shown] from the first, and the last, which takes the place of a kept one too close before it. */
    private fun everyNth(shown: List<Double>, n: Int): List<Double> {
        if (n <= 1) return shown
        val kept = shown.filterIndexed { i, _ -> i % n == 0 }.toMutableList()
        if (kept.last() != shown.last()) {
            if (kept.size > 1 && shown.size - 1 - shown.indexOf(kept.last()) < n) kept.removeAt(kept.lastIndex)
            kept += shown.last()
        }
        return kept
    }

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
