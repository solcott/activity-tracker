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
import io.github.solcott.activitytracker.app.ui.DiveFormat
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.chart.SeriesLabel
import io.github.solcott.activitytracker.app.ui.chart.SeriesPoint
import io.github.solcott.activitytracker.app.ui.chart.TimeSeriesChart
import io.github.solcott.activitytracker.app.ui.chart.ZoneBar
import io.github.solcott.activitytracker.app.ui.chart.ZoneBars
import io.github.solcott.activitytracker.app.ui.diveAlertLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.chart_avg_max
import io.github.solcott.activitytracker.composeApp.resources.chart_avg_np_max
import io.github.solcott.activitytracker.composeApp.resources.chart_depth_alerts
import io.github.solcott.activitytracker.composeApp.resources.chart_depth_ceiling
import io.github.solcott.activitytracker.composeApp.resources.chart_depth_switch
import io.github.solcott.activitytracker.composeApp.resources.chart_depth_switches
import io.github.solcott.activitytracker.composeApp.resources.chart_gear_value
import io.github.solcott.activitytracker.composeApp.resources.chart_gears
import io.github.solcott.activitytracker.composeApp.resources.chart_gears_help
import io.github.solcott.activitytracker.composeApp.resources.chart_heart_rate_zones
import io.github.solcott.activitytracker.composeApp.resources.chart_left_right
import io.github.solcott.activitytracker.composeApp.resources.chart_left_right_help
import io.github.solcott.activitytracker.composeApp.resources.chart_marker
import io.github.solcott.activitytracker.composeApp.resources.chart_power_curve
import io.github.solcott.activitytracker.composeApp.resources.chart_power_curve_help
import io.github.solcott.activitytracker.composeApp.resources.chart_power_curve_point
import io.github.solcott.activitytracker.composeApp.resources.chart_power_zones
import io.github.solcott.activitytracker.composeApp.resources.chart_tank_pressure_help
import io.github.solcott.activitytracker.composeApp.resources.chart_zone_label
import io.github.solcott.activitytracker.composeApp.resources.chart_zone_range
import io.github.solcott.activitytracker.composeApp.resources.chart_zone_range_open
import io.github.solcott.activitytracker.composeApp.resources.charts_missing_zones
import io.github.solcott.activitytracker.composeApp.resources.charts_set_thresholds
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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * [card]'s charts in full: its lines, then zones (or how to get them, [onSetThresholds], when the owner has none) after heart rate and
 * power, and the power curve before power zones. With [elevationBehind] (from 600dp: on phones it roughly doubled a chart's draw time, past
 * a frame; design.md, Measurements) elevation sits faintly behind the effort lines, as Strava draws it, so effort reads against the climbs;
 * a chart without an elevation series draws none.
 */
@Composable
internal fun CardCharts(
    card: MetricCard,
    charts: ActivityCharts,
    missingZones: Boolean,
    onSetThresholds: (() -> Unit)?,
    elevationBehind: Boolean,
    modifier: Modifier = Modifier,
) {
    val section = card.section
    val elevation = charts.lines.firstOrNull { it.kind == StreamKind.Elevation }.takeIf { elevationBehind }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CHART_GAP)) {
        if (section == DetailSection.TankPressure) {
            TankPressures(card.series, charts.durationSeconds)
        } else {
            for (series in card.series) {
                // The card's title and headline already name and sum up its own line; a second (grade under elevation) needs a title.
                val titled = series.kind.name != section.name && card.series.size > 1
                if (series.kind == StreamKind.Depth) {
                    DepthChart(series, charts)
                } else {
                    LineChart(
                        series,
                        charts.durationSeconds,
                        elevation?.takeIf { series.kind in OVER_ELEVATION },
                        titled,
                        step = series.kind == StreamKind.EbikeAssistMode,
                        pace = card.pace.takeIf { series.kind == StreamKind.Speed },
                    )
                }
            }
        }
        CardExtras(card, charts, elevation, missingZones, onSetThresholds)
    }
}

/** What follows [card]'s lines: zones (or how to get them) and the power curve, the gears, or its table. */
@Composable
private fun CardExtras(
    card: MetricCard,
    charts: ActivityCharts,
    elevation: LineSeries?,
    missingZones: Boolean,
    onSetThresholds: (() -> Unit)?,
) {
    when (card.section) {
        DetailSection.HeartRate -> {
            val zones = card.zones
            if (zones != null) Zones(Res.string.chart_heart_rate_zones, zones, Res.string.unit_bpm)
            else if (missingZones && onSetThresholds != null) MissingZones(onSetThresholds)
        }

        DetailSection.Power -> {
            if (card.powerCurve.size >= 2) PowerCurve(card.powerCurve)
            val zones = card.zones
            if (zones != null) Zones(Res.string.chart_power_zones, zones, Res.string.unit_watts)
            else if (missingZones && onSetThresholds != null) MissingZones(onSetThresholds)
        }

        DetailSection.Gears -> Gears(card.gears, charts.durationSeconds, elevation)

        DetailSection.Laps -> LapTable(charts.laps, ski = charts.skiRuns != null)

        DetailSection.Legs -> LegTable(charts.legs)

        DetailSection.Lengths -> LengthTable(charts.lengths)

        DetailSection.Sets -> SetTable(charts.sets)

        else -> {}
    }
}

@Composable
private fun MissingZones(onSetThresholds: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.charts_missing_zones),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppTextButton(onClick = onSetThresholds) { Text(stringResource(Res.string.charts_set_thresholds)) }
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

/**
 * A dive's depth, with each gas switch marked by the gas's name at its time, each warning the computer raised marked by its name in the
 * error colour, and the deco ceiling, where the computer logged one, drawn as a second line under the depth.
 */
@Composable
private fun DepthChart(series: LineSeries, charts: ActivityCharts) {
    val unit = streamUnit(StreamKind.Depth)
    val ceiling =
        remember(charts.ceiling, unit) { charts.ceiling.map { SeriesPoint(it.seconds.toDouble(), unit.convert(it.value), it.gapBefore) } }
    val names = charts.gasSwitches.map { DiveFormat.gasName(it.oxygen, it.helium) }
    val events =
        remember(charts.gasSwitches, names) {
            charts.gasSwitches.mapIndexed { i, switch -> SeriesLabel(switch.seconds.toDouble(), 0.0, names[i]) }.toImmutableList()
        }
    val switchText =
        charts.gasSwitches.zip(names) { switch, name ->
            stringResource(Res.string.chart_depth_switch, name, Format.duration(switch.seconds.toLong()))
        }
    val alertNames = charts.alerts.map { diveAlertLabel(it.kind) }
    val warnings =
        remember(charts.alerts, alertNames) {
            charts.alerts.mapIndexed { i, alert -> SeriesLabel(alert.seconds.toDouble(), 0.0, alertNames[i]) }.toImmutableList()
        }
    val alertText =
        charts.alerts.zip(alertNames) { alert, name ->
            stringResource(Res.string.chart_depth_switch, name, Format.duration(alert.seconds.toLong()))
        }
    val extra =
        listOfNotNull(
            switchText.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.chart_depth_switches, it.joinToString(", ")) },
            alertText.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.chart_depth_alerts, it.joinToString(", ")) },
            stringResource(Res.string.chart_depth_ceiling).takeIf { ceiling.isNotEmpty() },
        )
    LineChart(
        series,
        charts.durationSeconds,
        elevation = null,
        titled = false,
        others = if (ceiling.isEmpty()) persistentListOf() else persistentListOf(ceiling.toImmutableList()),
        events = events,
        warnings = warnings,
        extraDescription = extra.joinToString(" "),
    )
}

/** A line per tank, on one scale, the first in the theme's colour and the rest in the others. */
@Composable
private fun TankPressures(tanks: ImmutableList<LineSeries>, durationSeconds: Int) {
    val first = tanks.firstOrNull() ?: return
    val unit = streamUnit(StreamKind.TankPressure)
    val rest = tanks.drop(1).map { rememberPoints(it) }.toImmutableList()
    LineChart(
        first,
        durationSeconds,
        elevation = null,
        titled = tanks.size > 1,
        others = rest,
        extraDescription = stringResource(Res.string.chart_tank_pressure_help, tanks.size).takeIf { tanks.size > 1 },
        detailOverride = tanks.mapNotNull { it.max }.takeIf { tanks.size > 1 }?.joinToString(" · ") { unit.text(unit.convert(it)) },
    )
}

@Composable
@Suppress("LongParameterList") // The series, what goes with it, and how it reads.
private fun LineChart(
    series: LineSeries,
    durationSeconds: Int,
    elevation: LineSeries?,
    titled: Boolean,
    normalizedPower: Int? = null,
    others: ImmutableList<ImmutableList<SeriesPoint>> = persistentListOf(),
    events: ImmutableList<SeriesLabel> = persistentListOf(),
    warnings: ImmutableList<SeriesLabel> = persistentListOf(),
    extraDescription: String? = null,
    detailOverride: String? = null,
    step: Boolean = false,
    pace: PaceKind? = null,
) {
    val unit = lineUnit(series.kind, pace)
    val title = lineTitle(series.kind, pace)
    val average = series.average?.let { unit.text(unit.convert(it)) }
    val max = series.max?.let { unit.text(unit.convert(it)) }
    // A left/right pair draws the right leg as a second line, and each leg's average in the header.
    val right = series.right
    val rightPoints = right?.let { rememberPoints(it) }
    val lines = if (rightPoints == null) others else persistentListOf(rightPoints)
    val detail =
        when {
            average == null || max == null -> null
            right != null -> stringResource(Res.string.chart_left_right, average, right.average?.let { unit.text(unit.convert(it)) } ?: "–")
            detailOverride != null -> detailOverride
            normalizedPower != null ->
                stringResource(Res.string.chart_avg_np_max, average, stringResource(Res.string.unit_watts, normalizedPower), max)
            else -> stringResource(Res.string.chart_avg_max, average, max)
        }
    val points = rememberPoints(series, unit, paceCeiling(series, unit, pace))
    val backdrop = elevation?.let { rememberPoints(it) }
    val markerLabel = rememberMarkerLabel(backdrop) { unit.text(it) }
    val pairDescription = stringResource(Res.string.chart_left_right_help).takeIf { right != null }
    Column(verticalArrangement = Arrangement.spacedBy(HEADER_GAP)) {
        // A card's headline already gives a lone line's average and maximum; a titled line, a leg pair or several tanks give their own.
        if (titled || right != null || detailOverride != null) ChartHeader(title.takeIf { titled }, detail)
        TimeSeriesChart(
            points = points,
            xRange = 0.0..durationSeconds.toDouble(),
            description =
                listOfNotNull(
                        lineDescription(title, average.orEmpty(), max.orEmpty(), pace),
                        extraDescription,
                        pairDescription,
                    )
                    .joinToString(" "),
            yLabel = unit.axis,
            xLabel = { Format.duration(it.roundToLong()) },
            markerLabel = markerLabel,
            average = series.average?.let(unit.convert),
            backdrop = backdrop,
            inverted = series.kind == StreamKind.Depth || pace != null,
            fillAbove = series.kind == StreamKind.Depth,
            timeValues = pace != null,
            others = lines,
            events = events,
            warnings = warnings,
            step = step,
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
