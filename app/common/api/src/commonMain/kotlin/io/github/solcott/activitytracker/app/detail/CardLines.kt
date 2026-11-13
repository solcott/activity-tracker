package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.chart.ChartLine
import io.github.solcott.activitytracker.app.chart.ChartMark
import io.github.solcott.activitytracker.app.chart.ChartTick
import io.github.solcott.activitytracker.app.chart.LineChart
import io.github.solcott.activitytracker.app.chart.LinePoint
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.DiveText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.app.text.ZoneNameText
import io.github.solcott.activitytracker.data.chart.ChartAxis
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatDecimal
import io.github.solcott.activitytracker.data.units.formatWhole
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * One line of a metric card, as a native page draws it: its [chart], with a [title] when the card's own doesn't name it (grade under
 * elevation) and a [detail] for its end of the header (a left/right pair's averages), each null when the card's headline already says it.
 */
@Immutable class CardLine(val title: AppText?, val detail: AppText?, val chart: LineChart)

/**
 * How a metric card's lines read and chart (feed/activity-page-layout, dev/shared-display-text): each line's title, header detail and
 * description, which the shared chart asks too, and each as a [LineChart] over the activity's time for a native page, with what the shared
 * chart draws around it: the elevation behind an effort line, a pair's right leg, every tank on one chart, a dive's ceiling, gas switches
 * and warnings, an e-bike's assist mode in steps, then the power curve and the gears.
 */
@Suppress("TooManyFunctions") // One per chart the card draws and the words each says.
object CardLines {
    /** The card's charts, in drawing order; a series with fewer than two points has none. */
    fun of(card: MetricCard, charts: ActivityCharts, units: UnitSystem): List<CardLine> {
        val elevation = charts.lines.firstOrNull { it.kind == StreamKind.Elevation }
        val lines =
            if (card.section == DetailSection.TankPressure) {
                listOfNotNull(tanks(card.series, charts.durationSeconds, units))
            } else {
                card.series.mapNotNull { series -> line(card, series, charts, elevation, units) }
            }
        val extra =
            when (card.section) {
                DetailSection.Power -> listOfNotNull(powerCurve(card.powerCurve))
                DetailSection.Gears -> listOfNotNull(gears(card.gears, charts.durationSeconds, elevation, units))
                else -> emptyList()
            }
        return lines + extra
    }

    private fun line(card: MetricCard, series: LineSeries, charts: ActivityCharts, elevation: LineSeries?, units: UnitSystem): CardLine? {
        val pace = card.pace.takeIf { series.kind == StreamKind.Speed }
        // The card's title and headline already name and sum up its own line; a second (grade under elevation) needs a title.
        val titled = series.kind.name != card.section.name && card.series.size > 1
        val behind = elevation?.takeIf { series.kind in OVER_ELEVATION && it !== series }
        val dive = charts.takeIf { series.kind == StreamKind.Depth }
        return chart(series, charts.durationSeconds, pace, units, behind, dive)?.let { chart ->
            CardLine(
                title = title(series.kind, pace).takeIf { titled },
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
        val text = { v: Double -> valueText(series.kind, v, pace, units) }
        val max = series.max?.let(text)
        val average = series.average?.let(text)
        val right = series.right
        return when {
            max == null -> null
            average == null -> AppText.named("chart_max_only", max)
            right != null -> AppText.named("chart_left_right", average, right.average?.let(text) ?: StatText.missing)
            else -> AppText.named("chart_avg_max", average, max)
        }
    }

    /** What a line's chart shows, for a screen reader: a pace's fastest is its best, not its maximum. */
    fun description(title: AppText, average: AppText?, max: AppText, pace: PaceKind?): AppText =
        when {
            pace != null -> AppText.named("chart_description_pace", average ?: AppText.written(""), max)
            average == null -> AppText.named("chart_description_max", title, max)
            else -> AppText.named("chart_description", title, average, max)
        }

    /**
     * [series] over the activity's [durationSeconds] as a [LineChart]; a pace line inverted, the faster at the top, and capped. With
     * [elevation] it's drawn faintly behind and each readout names the elevation there; a left/right pair's right leg is a second line; a
     * dive's depth (with its [dive] charts) is filled up to the surface with its ceiling, gas switches and warnings; assist mode is in
     * steps.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList") // A chart, each thing drawn with it, and their data.
    fun chart(
        series: LineSeries,
        durationSeconds: Int,
        pace: PaceKind?,
        units: UnitSystem,
        elevation: LineSeries? = null,
        dive: ActivityCharts? = null,
    ): LineChart? {
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
        val right =
            series.right
                ?.takeIf { it.points.size >= 2 }
                ?.let { leg ->
                    val converted = leg.points.map { convert(it.value) }
                    ChartLine(
                        leg.points
                            .mapIndexed { i, sample -> LinePoint(sample.seconds.toDouble(), sign * converted[i], sample.gapBefore) }
                            .toImmutableList(),
                    )
                }
        val depthCeiling =
            dive
                ?.ceiling
                ?.takeIf { it.size >= 2 }
                ?.let { samples ->
                    ChartLine(samples.map { LinePoint(it.seconds.toDouble(), sign * convert(it.value), it.gapBefore) }.toImmutableList())
                }
        val others = listOfNotNull(right, depthCeiling)
        // The axis spans every line it draws.
        val axis =
            ChartAxis.of(
                values + others.flatMap { line -> line.points.map { sign * it.y } },
                inverted = inverted,
                timeValues = pace != null,
            )
        val heights = elevation?.let { elevationPoints(it, units) }.orEmpty()
        val readouts =
            series.points.mapIndexed { i, sample ->
                val at = time(sample.seconds)
                nearestY(heights, sample.seconds.toDouble())?.let { height ->
                    AppText.named("chart_marker_elevation", at, text(values[i]), StreamUnits.text(StreamKind.Elevation, height, units))
                } ?: AppText.named("chart_marker", at, text(values[i]))
            }
        val notes = listOfNotNull(dive?.let { depthNotes(it) }, AppText.named("chart_left_right_help").takeIf { right != null })
        return LineChart.of(
            points = points,
            xMin = 0.0,
            xMax = durationSeconds.toDouble().coerceAtLeast(points.last().x),
            xTicks = timeTicks(durationSeconds),
            yLabel = { axisLabel(sign * it) },
            readouts = readouts,
            description = sentences(listOf(description(title, average?.let(text), maxText, pace)) + notes),
            axis = axis,
            average = average?.let { sign * it.coerceAtMost(ceiling) },
            steps = series.kind == StreamKind.EbikeAssistMode,
            backdrop = LineChart.backdropOf(heights, axis),
            others = others,
            marks = dive?.let { depthMarks(it) }.orEmpty(),
            fillAbove = series.kind == StreamKind.Depth,
        )
    }

    /**
     * A chart of every tank on one scale, the first filled and the rest as more lines, headed by each tank's maximum (untitled, as its card
     * names it); one tank charts as any line. Null without a tank of two points.
     */
    fun tanks(tanks: List<LineSeries>, durationSeconds: Int, units: UnitSystem): CardLine? {
        val drawn = tanks.filter { it.points.size >= 2 }
        val base = drawn.firstOrNull()?.let { chart(it, durationSeconds, pace = null, units) } ?: return null
        return if (drawn.size == 1) CardLine(title = null, detail = null, chart = base) else tanksLine(drawn, base, units)
    }

    /** Several [tanks] on one chart, built on [base], the first tank's own. */
    private fun tanksLine(drawn: List<LineSeries>, base: LineChart, units: UnitSystem): CardLine {
        val convert = { v: Double -> StreamUnits.convert(StreamKind.TankPressure, v, units) }
        val others =
            drawn.drop(1).mapIndexed { i, tank ->
                ChartLine(tank.points.map { LinePoint(it.seconds.toDouble(), convert(it.value), it.gapBefore) }.toImmutableList(), tone = i)
            }
        val axis = ChartAxis.of(drawn.flatMap { tank -> tank.points.map { convert(it.value) } })
        val chart =
            LineChart.of(
                points = base.points,
                xMin = base.xMin,
                xMax = base.xMax,
                xTicks = base.xTicks,
                yLabel = StreamUnits::axis,
                readouts = base.readouts,
                description = sentences(listOf(base.description, tanksHelp(drawn.size))),
                axis = axis,
                average = base.average,
                others = others,
            )
        // The card's own title already names it; its header gives each tank's maximum.
        return CardLine(title = null, detail = tanksDetail(drawn, units), chart = chart)
    }

    /** Several tanks' maxima, "200 bar · 180 bar"; null without one. */
    fun tanksDetail(tanks: List<LineSeries>, units: UnitSystem): AppText? =
        tanks
            .mapNotNull { it.max?.let { max -> StreamUnits.valueText(StreamKind.TankPressure, max, units) } }
            .reduceOrNull { a, b -> AppText.named("text_dot_pair", a, b) }

    /** What a chart of [count] tanks shows, for a screen reader. */
    fun tanksHelp(count: Int): AppText = AppText.named("chart_tank_pressure_help", AppText.written("$count"))

    /** A dive's gas switches, by the gas's name, and its warnings, in the error colour, as marks on its depth chart. */
    fun depthMarks(charts: ActivityCharts): List<ChartMark> =
        charts.gasSwitches.map { ChartMark(it.seconds.toDouble(), DiveText.gasName(it.oxygen, it.helium)) } +
            charts.alerts.map { ChartMark(it.seconds.toDouble(), LabelText.diveAlert(it.kind), warning = true) }

    /** What a depth chart marks, for a screen reader: each switch and warning at its time, and the ceiling; null for none. */
    fun depthNotes(charts: ActivityCharts): AppText? {
        val switches =
            AppText.joined(
                charts.gasSwitches.map { AppText.named("chart_depth_switch", DiveText.gasName(it.oxygen, it.helium), time(it.seconds)) },
            )
        val alerts =
            AppText.joined(charts.alerts.map { AppText.named("chart_depth_switch", LabelText.diveAlert(it.kind), time(it.seconds)) })
        val notes =
            listOfNotNull(
                switches?.let { AppText.named("chart_depth_switches", it) },
                alerts?.let { AppText.named("chart_depth_alerts", it) },
                AppText.named("chart_depth_ceiling").takeIf { charts.ceiling.size >= 2 },
            )
        return notes.takeIf { it.isNotEmpty() }?.let(::sentences)
    }

    /**
     * The best average power for each duration, each an equal step along the axis so 1 s to 1 min takes no more room than 1 min to 1 h,
     * labelled at 5 s, 1, 5 and 20 min and 1 h; null with fewer than two durations.
     */
    fun powerCurve(curve: List<CurvePoint>): CardLine? {
        if (curve.size < 2) return null
        val points = curve.mapIndexed { i, point -> LinePoint(i.toDouble(), point.watts.toDouble()) }
        val watts = { w: Double -> AppText.named("unit_watts", AppText.written(formatWhole(w.roundToInt()))) }
        val chart =
            LineChart.of(
                points = points,
                // Half a step either side, so a label at either end has room.
                xMin = -CURVE_EDGE,
                xMax = points.last().x + CURVE_EDGE,
                xTicks =
                    curve.mapIndexedNotNull { i, p ->
                        ChartTick(i.toDouble(), durationLabel(p.seconds)).takeIf { p.seconds in CURVE_STATS }
                    },
                yLabel = watts,
                readouts = curve.map { AppText.named("chart_marker", durationLabel(it.seconds), watts(it.watts.toDouble())) },
                description = powerCurveDescription(curve),
                axis = ChartAxis.of(points.map { it.y }),
            )
        return CardLine(title = AppText.named("chart_power_curve"), detail = null, chart = chart)
    }

    /** What the power curve shows, for a screen reader: the best power at each labelled duration. */
    fun powerCurveDescription(curve: List<CurvePoint>): AppText {
        val points =
            curve
                .filter { it.seconds in CURVE_STATS }
                .map {
                    AppText.named(
                        "chart_power_curve_point",
                        durationLabel(it.seconds),
                        AppText.named("unit_watts", AppText.written(formatWhole(it.watts))),
                    )
                }
        val listed = points.reduceOrNull { a, b -> AppText.named("text_semicolon_pair", a, b) } ?: AppText.written("")
        return AppText.named("chart_power_curve_help", listed)
    }

    /** A power curve's duration: "5s", "20m", "1h". */
    fun durationLabel(seconds: Int): AppText =
        when {
            seconds < MINUTE -> AppText.named("unit_duration_seconds", AppText.written("$seconds"))
            seconds < HOUR -> AppText.named("unit_duration_minutes", AppText.written("${seconds / MINUTE}"))
            else -> AppText.named("unit_duration_hours", AppText.written("${seconds / HOUR}"))
        }

    /** The rear gear after each shift, held to the end of the activity, in steps over the [elevation]; null with no rear gear. */
    fun gears(gears: List<GearPoint>, durationSeconds: Int, elevation: LineSeries?, units: UnitSystem): CardLine? {
        val shifts = gears.mapNotNull { gear -> gear.rear?.let { LinePoint(gear.seconds.toDouble(), it.toDouble()) } }
        val last = shifts.lastOrNull()
        val points = if (last != null && last.x < durationSeconds) shifts + last.copy(x = durationSeconds.toDouble()) else shifts
        if (points.size < 2) return null
        val heights = elevation?.let { elevationPoints(it, units) }.orEmpty()
        val axis = ChartAxis.of(points.map { it.y })
        val chart =
            LineChart.of(
                points = points,
                xMin = 0.0,
                xMax = durationSeconds.toDouble().coerceAtLeast(points.last().x),
                xTicks = timeTicks(durationSeconds),
                yLabel = { AppText.written("${it.roundToInt()}") },
                readouts =
                    points.map { p ->
                        val at = time(p.x.toInt())
                        val gear = gearText(p.y.roundToInt())
                        nearestY(heights, p.x)?.let {
                            AppText.named("chart_marker_elevation", at, gear, StreamUnits.text(StreamKind.Elevation, it, units))
                        } ?: AppText.named("chart_marker", at, gear)
                    },
                description = gearsHelp,
                axis = axis,
                steps = true,
                backdrop = LineChart.backdropOf(heights, axis),
            )
        return CardLine(title = AppText.named("chart_gears"), detail = null, chart = chart)
    }

    /** "Gear 4". */
    fun gearText(gear: Int): AppText = AppText.named("chart_gear_value", AppText.written("$gear"))

    val gearsHelp: AppText = AppText.named("chart_gears_help")

    /** [series] (an elevation) in the viewer's units, as points over time. */
    private fun elevationPoints(series: LineSeries, units: UnitSystem): List<LinePoint> =
        series.points.map { LinePoint(it.seconds.toDouble(), StreamUnits.convert(StreamKind.Elevation, it.value, units), it.gapBefore) }

    /** The y of the point in [points] (sorted by x) closest to [x]; null for none. */
    private fun nearestY(points: List<LinePoint>, x: Double): Double? {
        val i = points.binarySearch { it.x.compareTo(x) }
        val after = -i - 1
        return if (i >= 0) points[i].y
        else listOfNotNull(points.getOrNull(after - 1), points.getOrNull(after)).minByOrNull { abs(it.x - x) }?.y
    }

    /** [texts] as one passage, sentence after sentence. */
    fun sentences(texts: List<AppText>): AppText = texts.reduce { a, b -> AppText.named("text_space_pair", a, b) }

    /** Labels along an activity's time: a round step of minutes or hours, at most five labels from the start. */
    fun timeTicks(durationSeconds: Int): List<ChartTick> {
        val step = TIME_STEPS.firstOrNull { durationSeconds / it <= MAX_TIME_GAPS } ?: (durationSeconds / MAX_TIME_GAPS + 1)
        return (0..durationSeconds / step).map { ChartTick((it * step).toDouble(), time(it * step)) }
    }

    private fun time(seconds: Int): AppText = AppText.written(StatText.duration(seconds.toLong()))

    /** How much slower than its average a pace chart goes. */
    const val PACE_CEILING = 1.5
    private const val MAX_TIME_GAPS = 4
    private const val CURVE_EDGE = 0.5
    private const val MINUTE = 60
    private const val HOUR = 3600
}

/** Lines that read against the climbs; not elevation itself, nor a dive's depth. */
private val OVER_ELEVATION =
    setOf(StreamKind.HeartRate, StreamKind.Power, StreamKind.Speed, StreamKind.Cadence, StreamKind.Grade, StreamKind.Temperature)

/** The power curve's labelled durations. */
private val CURVE_STATS = setOf(5, 60, 300, 1200, 3600)

/** The steps an activity's time axis is labelled at, in seconds. */
private val TIME_STEPS = listOf(60, 120, 300, 600, 900, 1800, 3600, 7200, 10800, 14400, 21600, 43200)

/**
 * Time in one zone as a row reads it: "Z2", its name, both as one [title] ("Z2 Endurance"), its range with its unit, its time, and its
 * share of the time in all of them as a [fraction] and a [percent] ("58%", "<1%" for a sliver). [spoken] is the row as one sentence.
 */
@Immutable
class ZoneRow(val label: AppText, val name: AppText, val range: AppText, val time: AppText, val fraction: Double) {
    val title: AppText = AppText.named("chart_zone_title", label, name)
    val percent: AppText = ZoneRows.percent(fraction)
    val spoken: AppText = AppText.named("chart_zone_spoken", title, range, time, percent)
}

/** Time in heart rate or power zones (feed/activity-zones), as both UIs word it. */
object ZoneRows {
    /** The heart rate or power zones' heading. */
    fun heading(section: DetailSection): AppText =
        AppText.named(if (section == DetailSection.Power) "chart_power_zones" else "chart_heart_rate_zones")

    /** [card]'s zones as rows; none without zones. */
    fun of(card: MetricCard): List<ZoneRow> = card.zones?.let { of(it, card.section) }.orEmpty()

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

    /** A zone's share of the time, rounded: never "0%" beside a sliver of bar, which reads "<1%". */
    fun percent(fraction: Double): AppText {
        val rounded = (fraction * PERCENT).roundToInt()
        return if (rounded == 0 && fraction > 0) {
            AppText.named("chart_zone_percent_under")
        } else {
            AppText.named("unit_percent", AppText.written(formatWhole(rounded.toLong())))
        }
    }

    /** Why the owner sees no zones, and the button to the zones screen. */
    val missing: AppText = AppText.named("charts_missing_zones")
    val setThresholds: AppText = AppText.named("charts_set_thresholds")

    private const val PERCENT = 100
}

/** A table row: its cells in column order, and the sentence a screen reader says for it. */
@Immutable class TableRow(val cells: ImmutableList<AppText>, val spoken: AppText)

/**
 * A table: its column [headers] and its [rows]. [wideFirst] gives the first column (a lift's name) a wide share; [wideColumn] is the column
 * (a name) given twice the others' share, -1 for none.
 */
@Immutable
class DetailTable(
    val headers: ImmutableList<AppText>,
    val rows: ImmutableList<TableRow>,
    val wideFirst: Boolean,
    val wideColumn: Int = if (wideFirst) 0 else -1,
)

/**
 * The laps table (feed/activity-page-layout): each lap's time, distance, speed and effort, with its intensity when the laps record one. A
 * ski day's laps are its runs, numbered, with their descent, and its lift rides, named by their lift when one matched. Both UIs ask it.
 */
object LapTableTexts {
    /** [charts]' laps, a ski day's as runs and lifts. */
    fun of(charts: ActivityCharts, units: UnitSystem): DetailTable = of(charts.laps, ski = charts.skiRuns != null, units)

    @Suppress("LongMethod", "CyclomaticComplexMethod") // A column at a time, each shown or not.
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
