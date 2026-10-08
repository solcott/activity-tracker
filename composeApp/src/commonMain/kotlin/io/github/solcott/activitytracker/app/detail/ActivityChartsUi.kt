package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.settings.zoneName
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.VisibilityPicker
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.chart.SeriesLabel
import io.github.solcott.activitytracker.app.ui.chart.SeriesPoint
import io.github.solcott.activitytracker.app.ui.chart.TimeSeriesChart
import io.github.solcott.activitytracker.app.ui.chart.ZoneBar
import io.github.solcott.activitytracker.app.ui.chart.ZoneBars
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.chart_avg_max
import io.github.solcott.activitytracker.composeApp.resources.chart_cadence
import io.github.solcott.activitytracker.composeApp.resources.chart_depth
import io.github.solcott.activitytracker.composeApp.resources.chart_description
import io.github.solcott.activitytracker.composeApp.resources.chart_elevation
import io.github.solcott.activitytracker.composeApp.resources.chart_gear_value
import io.github.solcott.activitytracker.composeApp.resources.chart_gears
import io.github.solcott.activitytracker.composeApp.resources.chart_gears_help
import io.github.solcott.activitytracker.composeApp.resources.chart_grade
import io.github.solcott.activitytracker.composeApp.resources.chart_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.chart_heart_rate_zones
import io.github.solcott.activitytracker.composeApp.resources.chart_marker
import io.github.solcott.activitytracker.composeApp.resources.chart_power
import io.github.solcott.activitytracker.composeApp.resources.chart_power_curve
import io.github.solcott.activitytracker.composeApp.resources.chart_power_curve_help
import io.github.solcott.activitytracker.composeApp.resources.chart_power_curve_point
import io.github.solcott.activitytracker.composeApp.resources.chart_power_zones
import io.github.solcott.activitytracker.composeApp.resources.chart_speed
import io.github.solcott.activitytracker.composeApp.resources.chart_temperature
import io.github.solcott.activitytracker.composeApp.resources.chart_zone_label
import io.github.solcott.activitytracker.composeApp.resources.chart_zone_range
import io.github.solcott.activitytracker.composeApp.resources.chart_zone_range_open
import io.github.solcott.activitytracker.composeApp.resources.charts_heading
import io.github.solcott.activitytracker.composeApp.resources.charts_missing_zones
import io.github.solcott.activitytracker.composeApp.resources.charts_set_thresholds
import io.github.solcott.activitytracker.composeApp.resources.charts_visibility_heading
import io.github.solcott.activitytracker.composeApp.resources.charts_visibility_help
import io.github.solcott.activitytracker.composeApp.resources.unit_bpm
import io.github.solcott.activitytracker.composeApp.resources.unit_duration_hours
import io.github.solcott.activitytracker.composeApp.resources.unit_duration_minutes
import io.github.solcott.activitytracker.composeApp.resources.unit_duration_seconds
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
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The activity's charts, in Strava's order: each line with its zones after it, then the power curve and gears. */
internal fun LazyListScope.activityCharts(state: ActivityDetailState.Loaded) {
    val charts = state.charts ?: return
    item(key = "charts-heading") { Text(stringResource(Res.string.charts_heading), style = MaterialTheme.typography.titleMedium) }
    if (state.isOwn) {
        item(key = "charts-visibility") { ChartsVisibility(charts.visibility, state.savingChartsVisibility, state.eventSink) }
    }
    if (state.chartsMissingZones) {
        item(key = "charts-missing-zones") { MissingZones(state.eventSink) }
    }
    for (series in charts.lines) {
        item(key = "line-${series.kind}") { LineChart(series, charts.durationSeconds) }
        when (series.kind) {
            StreamKind.HeartRate -> {
                charts.heartRateZones?.let { zones ->
                    item(key = "zones-hr") { Zones(Res.string.chart_heart_rate_zones, zones, Res.string.unit_bpm) }
                }
            }

            StreamKind.Power -> {
                if (charts.powerCurve.size >= 2) item(key = "power-curve") { PowerCurve(charts.powerCurve) }
                charts.powerZones?.let { zones ->
                    item(key = "zones-power") { Zones(Res.string.chart_power_zones, zones, Res.string.unit_watts) }
                }
            }

            else -> {}
        }
    }
    if (charts.gears.size >= 2) item(key = "gears") { Gears(charts.gears, charts.durationSeconds) }
}

@Composable
private fun ChartsVisibility(visibility: String, saving: Boolean, eventSink: (ActivityDetailEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.charts_visibility_heading), style = MaterialTheme.typography.labelLarge)
        VisibilityPicker(
            visibility,
            onSelect = { eventSink(ActivityDetailEvent.SetChartsVisibility(it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving,
        )
        Text(
            stringResource(Res.string.charts_visibility_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MissingZones(eventSink: (ActivityDetailEvent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.charts_missing_zones),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppTextButton(onClick = { eventSink(ActivityDetailEvent.OpenTrainingZones) }) {
            Text(stringResource(Res.string.charts_set_thresholds))
        }
    }
}

@Composable
private fun ChartHeader(title: String, detail: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun LineChart(series: LineSeries, durationSeconds: Int) {
    val unit = streamUnit(series.kind)
    val title = stringResource(streamTitle(series.kind))
    val average = series.average?.let { unit.text(unit.convert(it)) }
    val max = series.max?.let { unit.text(unit.convert(it)) }
    val detail = if (average != null && max != null) stringResource(Res.string.chart_avg_max, average, max) else null
    val marker = stringResource(Res.string.chart_marker, "%1", "%2")
    val points =
        remember(series, unit) { series.points.map { SeriesPoint(it.seconds.toDouble(), unit.convert(it.value)) }.toImmutableList() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChartHeader(title, detail)
        TimeSeriesChart(
            points = points,
            xRange = 0.0..durationSeconds.toDouble(),
            description = stringResource(Res.string.chart_description, title, average.orEmpty(), max.orEmpty()),
            yLabel = unit.axis,
            xLabel = { Format.duration(it.roundToLong()) },
            markerLabel = { x, y -> marker.replace("%1", Format.duration(x.roundToLong())).replace("%2", unit.text(y)) },
            average = series.average?.let(unit.convert),
        )
    }
}

@Composable
private fun Zones(title: StringResource, zones: ImmutableList<ZoneTime>, unit: StringResource) {
    val total = zones.sumOf { it.seconds }.coerceAtLeast(1)
    val bars = zones.map { zone ->
        ZoneBar(
            label = stringResource(Res.string.chart_zone_label, zone.label),
            name = stringResource(zoneName(zone.name)),
            range =
                stringResource(
                    unit,
                    zone.high?.let { stringResource(Res.string.chart_zone_range, zone.low, it) }
                        ?: stringResource(Res.string.chart_zone_range_open, zone.low),
                ),
            time = Format.duration(zone.seconds.toLong()),
            fraction = zone.seconds.toFloat() / total,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChartHeader(stringResource(title), null)
        ZoneBars(bars.toImmutableList())
    }
}

@Composable
private fun PowerCurve(curve: ImmutableList<CurvePoint>) {
    val watts = stringResource(Res.string.unit_watts, "")
    // A log scale, so 5 s and 1 h both get room.
    val points = remember(curve) { curve.map { SeriesPoint(log10(it.seconds.toDouble()), it.watts.toDouble()) }.toImmutableList() }
    val labelled = remember(curve) { curve.filter { it.seconds in CURVE_STATS } }
    val seconds = stringResource(Res.string.unit_duration_seconds, "%s")
    val minutes = stringResource(Res.string.unit_duration_minutes, "%s")
    val hours = stringResource(Res.string.unit_duration_hours, "%s")
    val durationLabel = { s: Int ->
        when {
            s < MINUTE -> seconds.replace("%s", "$s")
            s < HOUR -> minutes.replace("%s", "${s / MINUTE}")
            else -> hours.replace("%s", "${s / HOUR}")
        }
    }
    val marker = stringResource(Res.string.chart_marker, "%1", "%2")
    val wattsTemplate = stringResource(Res.string.unit_watts, "%s")
    val wattsAt = labelled.map { durationLabel(it.seconds) to stringResource(Res.string.unit_watts, it.watts) }
    val described = wattsAt.map { (duration, w) -> stringResource(Res.string.chart_power_curve_point, duration, w) }
    val pointLabels =
        remember(labelled, wattsAt) {
            labelled
                .mapIndexed { i, point -> SeriesLabel(log10(point.seconds.toDouble()), point.watts.toDouble(), wattsAt[i].second) }
                .toImmutableList()
        }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChartHeader(stringResource(Res.string.chart_power_curve), null)
        TimeSeriesChart(
            points = points,
            xRange = 0.0..points.last().x,
            description = stringResource(Res.string.chart_power_curve_help, described.joinToString("; ")),
            yLabel = { "${it.roundToInt()}${watts.trim().let { u -> " $u" }}" },
            // A log axis, so durations sit only where the labelled points are.
            xLabel = { durationLabel(10.0.pow(it).roundToInt()) },
            markerLabel = { x, y ->
                marker
                    .replace("%1", durationLabel(10.0.pow(x).roundToInt()))
                    .replace("%2", wattsTemplate.replace("%s", "${y.roundToInt()}"))
            },
            xLabelsAt = pointLabels.map { it.x }.toImmutableList(),
            pointLabels = pointLabels,
        )
    }
}

@Composable
private fun Gears(gears: ImmutableList<GearPoint>, durationSeconds: Int) {
    // The last gear holds to the end.
    val points =
        remember(gears) {
            val shifts = gears.mapNotNull { gear -> gear.rear?.let { SeriesPoint(gear.seconds.toDouble(), it.toDouble()) } }
            (shifts + listOfNotNull(shifts.lastOrNull()?.copy(x = durationSeconds.toDouble()))).toImmutableList()
        }
    val marker = stringResource(Res.string.chart_marker, "%1", "%2")
    val gear = stringResource(Res.string.chart_gear_value, "%s")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChartHeader(stringResource(Res.string.chart_gears), null)
        TimeSeriesChart(
            points = points,
            xRange = 0.0..durationSeconds.toDouble(),
            description = stringResource(Res.string.chart_gears_help),
            yLabel = { "${it.roundToInt()}" },
            xLabel = { Format.duration(it.roundToLong()) },
            markerLabel = { x, y ->
                marker.replace("%1", Format.duration(x.roundToLong())).replace("%2", gear.replace("%s", "${y.roundToInt()}"))
            },
            step = true,
        )
    }
}

private fun streamTitle(kind: StreamKind): StringResource =
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
private class StreamUnit(val convert: (Double) -> Double, val text: (Double) -> String, val axis: (Double) -> String)

@Composable
private fun streamUnit(kind: StreamKind): StreamUnit {
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

private val CURVE_STATS = setOf(5, 60, 300, 1200, 3600)
private const val MINUTE = 60
private const val HOUR = 3600
private const val MM_PER_M = 1000.0
private const val SECONDS_PER_HOUR = 3600.0
