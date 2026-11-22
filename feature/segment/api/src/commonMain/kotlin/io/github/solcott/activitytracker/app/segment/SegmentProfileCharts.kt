package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.chart.ChartTick
import io.github.solcott.activitytracker.app.chart.LineChart
import io.github.solcott.activitytracker.app.chart.LinePoint
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.chart.BandStop
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatDecimal
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * This terrain profile as a chart (segments/segment-elevation), for both UIs: elevation over distance along it, both in [units], labelled
 * along the bottom at a round step ([distanceLabels]), tinted by grade ([GradeBand], in the runs [BandStop.runs] keeps), each point reading
 * out its distance, elevation and grade.
 */
fun SegmentProfile.chart(units: UnitSystem): LineChart = profileChart(listOf(this), units)

/**
 * [pieces] one after another as one chart, as [chart] draws one (a trail's line pieces, segments/path-profile): each starts where the one
 * before it ended, the line breaking between them ([LinePoint.gapBefore]) except before a piece [joined] marks as running on from the one
 * before it, over their whole length in [units].
 */
internal fun profileChart(pieces: List<SegmentProfile>, units: UnitSystem, joined: List<Boolean> = emptyList()): LineChart {
    require(pieces.isNotEmpty()) { "a chart needs a piece" }
    val lengths = pieces.map { UnitConversions.toDistanceUnit(it.distanceMeters.toDouble(), units) }
    val length = lengths.sum()
    val starts = lengths.runningFold(0.0, Double::plus)
    val decimals = distanceDecimals(length)
    fun distance(value: Double) = unit(units, "unit_distance_km", "unit_distance_mi", formatDecimal(value, decimals))
    fun elevation(value: Double) = unit(units, "unit_elevation_m", "unit_elevation_ft", formatDecimal(value, 0))
    val points = mutableListOf<LinePoint>()
    val readouts = mutableListOf<AppText>()
    val grades = mutableListOf<Int>()
    pieces.forEachIndexed { p, piece ->
        val step = if (piece.heightsM.size > 1) lengths[p] / (piece.heightsM.size - 1) else 0.0
        val bands = piece.gradeBands
        piece.heightsM.forEachIndexed { i, h ->
            val point =
                LinePoint(
                    starts[p] + i * step,
                    UnitConversions.toElevationUnit(h, units),
                    gapBefore = p > 0 && i == 0 && !joined.getOrElse(p) { false },
                )
            val grade = piece.gradesPercent.getOrElse(piece.gradeIndex(i, piece.heightsM.size)) { 0.0 }
            points += point
            readouts +=
                AppText.named(
                    "segment_profile_readout",
                    distance(point.x),
                    elevation(point.y),
                    AppText.named("unit_percent", AppText.written(formatDecimal(grade, 1))),
                )
            grades += bands.getOrElse(piece.gradeIndex(i, piece.heightsM.size)) { 0 }
        }
    }
    val meters = pieces.sumOf { it.distanceMeters }.toDouble()
    return LineChart.of(
        points = points,
        xMin = 0.0,
        xMax = length,
        xTicks = distanceLabels(length).map { ChartTick(it, distance(it)) },
        yLabel = ::elevation,
        readouts = readouts,
        description =
            AppText.named(
                "segment_profile_description",
                StatText.distance(meters, units),
                StatText.elevation(pieces.first().heightsM.first(), units),
                StatText.elevation(pieces.last().heightsM.last(), units),
            ),
        bands = BandStop.runs(points.map { (if (length > 0) it.x / length else 0.0).toFloat() }, grades),
    )
}

/** Which grade the [i]th of [count] chart points reads out: the profile's grades are as many as its heights. */
internal fun SegmentProfile.gradeIndex(i: Int, count: Int): Int =
    if (count > 1) (i.toDouble() / (count - 1) * (gradesPercent.size - 1)).roundToInt().coerceIn(0, gradesPercent.lastIndex) else 0

/** Distance labels from 0 at a round step that gives at most [MAX_LABELS] of them along [length], in the viewer's distance unit. */
internal fun distanceLabels(length: Double): List<Double> {
    if (length <= 0) return listOf(0.0)
    val step = labelStep(length)
    return (0..floor(length / step).toInt()).map { it * step }
}

/** How many decimals a distance label needs at the step [distanceLabels] picks for [length]. */
internal fun distanceDecimals(length: Double): Int {
    val step = labelStep(length)
    return when {
        step >= 1 -> 0
        (step * TENTHS).roundToLong() / TENTHS == step -> 1
        else -> 2
    }
}

private fun labelStep(length: Double): Double = LABEL_STEPS.firstOrNull { length / it <= MAX_LABELS } ?: LABEL_STEPS.last()

private fun unit(units: UnitSystem, metric: String, imperial: String, value: String): AppText =
    AppText.named(if (units == UnitSystem.Metric) metric else imperial, AppText.written(value))

private val LABEL_STEPS = listOf(0.1, 0.2, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0)
private const val MAX_LABELS = 5
private const val TENTHS = 10.0
