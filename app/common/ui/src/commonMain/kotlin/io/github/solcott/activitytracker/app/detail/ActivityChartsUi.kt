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
import io.github.solcott.activitytracker.app.ui.DiveFormat
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.chart.SeriesLabel
import io.github.solcott.activitytracker.app.ui.chart.SeriesPoint
import io.github.solcott.activitytracker.app.ui.chart.TimeSeriesChart
import io.github.solcott.activitytracker.app.ui.chart.ZoneBar
import io.github.solcott.activitytracker.app.ui.chart.ZoneBars
import io.github.solcott.activitytracker.app.ui.diveAlertLabel
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.chart_avg_np_max
import io.github.solcott.activitytracker.composeApp.resources.chart_gear_value
import io.github.solcott.activitytracker.composeApp.resources.chart_gears
import io.github.solcott.activitytracker.composeApp.resources.chart_left_right_help
import io.github.solcott.activitytracker.composeApp.resources.chart_marker
import io.github.solcott.activitytracker.composeApp.resources.chart_power_curve
import io.github.solcott.activitytracker.composeApp.resources.unit_watts
import io.github.solcott.activitytracker.data.units.formatWhole
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * [card]'s charts in full: its lines, then zones (or how to get them, [onSetThresholds], when the owner has none) after heart rate and
 * power, and the power curve before power zones. Elevation sits faintly behind the effort lines at every width, so effort reads against the
 * climbs; a chart without an elevation series draws none.
 */
@Composable
internal fun CardCharts(
    card: MetricCard,
    charts: ActivityCharts,
    missingZones: Boolean,
    onSetThresholds: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val section = card.section
    val elevation = charts.lines.firstOrNull { it.kind == StreamKind.Elevation }
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
            if (zones != null) Zones(DetailSection.HeartRate, zones)
            else if (missingZones && onSetThresholds != null) MissingZones(onSetThresholds)
        }

        DetailSection.Power -> {
            if (card.powerCurve.size >= 2) PowerCurve(card.powerCurve)
            val zones = card.zones
            if (zones != null) Zones(DetailSection.Power, zones)
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
            ZoneRows.missing.string(),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppTextButton(onClick = onSetThresholds) { Text(ZoneRows.setThresholds.string()) }
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
    val alertNames = charts.alerts.map { diveAlertLabel(it.kind) }
    val warnings =
        remember(charts.alerts, alertNames) {
            charts.alerts.mapIndexed { i, alert -> SeriesLabel(alert.seconds.toDouble(), 0.0, alertNames[i]) }.toImmutableList()
        }
    val extra = CardLines.depthNotes(charts)?.string()
    LineChart(
        series,
        charts.durationSeconds,
        elevation = null,
        titled = false,
        others = if (ceiling.isEmpty()) persistentListOf() else persistentListOf(ceiling.toImmutableList()),
        events = events,
        warnings = warnings,
        extraDescription = extra,
    )
}

/** A line per tank, on one scale, the first in the theme's colour and the rest in the others. */
@Composable
private fun TankPressures(tanks: ImmutableList<LineSeries>, durationSeconds: Int) {
    val first = tanks.firstOrNull() ?: return
    val rest = tanks.drop(1).map { rememberPoints(it) }.toImmutableList()
    LineChart(
        first,
        durationSeconds,
        elevation = null,
        titled = tanks.size > 1,
        others = rest,
        extraDescription = CardLines.tanksHelp(tanks.size).string().takeIf { tanks.size > 1 },
        detailOverride = CardLines.tanksDetail(tanks, LocalUnitSystem.current)?.string()?.takeIf { tanks.size > 1 },
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
    // The shared rule (CardLines.detail), but for several tanks' maxima, and normalized power beside power.
    val shared = CardLines.detail(series, pace, LocalUnitSystem.current)?.string()
    val detail =
        when {
            max == null || average == null || right != null -> shared
            detailOverride != null -> detailOverride
            normalizedPower != null ->
                stringResource(
                    Res.string.chart_avg_np_max,
                    average,
                    stringResource(Res.string.unit_watts, formatWhole(normalizedPower)),
                    max,
                )
            else -> shared
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
                        lineDescription(title, average, max.orEmpty(), pace),
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
private fun Zones(section: DetailSection, zones: ImmutableList<ZoneTime>) {
    val bars =
        ZoneRows.of(zones, section).map { row ->
            ZoneBar(
                label = row.label.string(),
                name = row.name.string(),
                range = row.range.string(),
                time = row.time.string(),
                fraction = row.fraction.toFloat(),
            )
        }
    Column(verticalArrangement = Arrangement.spacedBy(HEADER_GAP)) {
        ChartHeader(ZoneRows.heading(section).string(), null)
        ZoneBars(bars.toImmutableList())
    }
}

@Composable
private fun PowerCurve(curve: ImmutableList<CurvePoint>) {
    val watts = stringResource(Res.string.unit_watts, "")
    // Each duration is an equal step along the axis, so 1 s to 1 min takes no more room than 1 min to 1 h.
    val points = remember(curve) { curve.mapIndexed { i, point -> SeriesPoint(i.toDouble(), point.watts.toDouble()) }.toImmutableList() }
    // Between two durations the axis runs on a log scale, so a marker between points reads a duration between theirs.
    val secondsAt = { x: Double ->
        val i = x.toInt().coerceIn(0, curve.lastIndex)
        val next = curve.getOrNull(i + 1)?.seconds ?: curve[i].seconds
        (curve[i].seconds * (next.toDouble() / curve[i].seconds).pow(x - i)).roundToInt()
    }
    // The shared duration label (CardLines.durationLabel), at any duration a marker lands on.
    val templates = DURATION_UNITS.map { CardLines.durationLabel(it).string().replace("1", "%s") }
    val durationLabel = { s: Int ->
        when {
            s < DURATION_UNITS[1] -> templates[0].replace("%s", "$s")
            s < DURATION_UNITS[2] -> templates[1].replace("%s", "${s / DURATION_UNITS[1]}")
            else -> templates[2].replace("%s", "${s / DURATION_UNITS[2]}")
        }
    }
    val marker = stringResource(Res.string.chart_marker, "%1", "%2")
    val wattsTemplate = stringResource(Res.string.unit_watts, "%s")
    val description = CardLines.powerCurveDescription(curve).string()
    Column(verticalArrangement = Arrangement.spacedBy(HEADER_GAP)) {
        ChartHeader(stringResource(Res.string.chart_power_curve), null)
        TimeSeriesChart(
            points = points,
            // Half a step either side: the axis gives a label only as much room as it has to the range's edge.
            xRange = -CURVE_EDGE..points.last().x + CURVE_EDGE,
            description = description,
            yLabel = { "${formatWhole(it.roundToInt())}${watts.trim().let { u -> " $u" }}" },
            xLabel = { durationLabel(secondsAt(it)) },
            markerLabel = { x, y ->
                marker.replace("%1", durationLabel(secondsAt(x))).replace("%2", wattsTemplate.replace("%s", formatWhole(y.roundToInt())))
            },
            // A label at every duration; the chart leaves some out when they don't fit.
            xLabelsAt = remember(points) { points.map { it.x }.toImmutableList() },
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
            description = CardLines.gearsHelp.string(),
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
private const val CURVE_EDGE = 0.5

/** A second, a minute and an hour: each reads as one of its unit, so its label is the unit's template. */
private val DURATION_UNITS = listOf(1, 60, 3600)
private val CHART_GAP = 24.dp
private val HEADER_GAP = 8.dp
