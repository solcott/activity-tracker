package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.chart.SeriesPoint
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.chart_cadence
import io.github.solcott.activitytracker.composeApp.resources.chart_depth
import io.github.solcott.activitytracker.composeApp.resources.chart_elevation
import io.github.solcott.activitytracker.composeApp.resources.chart_grade
import io.github.solcott.activitytracker.composeApp.resources.chart_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.chart_marker
import io.github.solcott.activitytracker.composeApp.resources.chart_marker_elevation
import io.github.solcott.activitytracker.composeApp.resources.chart_power
import io.github.solcott.activitytracker.composeApp.resources.chart_speed
import io.github.solcott.activitytracker.composeApp.resources.chart_temperature
import io.github.solcott.activitytracker.composeApp.resources.unit_bpm
import io.github.solcott.activitytracker.composeApp.resources.unit_elevation_ft
import io.github.solcott.activitytracker.composeApp.resources.unit_elevation_m
import io.github.solcott.activitytracker.composeApp.resources.unit_percent
import io.github.solcott.activitytracker.composeApp.resources.unit_rpm
import io.github.solcott.activitytracker.composeApp.resources.unit_speed_kmh
import io.github.solcott.activitytracker.composeApp.resources.unit_speed_mph
import io.github.solcott.activitytracker.composeApp.resources.unit_temperature_c
import io.github.solcott.activitytracker.composeApp.resources.unit_temperature_f
import io.github.solcott.activitytracker.composeApp.resources.unit_watts
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatDecimal
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** [series] in the athlete's units, ready to chart. */
@Composable
internal fun rememberPoints(series: LineSeries): ImmutableList<SeriesPoint> {
    val unit = streamUnit(series.kind)
    return remember(series, unit) { series.points.map { SeriesPoint(it.seconds.toDouble(), unit.convert(it.value)) }.toImmutableList() }
}

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

internal fun streamTitle(kind: StreamKind): StringResource =
    when (kind) {
        StreamKind.HeartRate -> Res.string.chart_heart_rate
        StreamKind.Power -> Res.string.chart_power
        StreamKind.Speed -> Res.string.chart_speed
        StreamKind.Cadence -> Res.string.chart_cadence
        StreamKind.Elevation -> Res.string.chart_elevation
        StreamKind.Grade -> Res.string.chart_grade
        StreamKind.Temperature -> Res.string.chart_temperature
        StreamKind.Depth -> Res.string.chart_depth
    }

/**
 * How a stream's values show: [convert] from the presenter's unit to the athlete's, then [text] with the unit or [axis] without decimals.
 */
internal class StreamUnit(val convert: (Double) -> Double, val text: (Double) -> String, val axis: (Double) -> String)

@Composable
internal fun streamUnit(kind: StreamKind): StreamUnit {
    val units = LocalUnitSystem.current
    val template =
        stringResource(
            when (kind) {
                StreamKind.HeartRate -> Res.string.unit_bpm
                StreamKind.Power -> Res.string.unit_watts
                StreamKind.Cadence -> Res.string.unit_rpm
                StreamKind.Grade -> Res.string.unit_percent
                StreamKind.Speed -> if (units == UnitSystem.Metric) Res.string.unit_speed_kmh else Res.string.unit_speed_mph
                StreamKind.Temperature -> if (units == UnitSystem.Metric) Res.string.unit_temperature_c else Res.string.unit_temperature_f
                StreamKind.Elevation,
                StreamKind.Depth -> if (units == UnitSystem.Metric) Res.string.unit_elevation_m else Res.string.unit_elevation_ft
            },
            "%s",
        )
    return remember(kind, units, template) {
        val convert: (Double) -> Double =
            when (kind) {
                StreamKind.Speed -> { mm ->
                    mm / MM_PER_M * SECONDS_PER_HOUR / UnitConversions.metersPerDistanceUnit(units)
                }
                StreamKind.Temperature -> { tenths ->
                    UnitConversions.toTemperatureUnit(tenths.roundToLong(), units)
                }
                StreamKind.Elevation,
                StreamKind.Depth -> { m ->
                    UnitConversions.toElevationUnit(m, units)
                }
                else -> { v ->
                    v
                }
            }
        val decimals = if (kind == StreamKind.Speed || kind == StreamKind.Grade) 1 else 0
        StreamUnit(
            convert = convert,
            text = { template.replace("%s", formatDecimal(it, decimals)) },
            axis = { formatDecimal(it, 0) },
        )
    }
}

private const val MM_PER_M = 1000.0
private const val SECONDS_PER_HOUR = 3600.0
