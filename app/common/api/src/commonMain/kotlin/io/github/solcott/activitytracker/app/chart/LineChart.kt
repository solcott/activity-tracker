package io.github.solcott.activitytracker.app.chart

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.chart.BandStop
import io.github.solcott.activitytracker.data.chart.ChartAxis
import kotlin.math.abs
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** A point of a [LineChart]: [x] and [y] in the units its labels read. [gapBefore] when nothing was recorded for a while before it. */
@Immutable data class LinePoint(val x: Double, val y: Double, val gapBefore: Boolean = false)

/**
 * A dot on the point at [index] of a [LineChart]: [filled] (the current value) or hollow, with [label] over it when given, kept inside the
 * chart.
 */
@Immutable data class ChartDot(val index: Int, val filled: Boolean, val label: AppText? = null)

/** A labelled place on a [LineChart]'s axis: [value] on the axis and its [label]. */
@Immutable data class ChartTick(val value: Double, val label: AppText)

/**
 * What a line chart shows, chosen once for every UI (dev/shared-display-text): its [points] (sorted by x, at least two), across [xMin] to
 * [xMax], on the y [axis] with a labelled gridline at each of [yTicks], labels along the bottom at [xTicks], the text read out for each
 * point ([readouts], one per point), the [description] assistive technology reads, the [bands] it is tinted by along its width (empty when
 * it isn't), its [average], marked by a dashed line, where it has one, whether it is drawn in [steps] (each value held until the next
 * point, as a threshold holds until it changes), and the [dots] marking some of its points. A chart drawn with its larger values lower (a
 * pace, the faster at the top) is plotted negated, on an axis [ChartAxis.of] chose inverted, its labels reading the values unnegated. A UI
 * only lays it out.
 */
@Immutable
@Suppress("LongParameterList") // A chart's data, its axes' ticks and its texts.
class LineChart(
    val points: ImmutableList<LinePoint>,
    val xMin: Double,
    val xMax: Double,
    val axis: ChartAxis,
    val yTicks: ImmutableList<ChartTick>,
    val xTicks: ImmutableList<ChartTick>,
    val readouts: ImmutableList<AppText>,
    val description: AppText,
    val bands: ImmutableList<BandStop> = persistentListOf(),
    val average: Double? = null,
    val steps: Boolean = false,
    val dots: ImmutableList<ChartDot> = persistentListOf(),
) {
    init {
        require(points.size >= 2) { "a line needs two points" }
        require(readouts.size == points.size) { "one readout per point" }
        require(dots.all { it.index in points.indices }) { "a dot marks a point" }
    }

    /** The index of the point whose x is closest to [x]; the earlier one on a tie. */
    fun nearest(x: Double): Int {
        val found = points.binarySearch { it.x.compareTo(x) }
        if (found >= 0) return found
        val after = -found - 1
        return when {
            after == 0 -> 0
            after == points.size -> points.lastIndex
            abs(points[after].x - x) < abs(points[after - 1].x - x) -> after
            else -> after - 1
        }
    }

    /** The readout for the point nearest [x]. */
    fun readout(x: Double): AppText = readouts[nearest(x)]

    /** The point nearest [x]. */
    fun pointNear(x: Double): LinePoint = points[nearest(x)]

    /** How many band stops tint the chart. */
    val bandCount: Int
        get() = bands.size

    /** The [index]th band stop. */
    fun bandAt(index: Int): BandStop = bands[index]

    /** How many dots mark the chart's points. */
    val dotCount: Int
        get() = dots.size

    /** The [index]th dot. */
    fun dotAt(index: Int): ChartDot = dots[index]

    /** The label of the tick in [ticks] at [value], or null when none is there. */
    fun labelAt(value: Double, ticks: List<ChartTick>): AppText? = ticks.firstOrNull { abs(it.value - value) < TICK_MATCH }?.label

    companion object {
        /**
         * A chart of [points] across [xMin] to [xMax] with labels at [xTicks]: its y axis chosen by [ChartAxis.of] (or [axis]), each
         * gridline labelled by [yLabel].
         */
        @Suppress("LongParameterList") // A chart's data, its axis's labels and its texts.
        fun of(
            points: List<LinePoint>,
            xMin: Double,
            xMax: Double,
            xTicks: List<ChartTick>,
            yLabel: (Double) -> AppText,
            readouts: List<AppText>,
            description: AppText,
            bands: List<BandStop> = emptyList(),
            axis: ChartAxis = ChartAxis.of(points.map { it.y }),
            average: Double? = null,
            steps: Boolean = false,
            dots: List<ChartDot> = emptyList(),
        ): LineChart =
            LineChart(
                points = points.toImmutableList(),
                xMin = xMin,
                xMax = xMax,
                axis = axis,
                yTicks = axis.ticks.map { ChartTick(it, yLabel(it)) }.toImmutableList(),
                xTicks = xTicks.toImmutableList(),
                readouts = readouts.toImmutableList(),
                description = description,
                bands = bands.toImmutableList(),
                average = average,
                steps = steps,
                dots = dots.toImmutableList(),
            )

        private const val TICK_MATCH = 1e-6
    }
}
