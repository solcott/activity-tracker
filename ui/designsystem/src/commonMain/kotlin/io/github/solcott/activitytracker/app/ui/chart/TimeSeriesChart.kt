package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.multiplatform.common.DashedShape
import com.patrykandpatrick.vico.multiplatform.common.Fill
import com.patrykandpatrick.vico.multiplatform.common.component.rememberLineComponent
import com.patrykandpatrick.vico.multiplatform.common.component.rememberTextComponent
import io.github.solcott.activitytracker.data.chart.ChartAxis
import kotlin.math.ceil
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Values over an activity, filled beneath, on the y [axis] a caller chose ([ChartAxis]) with [yLabels] at its gridlines and [xLabels]
 * exactly at their values across [xRange] (none when empty). Every label is resolved by the caller: the chart chooses no text and no tick.
 * [average] draws a reference line. [step] holds each value until the next point, for values like gears. [pointLabels] write values above
 * their points. [backdrop] is drawn as a grey silhouette behind the line, on the same axis (elevation behind an effort chart, already
 * scaled onto it); the marker skips it. Hovering, tapping or dragging across the chart shows [markerLabel] for the nearest point, in a
 * [ChartReadout] over the chart. A line read with larger values lower (a pace, a dive's depth) is given negated, on an axis chosen
 * inverted. [fillAbove] fills from the line up to the top, as a dive's depth reads. [others] are more lines on the same scale, unfilled and
 * in their own colours (another tank, or a dive's ceiling under its depth). [events] mark moments with a labelled vertical line, like a gas
 * switch, and [warnings] mark them in the error colour, labelled along the bottom, like a dive computer's alert. Every line, its fill and
 * the backdrop break, drawing nothing, into a point that follows a gap in the samples ([SeriesPoint.gapBefore]). The backdrop, and the fill
 * of a line with gaps, are [AreaUnderlay]s drawn under Vico's layers, so they cost little more than the line itself. [lineBrush] and
 * [areaBrush] paint the line and its fill across the chart instead of the theme's colour, as an elevation profile is tinted by grade
 * ([GradeBands]); a line with gaps keeps its plain fill.
 */
@Composable
@Suppress("LongParameterList") // A chart's data, its labels and what it draws with them.
fun TimeSeriesChart(
    points: ImmutableList<SeriesPoint>,
    xRange: ClosedFloatingPointRange<Double>,
    description: String,
    axis: ChartAxis,
    yLabels: ImmutableList<AxisLabel>,
    xLabels: ImmutableList<AxisLabel>,
    markerLabel: (x: Double, y: Double) -> String,
    modifier: Modifier = Modifier,
    average: Double? = null,
    step: Boolean = false,
    pointLabels: ImmutableList<SeriesLabel> = persistentListOf(),
    backdrop: ImmutableList<SeriesPoint>? = null,
    fillAbove: Boolean = false,
    others: ImmutableList<ImmutableList<SeriesPoint>> = persistentListOf(),
    events: ImmutableList<SeriesLabel> = persistentListOf(),
    warnings: ImmutableList<SeriesLabel> = persistentListOf(),
    lineBrush: Brush? = null,
    areaBrush: Brush? = null,
    height: Dp = 140.dp,
) {
    if (points.size < 2) return
    val colors = chartColors(FILL_ALPHA)
    val extra = remember(others) { others.filter { it.size >= 2 } }
    val behind = backdrop?.takeIf { it.size >= 2 }
    val gaps = remember(points) { gapsIn(points) }
    // A line with gaps draws its fill apart, under the line, with no area across each gap.
    val separateFill = gaps.isNotEmpty()
    val model = remember(points, extra) { CartesianChartModel(linesModel(listOf(points) + extra)) }
    val minY = axis.min
    val maxY = axis.max
    val connector = if (step) StepConnector else LineCartesianLayer.PointConnector.Sharp
    val areaFill =
        remember(colors, minY, maxY, fillAbove, areaBrush) {
            LineCartesianLayer.AreaFill.single(fillOf(areaBrush, colors.area), splitY = { if (fillAbove) maxY else minY })
        }
    val line =
        LineCartesianLayer.rememberLine(
            fill = remember(colors, lineBrush) { LineCartesianLayer.LineFill.single(fillOf(lineBrush, colors.line)) },
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.5.dp, cap = StrokeCap.Round),
            areaFill = areaFill.takeIf { !separateFill },
            pointConnector = remember(connector, gaps) { if (gaps.isEmpty()) connector else GapConnector(connector, gaps) },
        )
    val rangeProvider =
        remember(minY, maxY, xRange) {
            CartesianLayerRangeProvider.fixed(minX = xRange.start, maxX = xRange.endInclusive, minY = minY, maxY = maxY)
        }
    val fillUnder =
        remember(separateFill, points, minY, maxY, colors, fillAbove, step) {
            if (separateFill) AreaUnderlay(points, minY, maxY, colors.area, fromTop = fillAbove, step = step) else null
        }
    val backdropUnder = behind?.let { rememberBackdrop(it, minY, maxY) }
    val otherLines = rememberOtherLines(remember(extra) { extra.map(::gapsIn).toImmutableList() })
    val frame = remember { ChartFrame() }
    val currentLabel by rememberUpdatedState(markerLabel)
    val readout =
        remember(points, extra, minY, maxY, frame) {
            ChartReadout(frame, points, minY..maxY, extra.map { it.toImmutableList() }.toImmutableList()) { x, y -> currentLabel(x, y) }
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
    val yFormatter = remember(yLabels) { CartesianValueFormatter { _, value, _ -> labelAt(yLabels, value) } }
    val xFormatter = remember(xLabels) { CartesianValueFormatter { _, value, _ -> labelAt(xLabels, value) } }
    val xStep = xRange.endInclusive - xRange.start
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
                    itemPlacer = remember(axis.step) { VerticalAxis.ItemPlacer.step({ axis.step }) },
                ),
            bottomAxis =
                HorizontalAxis.rememberBottom(
                    line = rememberLineComponent(Fill(colors.baseline), thickness = 1.dp),
                    label = label.takeIf { xLabels.isNotEmpty() },
                    valueFormatter = xFormatter,
                    tick = null,
                    guideline = null,
                    itemPlacer = remember(xLabels) { FixedXLabels(xLabels.map { it.value }) },
                ),
            decorations =
                remember(backdropUnder, fillUnder, average, averageLine, labelsAbove, eventMarks, warningMarks, frame) {
                    listOfNotNull(
                        // Under the layers in this order: the backdrop, then the line's fill.
                        backdropUnder,
                        fillUnder,
                        average?.let { avg ->
                            HorizontalLine(y = { avg }, line = averageLine, verticalAxisPosition = Axis.Position.Vertical.Start)
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

/** [brush] as a fill when there is one, else [color]. */
private fun fillOf(brush: Brush?, color: Color): Fill = if (brush != null) Fill(brush) else Fill(color)

private const val FILL_ALPHA = 0.18f
private const val BACKDROP_ALPHA = 0.12f

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

/** [points] as a grey area on the chart's axis, from [minY] to [maxY]: already scaled so the line in front stays readable. */
@Composable
private fun rememberBackdrop(points: ImmutableList<SeriesPoint>, minY: Double, maxY: Double): AreaUnderlay {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(points, minY, maxY, color) { AreaUnderlay(points, minY, maxY, color.copy(alpha = BACKDROP_ALPHA)) }
}

/** How close to an edge, in x, a label counts as at it. */
private const val EDGE = 1e-6

/** Room each fixed x label needs, as a share of the widest label's width, so neighbours keep a gap. */
private const val LABEL_ROOM = 1.3

/**
 * Labels exactly at [values] inside the range, with room past either end for a label near it, so it isn't cut off. When they don't all fit,
 * every nth is kept, always the first and the last, so no two overlap.
 */
private class FixedXLabels(private val values: List<Double>) : HorizontalAxis.ItemPlacer {
    override fun getLabelValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> {
        // One exactly at an edge has no width to draw in.
        val shown = values.filter { it in fullXRange && it > context.ranges.minX + EDGE && it < context.ranges.maxX - EDGE }
        if (shown.isEmpty()) return shown
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
    ): Float {
        val start = context.ranges.minX
        val first = values.filter { it > start + EDGE }.minOrNull() ?: return 0f
        return room(first - start, context, layerDimensions, maxLabelWidth)
    }

    override fun getEndLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float {
        val end = context.ranges.maxX
        val last = values.filter { it < end - EDGE }.maxOrNull() ?: return 0f
        return room(end - last, context, layerDimensions, maxLabelWidth)
    }

    /**
     * Room past the plot for half of a label [distance] from its edge, so it isn't cut off. A label exactly at an edge gets none: Vico
     * gives it no width to draw in, so it isn't drawn (as Compose's charts never drew the start and end of an activity's time).
     */
    private fun room(distance: Double, context: CartesianMeasuringContext, layerDimensions: CartesianLayerDimensions, width: Float): Float {
        val pixels = (distance / context.ranges.xStep * layerDimensions.xSpacing).toFloat()
        return (width / 2 - pixels).coerceAtLeast(0f)
    }
}
