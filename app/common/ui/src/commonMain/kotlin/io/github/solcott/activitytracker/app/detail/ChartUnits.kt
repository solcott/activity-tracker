package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.chart.SeriesPoint
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.chart_description
import io.github.solcott.activitytracker.composeApp.resources.chart_description_max
import io.github.solcott.activitytracker.composeApp.resources.chart_description_pace
import io.github.solcott.activitytracker.composeApp.resources.chart_marker
import io.github.solcott.activitytracker.composeApp.resources.chart_marker_elevation
import io.github.solcott.activitytracker.data.units.formatDecimal
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/** [series] in the athlete's units, ready to chart. */
@Composable
internal fun rememberPoints(
    series: LineSeries,
    unit: StreamUnit = streamUnit(series.kind),
    ceiling: Double = Double.MAX_VALUE,
): ImmutableList<SeriesPoint> =
    remember(series, unit, ceiling) {
        series.points
            .map { SeriesPoint(it.seconds.toDouble(), unit.convert(it.value).coerceAtMost(ceiling), it.gapBefore) }
            .toImmutableList()
    }

/**
 * Speed (mm/s) as pace: the seconds a kilometer or mile takes, or 100 m or yd swimming. A standstill's pace is infinite, so a chart caps it
 * (`PACE_CEILING`).
 */
@Composable
internal fun paceUnit(kind: PaceKind): StreamUnit {
    val units = LocalUnitSystem.current
    val template = AppText.named(StreamUnits.paceUnitName(kind, units), AppText.written("%s")).string()
    return remember(kind, units, template) {
        StreamUnit(
            convert = { mm -> StreamUnits.paceSeconds(kind, mm, units) },
            text = { template.replace("%s", Format.duration(it.roundToLong())) },
            axis = { Format.duration(it.roundToLong()) },
        )
    }
}

// A paced sport's speed reads as pace, drawn inverted so the faster (lower) pace is at the top.
@Composable internal fun lineUnit(kind: StreamKind, pace: PaceKind?): StreamUnit = pace?.let { paceUnit(it) } ?: streamUnit(kind)

@Composable
internal fun lineTitle(kind: StreamKind, pace: PaceKind?): String =
    (if (pace != null) AppText.named("chart_pace") else StreamUnits.title(kind)).string()

/**
 * What a line's chart shows, for a screen reader: a pace's fastest is its best, not its maximum. A line without an average gives its
 * maximum.
 */
@Composable
internal fun lineDescription(title: String, average: String?, max: String, pace: PaceKind?): String =
    when {
        pace != null -> stringResource(Res.string.chart_description_pace, average.orEmpty(), max)
        average == null -> stringResource(Res.string.chart_description_max, title, max)
        else -> stringResource(Res.string.chart_description, title, average, max)
    }

/** A pace line's cap, [PACE_CEILING] times its average, so stops don't dwarf the rest; none for other lines. */
internal fun paceCeiling(series: LineSeries, unit: StreamUnit, pace: PaceKind?): Double =
    series.average?.takeIf { pace != null }?.let { unit.convert(it) * PACE_CEILING } ?: Double.MAX_VALUE

/** How much slower than its average a pace chart goes: past it, a stop would flatten the rest of the line. */
internal const val PACE_CEILING = 1.5

/** The marker's text: the time and [value], then the elevation there when the chart has it [behind]. */
@Composable
internal fun rememberMarkerLabel(behind: ImmutableList<SeriesPoint>?, value: (Double) -> String): (Double, Double) -> String {
    val marker = stringResource(Res.string.chart_marker, "%1", "%2")
    val withElevation = stringResource(Res.string.chart_marker_elevation, "%1", "%2", "%3")
    val elevation = streamUnit(StreamKind.Elevation)
    return { x, y ->
        val time = Format.duration(x.roundToLong())
        val height = behind?.let { nearestY(it, x) }
        if (height == null) marker.replace("%1", time).replace("%2", value(y))
        else withElevation.replace("%1", time).replace("%2", value(y)).replace("%3", elevation.text(height))
    }
}

/** The y of the point in [points] (sorted by x) closest to [x]. */
private fun nearestY(points: List<SeriesPoint>, x: Double): Double {
    val i = points.binarySearch { it.x.compareTo(x) }
    if (i >= 0) return points[i].y
    val after = -i - 1
    val candidates = listOfNotNull(points.getOrNull(after - 1), points.getOrNull(after))
    return candidates.minBy { abs(it.x - x) }.y
}

/**
 * How a stream's values show: [convert] from the presenter's unit to the athlete's, then [text] with the unit or [axis] without decimals.
 */
internal class StreamUnit(val convert: (Double) -> Double, val text: (Double) -> String, val axis: (Double) -> String)

@Composable
internal fun streamUnit(kind: StreamKind): StreamUnit {
    val units = LocalUnitSystem.current
    val template = AppText.named(StreamUnits.unitName(kind, units), AppText.written("%s")).string()
    return remember(kind, units, template) {
        val decimals = StreamUnits.decimals(kind)
        StreamUnit(
            convert = { StreamUnits.convert(kind, it, units) },
            text = { template.replace("%s", formatDecimal(it, decimals)) },
            axis = { formatDecimal(it, 0) },
        )
    }
}
