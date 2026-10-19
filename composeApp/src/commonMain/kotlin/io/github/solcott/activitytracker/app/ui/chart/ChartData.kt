package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.runtime.Immutable

/**
 * A point of a [TimeSeriesChart]: [x] in seconds from the start (or any unit its `xLabel` reads), [y] already in display units. [gapBefore]
 * when no samples were recorded for a while before it: the chart draws no line or fill into it.
 */
@Immutable data class SeriesPoint(val x: Double, val y: Double, val gapBefore: Boolean = false)

/** One row of [ZoneBars]: [label] like "Z2", [name] like "Endurance", its [range] in words, and [fraction] of the time spent in it. */
@Immutable data class ZoneBar(val label: String, val name: String, val range: String, val time: String, val fraction: Float)

/** [text] written above the point at [x], [y] of a [TimeSeriesChart]. */
@Immutable data class SeriesLabel(val x: Double, val y: Double, val text: String)
