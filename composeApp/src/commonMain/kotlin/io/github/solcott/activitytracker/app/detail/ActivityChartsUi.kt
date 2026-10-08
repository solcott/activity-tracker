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
import io.github.solcott.activitytracker.app.ui.VisibilityPicker
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.chart.SeriesLabel
import io.github.solcott.activitytracker.app.ui.chart.SeriesPoint
import io.github.solcott.activitytracker.app.ui.chart.TimeSeriesChart
import io.github.solcott.activitytracker.app.ui.chart.ZoneBar
import io.github.solcott.activitytracker.app.ui.chart.ZoneBars
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.chart_avg_max
import io.github.solcott.activitytracker.composeApp.resources.chart_description
import io.github.solcott.activitytracker.composeApp.resources.chart_gear_value
import io.github.solcott.activitytracker.composeApp.resources.chart_gears
import io.github.solcott.activitytracker.composeApp.resources.chart_gears_help
import io.github.solcott.activitytracker.composeApp.resources.chart_heart_rate_zones
import io.github.solcott.activitytracker.composeApp.resources.chart_marker
import io.github.solcott.activitytracker.composeApp.resources.chart_power_curve
import io.github.solcott.activitytracker.composeApp.resources.chart_power_curve_help
import io.github.solcott.activitytracker.composeApp.resources.chart_power_curve_point
import io.github.solcott.activitytracker.composeApp.resources.chart_power_zones
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
import io.github.solcott.activitytracker.composeApp.resources.unit_watts
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
    // Elevation behind the other lines, as Strava draws it, so effort reads against the climbs.
    val elevation = charts.lines.firstOrNull { it.kind == StreamKind.Elevation }
    for (series in charts.lines) {
        val behind = elevation?.takeIf { series.kind in OVER_ELEVATION }
        item(key = "line-${series.kind}") { LineChart(series, charts.durationSeconds, behind) }
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
    if (charts.gears.size >= 2) item(key = "gears") { Gears(charts.gears, charts.durationSeconds, elevation) }
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
private fun LineChart(series: LineSeries, durationSeconds: Int, elevation: LineSeries?) {
    val unit = streamUnit(series.kind)
    val title = stringResource(streamTitle(series.kind))
    val average = series.average?.let { unit.text(unit.convert(it)) }
    val max = series.max?.let { unit.text(unit.convert(it)) }
    val detail = if (average != null && max != null) stringResource(Res.string.chart_avg_max, average, max) else null
    val points = rememberPoints(series)
    val backdrop = elevation?.let { rememberPoints(it) }
    val markerLabel = rememberMarkerLabel(backdrop) { unit.text(it) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChartHeader(title, detail)
        TimeSeriesChart(
            points = points,
            xRange = 0.0..durationSeconds.toDouble(),
            description = stringResource(Res.string.chart_description, title, average.orEmpty(), max.orEmpty()),
            yLabel = unit.axis,
            xLabel = { Format.duration(it.roundToLong()) },
            markerLabel = markerLabel,
            average = series.average?.let(unit.convert),
            backdrop = backdrop,
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
private fun Gears(gears: ImmutableList<GearPoint>, durationSeconds: Int, elevation: LineSeries?) {
    // The last gear holds to the end.
    val points =
        remember(gears) {
            val shifts = gears.mapNotNull { gear -> gear.rear?.let { SeriesPoint(gear.seconds.toDouble(), it.toDouble()) } }
            (shifts + listOfNotNull(shifts.lastOrNull()?.copy(x = durationSeconds.toDouble()))).toImmutableList()
        }
    val gear = stringResource(Res.string.chart_gear_value, "%s")
    val backdrop = elevation?.let { rememberPoints(it) }
    val markerLabel = rememberMarkerLabel(backdrop) { gear.replace("%s", "${it.roundToInt()}") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChartHeader(stringResource(Res.string.chart_gears), null)
        TimeSeriesChart(
            points = points,
            xRange = 0.0..durationSeconds.toDouble(),
            description = stringResource(Res.string.chart_gears_help),
            yLabel = { "${it.roundToInt()}" },
            xLabel = { Format.duration(it.roundToLong()) },
            markerLabel = markerLabel,
            step = true,
            backdrop = backdrop,
        )
    }
}

/** Lines that read against the climbs; not elevation itself, nor a dive's depth. */
private val OVER_ELEVATION =
    setOf(StreamKind.HeartRate, StreamKind.Power, StreamKind.Speed, StreamKind.Cadence, StreamKind.Grade, StreamKind.Temperature)
private val CURVE_STATS = setOf(5, 60, 300, 1200, 3600)
private const val MINUTE = 60
private const val HOUR = 3600
