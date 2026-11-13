package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import io.github.solcott.activitytracker.app.chart.ChartTick
import io.github.solcott.activitytracker.app.chart.LineChart
import io.github.solcott.activitytracker.app.chart.LinePoint
import io.github.solcott.activitytracker.app.detail.timeFormat
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.load
import io.github.solcott.activitytracker.app.ui.string
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * [chart] as the shared UI draws it, on Vico's [TimeSeriesChart], as the native iOS `LineChartView` draws it on Swift Charts
 * (dev/shared-display-text): every label, readout and the description are the chart's, and its axis, average, steps, backdrop, other lines,
 * marks and fill are drawn as given. A chart with bands is tinted by grade ([GradeBands]). It chooses no text and no tick of its own.
 */
@Composable
fun LineChartUi(chart: LineChart, modifier: Modifier = Modifier) {
    val points = remember(chart) { chart.points.toSeries() }
    val yLabels = axisLabels(chart.yTicks)
    val xLabels = axisLabels(chart.xTicks)
    // A readout for every point is too many texts to read in a composition: they are read once, off it, and shown when ready. A preview
    // (and layoutlib's screenshots, which can't read resources off a composition) shows no readout, so reads none.
    val zone = LocalTimeZone.current
    val format = timeFormat()
    val preview = LocalInspectionMode.current
    val readouts by
        produceState<List<String>?>(null, chart, zone, format, preview) {
            if (!preview) value = chart.readouts.map { it.load(zone, format) }
        }
    val events = marks(chart, warning = false)
    val warnings = marks(chart, warning = true)
    val others = remember(chart) { chart.others.map { it.points.toSeries() }.toImmutableList() }
    val backdrop = remember(chart) { chart.backdrop.takeIf { it.isNotEmpty() }?.toSeries() }
    val tinted = chart.bands.isNotEmpty()
    val colors = GradeBands.colors()
    val line = remember(chart, colors) { if (tinted) GradeBands.brush(chart.bands, colors) else null }
    val area = remember(chart, colors) { if (tinted) GradeBands.brush(chart.bands, colors, alpha = TINTED_AREA_ALPHA) else null }
    TimeSeriesChart(
        points = points,
        xRange = chart.xMin..chart.xMax,
        description = chart.description.string(),
        axis = chart.axis,
        yLabels = yLabels,
        xLabels = xLabels,
        markerLabel = { x, _ -> readouts?.getOrNull(chart.nearest(x)).orEmpty() },
        modifier = modifier,
        average = chart.average,
        step = chart.steps,
        backdrop = backdrop,
        fillAbove = chart.fillAbove,
        others = others,
        events = events,
        warnings = warnings,
        lineBrush = line,
        areaBrush = area,
    )
}

/** [ticks] with their labels read. */
@Composable
private fun axisLabels(ticks: ImmutableList<ChartTick>): ImmutableList<AxisLabel> {
    val texts = ticks.map { it.label.string() }
    return remember(ticks, texts) { ticks.mapIndexed { i, tick -> AxisLabel(tick.value, texts[i]) }.toImmutableList() }
}

/** [chart]'s marks, its warnings or the rest, with their labels read. */
@Composable
private fun marks(chart: LineChart, warning: Boolean): ImmutableList<SeriesLabel> {
    val marks = chart.marks.filter { it.warning == warning }
    val texts = marks.map { it.label.string() }
    return remember(chart, warning, texts) { marks.mapIndexed { i, mark -> SeriesLabel(mark.x, 0.0, texts[i]) }.toImmutableList() }
}

private fun List<LinePoint>.toSeries(): ImmutableList<SeriesPoint> = map { SeriesPoint(it.x, it.y, it.gapBefore) }.toImmutableList()

private const val TINTED_AREA_ALPHA = 0.45f
