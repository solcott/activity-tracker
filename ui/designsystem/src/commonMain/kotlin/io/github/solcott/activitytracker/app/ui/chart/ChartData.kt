package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.runtime.Immutable
import kotlin.math.abs

/**
 * A point of a [TimeSeriesChart]: [x] in seconds from the start (or any unit its `xLabel` reads), [y] already in display units. [gapBefore]
 * when no samples were recorded for a while before it: the chart draws no line or fill into it.
 */
@Immutable data class SeriesPoint(val x: Double, val y: Double, val gapBefore: Boolean = false)

/** One row of [ZoneBars]: [label] like "Z2", [name] like "Endurance", its [range] in words, and [fraction] of the time spent in it. */
/** A zone's row as words already chosen: its [title] ("Z2 Endurance"), range, time, [percent] and the [spoken] sentence for the row. */
@Immutable
data class ZoneBar(val title: String, val range: String, val time: String, val percent: String, val spoken: String, val fraction: Float)

/** [text] written above the point at [x], [y] of a [TimeSeriesChart]. */
@Immutable data class SeriesLabel(val x: Double, val y: Double, val text: String)

/** A label on a chart's axis: its [text] at [value], already resolved. */
@Immutable data class AxisLabel(val value: Double, val text: String)

/**
 * The text of the label in [labels] at [value], or a blank. Vico asks only at the chart's own ticks, and refuses an empty label, which a
 * string resource is on the web until resources load: the chart then shows nothing there rather than a number of its own.
 */
internal fun labelAt(labels: List<AxisLabel>, value: Double): String =
    labels.firstOrNull { abs(it.value - value) < LABEL_MATCH }?.text?.ifEmpty { null } ?: BLANK_LABEL

private const val LABEL_MATCH = 1e-6
internal const val BLANK_LABEL = " "
