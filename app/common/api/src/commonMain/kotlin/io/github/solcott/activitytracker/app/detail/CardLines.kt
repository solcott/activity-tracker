package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.chart.ChartTick
import io.github.solcott.activitytracker.app.chart.LineChart
import io.github.solcott.activitytracker.app.chart.LinePoint
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.app.text.ZoneNameText
import io.github.solcott.activitytracker.data.chart.ChartAxis
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatDecimal
import io.github.solcott.activitytracker.data.units.formatWhole
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * One line of a metric card, as a native page draws it: its [chart], with a [title] when the card's own doesn't name it (grade under
 * elevation) and a [detail] for its end of the header (a left/right pair's averages), each null when the card's headline already says it.
 */
@Immutable class CardLine(val title: AppText?, val detail: AppText?, val chart: LineChart)

/**
 * How a metric card's lines read and chart (feed/activity-page-layout, dev/shared-display-text): each line's title, header detail and
 * description, which the shared chart asks too, and each as a [LineChart] over the activity's time for a native page. The shared chart also
 * draws the elevation behind effort lines, a pair's second line, several tanks and a dive's markers, which [LineChart] can't yet; here each
 * card's lines are their main line alone.
 */
object CardLines {
    /** The card's lines as charts, in drawing order; a series with fewer than two points has none. */
    fun of(card: MetricCard, charts: ActivityCharts, units: UnitSystem): List<CardLine> =
        card.series.mapNotNull { series ->
            val pace = card.pace.takeIf { series.kind == StreamKind.Speed }
            // The card's title and headline already name and sum up its own line; a second (grade under elevation) needs a title.
            val titled = series.kind.name != card.section.name && card.series.size > 1
            val tanks = card.section == DetailSection.TankPressure && card.series.size > 1
            chart(series, charts.durationSeconds, pace, units)?.let { chart ->
                CardLine(
                    title = title(series.kind, pace).takeIf { titled || tanks },
                    detail = detail(series, pace, units).takeIf { titled || series.right != null },
                    chart = chart,
                )
            }
        }

    /** A line's title: Pace for a pace line, else its stream's. */
    fun title(kind: StreamKind, pace: PaceKind?): AppText = if (pace != null) AppText.named("chart_pace") else StreamUnits.title(kind)

    /** [value] as [LineSeries] keeps it, read as the line reads: a pace when [pace], else in its stream's unit. */
    fun valueText(kind: StreamKind, value: Double, pace: PaceKind?, units: UnitSystem): AppText =
        if (pace != null) StreamUnits.paceText(pace, StreamUnits.paceSeconds(pace, value, units), units)
        else StreamUnits.valueText(kind, value, units)

    /**
     * The end of a line's header: its average and maximum, its maximum alone without an average, or a left/right pair's two averages; null
     * without a maximum.
     */
    fun detail(series: LineSeries, pace: PaceKind?, units: UnitSystem): AppText? {
        val max = series.max?.let { valueText(series.kind, it, pace, units) } ?: return null
        val average = series.average?.let { valueText(series.kind, it, pace, units) } ?: return AppText.named("chart_max_only", max)
        val right = series.right
        return if (right != null) {
            AppText.named("chart_left_right", average, right.average?.let { valueText(series.kind, it, pace, units) } ?: StatText.missing)
        } else {
            AppText.named("chart_avg_max", average, max)
        }
    }

    /** What a line's chart shows, for a screen reader: a pace's fastest is its best, not its maximum. */
    fun description(title: AppText, average: AppText?, max: AppText, pace: PaceKind?): AppText =
        when {
            pace != null -> AppText.named("chart_description_pace", average ?: AppText.written(""), max)
            average == null -> AppText.named("chart_description_max", title, max)
            else -> AppText.named("chart_description", title, average, max)
        }

    /** [series] over the activity's [durationSeconds] as a [LineChart]; a pace line inverted, the faster at the top, and capped. */
    fun chart(series: LineSeries, durationSeconds: Int, pace: PaceKind?, units: UnitSystem): LineChart? {
        if (series.points.size < 2) return null
        val convert = { v: Double ->
            if (pace != null) StreamUnits.paceSeconds(pace, v, units) else StreamUnits.convert(series.kind, v, units)
        }
        val average = series.average?.let(convert)
        // A standstill's pace is infinite: past 1.5 times the average, a stop would flatten the rest of the line.
        val ceiling = average?.takeIf { pace != null }?.let { it * PACE_CEILING } ?: Double.MAX_VALUE
        val inverted = pace != null || series.kind == StreamKind.Depth
        val values = series.points.map { convert(it.value).coerceAtMost(ceiling) }
        val sign = if (inverted) -1.0 else 1.0
        val points = series.points.mapIndexed { i, sample -> LinePoint(sample.seconds.toDouble(), sign * values[i], sample.gapBefore) }
        val text = { v: Double -> if (pace != null) StreamUnits.paceText(pace, v, units) else StreamUnits.text(series.kind, v, units) }
        val axisLabel = { v: Double -> if (pace != null) StreamUnits.paceAxis(v) else StreamUnits.axis(v) }
        val maxText = series.max?.let { valueText(series.kind, it, pace, units) } ?: StatText.missing
        val title = title(series.kind, pace)
        return LineChart.of(
            points = points,
            xMin = 0.0,
            xMax = durationSeconds.toDouble().coerceAtLeast(points.last().x),
            xTicks = timeTicks(durationSeconds),
            yLabel = { axisLabel(sign * it) },
            readouts = series.points.mapIndexed { i, sample -> AppText.named("chart_marker", time(sample.seconds), text(values[i])) },
            description = description(title, average?.let(text), maxText, pace),
            axis = ChartAxis.of(values, inverted = inverted, timeValues = pace != null),
            average = average?.let { sign * it.coerceAtMost(ceiling) },
        )
    }

    /** Labels along an activity's time: a round step of minutes or hours, at most five labels from the start. */
    fun timeTicks(durationSeconds: Int): List<ChartTick> {
        val step = TIME_STEPS.firstOrNull { durationSeconds / it <= MAX_TIME_GAPS } ?: (durationSeconds / MAX_TIME_GAPS + 1)
        return (0..durationSeconds / step).map { ChartTick((it * step).toDouble(), time(it * step)) }
    }

    private fun time(seconds: Int): AppText = AppText.written(StatText.duration(seconds.toLong()))

    /** How much slower than its average a pace chart goes. */
    const val PACE_CEILING = 1.5
    private const val MAX_TIME_GAPS = 4
    private val TIME_STEPS = listOf(60, 120, 300, 600, 900, 1800, 3600, 7200, 10800, 14400, 21600, 43200)
}

/** Time in one zone as a row reads it: "Z2", its name, its range with its unit, its time, and its share of the time in all of them. */
@Immutable class ZoneRow(val label: AppText, val name: AppText, val range: AppText, val time: AppText, val fraction: Double)

/** Time in heart rate or power zones (feed/activity-zones), as both UIs word it. */
object ZoneRows {
    /** The heart rate or power zones' heading. */
    fun heading(section: DetailSection): AppText =
        AppText.named(if (section == DetailSection.Power) "chart_power_zones" else "chart_heart_rate_zones")

    /** [zones] as rows: ranges in bpm, or watts for the power card. */
    fun of(zones: List<ZoneTime>, section: DetailSection): List<ZoneRow> {
        val total = zones.sumOf { it.seconds }.coerceAtLeast(1)
        val unit = if (section == DetailSection.Power) "unit_watts" else "unit_bpm"
        return zones.map { zone ->
            val high = zone.high
            ZoneRow(
                label = AppText.named("chart_zone_label", AppText.written(zone.label)),
                name = ZoneNameText.of(zone.name),
                range =
                    AppText.named(
                        unit,
                        if (high != null) {
                            AppText.named("chart_zone_range", AppText.written(formatWhole(zone.low)), AppText.written(formatWhole(high)))
                        } else {
                            AppText.named("chart_zone_range_open", AppText.written(formatWhole(zone.low)))
                        },
                    ),
                time = AppText.written(StatText.duration(zone.seconds.toLong())),
                fraction = zone.seconds.toDouble() / total,
            )
        }
    }

    /** Why the owner sees no zones, and the button to the zones screen. */
    val missing: AppText = AppText.named("charts_missing_zones")
    val setThresholds: AppText = AppText.named("charts_set_thresholds")
}

/** A table row: its cells in column order, and the sentence a screen reader says for it. */
@Immutable class TableRow(val cells: ImmutableList<AppText>, val spoken: AppText)

/** A table: its column [headers] and its [rows]. [wideFirst] gives the first column (a lift's name) a wide share. */
@Immutable class DetailTable(val headers: ImmutableList<AppText>, val rows: ImmutableList<TableRow>, val wideFirst: Boolean)

/**
 * The laps table (feed/activity-page-layout): each lap's time, distance, speed and effort, with its intensity when the laps record one. A
 * ski day's laps are its runs, numbered, with their descent, and its lift rides, named by their lift when one matched. Both UIs ask it.
 */
object LapTableTexts {
    @Suppress("LongMethod") // A column at a time.
    fun of(laps: List<LapRow>, ski: Boolean, units: UnitSystem): DetailTable {
        val intensity = !ski && laps.any { it.intensity != null }
        val power = laps.any { it.avgPower != null }
        val heartRate = laps.any { it.avgHeartRate != null }
        val ascent = !ski && laps.any { it.ascent != null }
        val descent = ski && laps.any { it.descent != null }
        // Cadence only when there's no power, which says more.
        val cadence = !power && laps.any { it.avgCadence != null }
        val metric = units == UnitSystem.Metric
        val height = if (metric) "unit_elevation_m" else "unit_elevation_ft"
        val headers =
            listOfNotNull(
                AppText.named(if (ski) "lap_run" else "lap_number"),
                AppText.named("lap_type").takeIf { intensity },
                AppText.named("lap_time"),
                AppText.named("lap_distance"),
                AppText.named("lap_speed"),
                AppText.named("lap_heart_rate").takeIf { heartRate },
                AppText.named("lap_power").takeIf { power },
                AppText.named("lap_cadence").takeIf { cadence },
                AppText.named("lap_ascent").takeIf { ascent },
                AppText.named("lap_descent").takeIf { descent },
            )
        val runs = laps.runningFold(0) { n, lap -> if (lap.isRest) n else n + 1 }.drop(1)
        val rows = laps.mapIndexed { i, lap ->
            val name =
                when {
                    !ski -> AppText.written("${lap.number}")
                    lap.isRest -> lap.liftName?.let(AppText::written) ?: AppText.named("lap_lift")
                    else -> AppText.named("lap_run_number", AppText.written("${runs[i]}"))
                }
            val cells =
                listOfNotNull(
                    cell(lap.intensity, intensity) { intensityName(it) },
                    AppText.written(StatText.duration((lap.movingSeconds ?: lap.elapsedSeconds).toLong())),
                    cell(lap.distance) {
                        AppText.named(if (metric) "unit_distance_km" else "unit_distance_mi", AppText.written(formatDecimal(it, 2)))
                    },
                    cell(lap.speed) {
                        AppText.named(if (metric) "unit_speed_kmh" else "unit_speed_mph", AppText.written(formatDecimal(it, 1)))
                    },
                    cell(lap.avgHeartRate, heartRate) { AppText.named("unit_bpm", AppText.written(formatWhole(it))) },
                    cell(lap.avgPower, power) { AppText.named("unit_watts", AppText.written(formatWhole(it))) },
                    cell(lap.avgCadence, cadence) { AppText.named("unit_rpm", AppText.written(formatWhole(it))) },
                    cell(lap.ascent, ascent) { AppText.named(height, AppText.written(formatDecimal(it, 0))) },
                    // A lift ride has no descent worth showing.
                    if (!descent) null
                    else
                        lap.descent?.takeUnless { lap.isRest }?.let { AppText.named(height, AppText.written(formatDecimal(it, 0))) }
                            ?: StatText.missing,
                )
            val spokenName = if (ski) name else AppText.named("table_cell_description", headers.first(), name)
            TableRow((listOf(name) + cells).toImmutableList(), spoken(spokenName, headers.drop(1), cells))
        }
        return DetailTable(headers.toImmutableList(), rows.toImmutableList(), wideFirst = ski && laps.any { it.liftName != null })
    }

    /** A table row as a screen reader says it: its [name], then each header and value. */
    fun spoken(name: AppText, headers: List<AppText>, cells: List<AppText>): AppText =
        AppText.named(
            "table_row_description",
            name,
            AppText.joined(headers.zip(cells) { h, v -> AppText.named("table_cell_description", h, v) }) ?: AppText.written(""),
        )

    /** A lap's intensity ("Rest", "Warm-up"), by its token; an unknown one readably. */
    fun intensityName(token: String): AppText = AppText.Named("lap_intensity_$token", fallback = AppText.written(readableToken(token)))

    /** A token from an open set made readable: "leg_press" and "legPress" read "Leg press". */
    fun readableToken(token: String): String =
        token.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").replace('_', ' ').trim().lowercase().replaceFirstChar { it.uppercase() }

    private fun <T : Any> cell(value: T?, shown: Boolean = true, text: (T) -> AppText): AppText? =
        if (!shown) null else value?.let(text) ?: StatText.missing
}
