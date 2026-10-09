package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import io.github.solcott.activitytracker.composeApp.resources.chart_avg_np_max
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
import io.github.solcott.activitytracker.composeApp.resources.charts_missing_zones
import io.github.solcott.activitytracker.composeApp.resources.charts_set_thresholds
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

/**
 * [section]'s charts in full: its lines, then zones (or how to get them) after heart rate and power, and the power curve before power
 * zones. Elevation sits behind the effort lines, as Strava draws it, so effort reads against the climbs.
 */
@Composable
internal fun SectionCharts(section: DetailSection, state: ActivityDetailState.Loaded, modifier: Modifier = Modifier) {
    val charts = state.charts ?: return
    val elevation = charts.lines.firstOrNull { it.kind == StreamKind.Elevation }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CHART_GAP)) {
        for (series in charts.lines) {
            if (sectionOf(series.kind) != section) continue
            // The section's title already names its own line; grade under elevation needs one.
            val titled = series.kind.name != section.name
            val normalized = charts.normalizedPower.takeIf { series.kind == StreamKind.Power }
            LineChart(series, charts.durationSeconds, elevation?.takeIf { series.kind in OVER_ELEVATION }, titled, normalized)
        }
        when (section) {
            DetailSection.HeartRate -> {
                val zones = charts.heartRateZones
                if (zones != null) Zones(Res.string.chart_heart_rate_zones, zones, Res.string.unit_bpm)
                else if (state.chartsMissingZones) MissingZones(state.eventSink)
            }

            DetailSection.Power -> {
                if (charts.powerCurve.size >= 2) PowerCurve(charts.powerCurve)
                val zones = charts.powerZones
                if (zones != null) Zones(Res.string.chart_power_zones, zones, Res.string.unit_watts)
                else if (state.chartsMissingZones) MissingZones(state.eventSink)
            }

            DetailSection.Gears -> Gears(charts.gears, charts.durationSeconds, elevation)

            else -> {}
        }
    }
}

/** The section each line belongs in. */
private fun sectionOf(kind: StreamKind): DetailSection =
    when (kind) {
        StreamKind.HeartRate -> DetailSection.HeartRate
        StreamKind.Power -> DetailSection.Power
        StreamKind.Speed -> DetailSection.Speed
        StreamKind.Cadence -> DetailSection.Cadence
        StreamKind.Elevation,
        StreamKind.Grade -> DetailSection.Elevation
        StreamKind.Temperature -> DetailSection.Temperature
        StreamKind.Depth -> DetailSection.Depth
    }

/** The owner's choice of who may see the charts, under a heading that asks the question. */
@Composable
internal fun ChartsVisibility(
    visibility: String,
    saving: Boolean,
    eventSink: (ActivityDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HEADER_GAP)) {
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

/** A chart's [title], when the section's own doesn't name it, and its [detail] at the end. */
@Composable
private fun ChartHeader(title: String?, detail: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun LineChart(series: LineSeries, durationSeconds: Int, elevation: LineSeries?, titled: Boolean, normalizedPower: Int? = null) {
    val unit = streamUnit(series.kind)
    val title = stringResource(streamTitle(series.kind))
    val average = series.average?.let { unit.text(unit.convert(it)) }
    val max = series.max?.let { unit.text(unit.convert(it)) }
    val detail =
        when {
            average == null || max == null -> null
            normalizedPower != null ->
                stringResource(Res.string.chart_avg_np_max, average, stringResource(Res.string.unit_watts, normalizedPower), max)
            else -> stringResource(Res.string.chart_avg_max, average, max)
        }
    val points = rememberPoints(series)
    val backdrop = elevation?.let { rememberPoints(it) }
    val markerLabel = rememberMarkerLabel(backdrop) { unit.text(it) }
    Column(verticalArrangement = Arrangement.spacedBy(HEADER_GAP)) {
        if (titled || detail != null) ChartHeader(title.takeIf { titled }, detail)
        TimeSeriesChart(
            points = points,
            xRange = 0.0..durationSeconds.toDouble(),
            description = stringResource(Res.string.chart_description, title, average.orEmpty(), max.orEmpty()),
            yLabel = unit.axis,
            xLabel = { Format.duration(it.roundToLong()) },
            markerLabel = markerLabel,
            average = series.average?.let(unit.convert),
            backdrop = backdrop,
            inverted = series.kind == StreamKind.Depth,
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
    Column(verticalArrangement = Arrangement.spacedBy(HEADER_GAP)) {
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
    Column(verticalArrangement = Arrangement.spacedBy(HEADER_GAP)) {
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
    Column(verticalArrangement = Arrangement.spacedBy(HEADER_GAP)) {
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
private val CHART_GAP = 24.dp
private val HEADER_GAP = 8.dp
private const val MINUTE = 60
private const val HOUR = 3600
