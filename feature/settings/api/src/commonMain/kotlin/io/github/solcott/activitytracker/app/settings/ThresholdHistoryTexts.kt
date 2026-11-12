package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.chart.ChartDot
import io.github.solcott.activitytracker.app.chart.ChartTick
import io.github.solcott.activitytracker.app.chart.LineChart
import io.github.solcott.activitytracker.app.chart.LinePoint
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.DateText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.chart.ChartAxis
import io.github.solcott.activitytracker.data.training.PaceText
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.ThresholdSource
import kotlin.math.abs
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** What threshold history says and charts, chosen here for the shared and the native UI (dev/shared-display-text). */
object ThresholdHistoryTexts {
    /** "Default", or the sport's name. */
    fun heading(group: ThresholdHistoryGroup): AppText =
        group.sport?.let { LabelText.sport(it) } ?: AppText.named("zones_history_default")

    /** What the group applies to. */
    fun help(group: ThresholdHistoryGroup): AppText =
        group.sport?.let { AppText.named("zones_history_sport_help", LabelText.sport(it)) } ?: AppText.named("zones_history_default_help")

    /** "3 thresholds · 7 values". */
    fun summary(group: ThresholdHistoryGroup): AppText =
        AppText.named(
            "zones_history_summary",
            count("zones_history_thresholds", group.thresholds.size),
            count("zones_history_values", group.valueCount),
        )

    /** Whether the group is expanded, for assistive technology. */
    fun expansion(group: ThresholdHistoryGroup): AppText =
        AppText.named(if (group.expanded) "zones_history_expanded" else "zones_history_collapsed")

    /** The bar's button: Collapse all while any group is expanded, else Expand all. */
    fun toggleAll(state: ThresholdHistoryState): AppText =
        AppText.named(if (state.anyExpanded) "zones_history_collapse_all" else "zones_history_expand_all")

    /** The threshold's name; a threshold this client doesn't know reads as its token. */
    fun label(threshold: String): AppText =
        when (threshold) {
            Threshold.FUNCTIONAL_THRESHOLD_POWER -> AppText.named("zones_summary_threshold_power")
            Threshold.MAX_HEART_RATE -> AppText.named("zones_summary_max_heart_rate")
            Threshold.THRESHOLD_HEART_RATE -> AppText.named("zones_summary_threshold_heart_rate")
            Threshold.RESTING_HEART_RATE -> AppText.named("zones_summary_resting_heart_rate")
            Threshold.THRESHOLD_SPEED -> AppText.named("zones_summary_threshold_pace")
            else -> AppText.written(threshold)
        }

    /** [value] of [section]'s threshold with its unit: "250 W", "170 bpm", "8:12 /mi". */
    fun value(section: ThresholdHistorySection, value: Long): AppText =
        when (section.threshold) {
            Threshold.FUNCTIONAL_THRESHOLD_POWER -> AppText.named("zones_summary_value_watts", AppText.written(value.toString()))
            Threshold.THRESHOLD_SPEED ->
                AppText.named(
                    "zones_summary_value_pace",
                    AppText.written(PaceText.format(value, section.paceUnit)),
                    ZoneTexts.paceUnit(section.paceUnit),
                )
            Threshold.MAX_HEART_RATE,
            Threshold.THRESHOLD_HEART_RATE,
            Threshold.RESTING_HEART_RATE -> AppText.named("zones_summary_value_bpm", AppText.written(value.toString()))
            else -> AppText.written(value.toString())
        }

    /** The threshold's value now. */
    fun current(section: ThresholdHistorySection): AppText = value(section, section.newest.entry.value)

    /** A chart value without its unit: "233", or "8:12" for a pace. */
    fun pointLabel(section: ThresholdHistorySection, value: Long): AppText =
        AppText.written(if (section.threshold == Threshold.THRESHOLD_SPEED) PaceText.format(value, section.paceUnit) else value.toString())

    /**
     * "+18 W since Mar 2", "12 s/mi faster since Jun 1", or "Only value"; null for a threshold with values but nothing to compare. A UI
     * shows it in the accent colour when [changed].
     */
    fun change(section: ThresholdHistorySection): AppText? {
        val change = section.change
        return when {
            section.rows.size == 1 -> AppText.named("zones_history_only_value")
            change == null -> null
            else -> changeText(section, change)
        }
    }

    /** Whether [change] says how the threshold moved, rather than that it has only one value. */
    fun changed(section: ThresholdHistorySection): Boolean = section.rows.size != 1 && section.change != null

    private fun changeText(section: ThresholdHistorySection, change: ThresholdTrend): AppText {
        val since = shortDate(change.since)
        return when {
            change.to == change.from -> AppText.named("zones_history_change_none", since)
            section.threshold == Threshold.THRESHOLD_SPEED -> {
                val seconds = abs(PaceText.seconds(change.from, section.paceUnit) - PaceText.seconds(change.to, section.paceUnit))
                // A higher speed is a faster pace.
                AppText.named(
                    if (change.to > change.from) "zones_history_change_faster" else "zones_history_change_slower",
                    AppText.written(seconds.toString()),
                    ZoneTexts.paceUnit(section.paceUnit),
                    since,
                )
            }
            else ->
                AppText.named(
                    if (change.to > change.from) "zones_history_change_up" else "zones_history_change_down",
                    value(section, abs(change.to - change.from)),
                    since,
                )
        }
    }

    /** "Mar 2". */
    fun shortDate(date: HistoryDate): AppText =
        AppText.named("zones_history_date_short", AppText.named("month_short_${date.month}"), AppText.written(date.day.toString()))

    /** "Mar 2, 2026". */
    fun longDate(date: HistoryDate): AppText = DateText.date(date.year, date.month, date.day)

    /** When [row]'s value took effect, or that it's unknown. */
    fun date(row: ThresholdHistoryRow): AppText = row.date?.let { longDate(it) } ?: AppText.named("zones_history_date_unknown")

    /** Where [row]'s value came from; a source this client doesn't know reads as its token. */
    fun source(row: ThresholdHistoryRow): AppText {
        val device = row.entry.device
        return when (val source = row.entry.source) {
            null -> AppText.named("threshold_source_unknown")
            ThresholdSource.MANUAL -> AppText.named("threshold_source_manual")
            ThresholdSource.DEVICE ->
                if (device == null) AppText.named("threshold_source_device")
                else AppText.named("threshold_source_device_named", AppText.written(device))
            ThresholdSource.GARMIN -> AppText.named("threshold_source_garmin")
            ThresholdSource.TRAINER_ROAD -> AppText.named("threshold_source_trainer_road")
            ThresholdSource.TRAINING_PEAKS -> AppText.named("threshold_source_training_peaks")
            ThresholdSource.INTERVALS_ICU -> AppText.named("threshold_source_intervals_icu")
            ThresholdSource.ZWIFT -> AppText.named("threshold_source_zwift")
            ThresholdSource.WAHOO -> AppText.named("threshold_source_wahoo")
            ThresholdSource.STRAVA -> AppText.named("threshold_source_strava")
            else -> AppText.written(source)
        }
    }

    /** "Remove 250 W from Threshold power", what a value's remove button reads as. */
    fun removeLabel(section: ThresholdHistorySection, row: ThresholdHistoryRow): AppText =
        AppText.named("zones_history_remove_description", value(section, row.entry.value), label(section.threshold))

    /** "Show all 5 values", or "Show fewer" while every value shows. */
    fun showAll(section: ThresholdHistorySection): AppText =
        if (section.showingAll) AppText.named("zones_history_show_fewer")
        else AppText.named("zones_history_show_all", AppText.written(section.rows.size.toString()))

    /** "2 undated values not charted", or null when every value has a date. */
    fun undated(section: ThresholdHistorySection): AppText? =
        section.undatedCount.takeIf { it > 0 }?.let { count("zones_history_undated", it) }

    /** The chart's first date, written under its left end; "Today" is under its right. */
    fun chartStart(chart: ThresholdChart): AppText = longDate(chart.start)

    /** What a threshold's chart reads as to assistive technology. */
    fun chartDescription(section: ThresholdHistorySection, chart: ThresholdChart): AppText =
        AppText.named(
            "zones_history_chart_description",
            label(section.threshold),
            value(section, chart.points.first().value),
            longDate(chart.start),
            value(section, section.newest.entry.value),
        )

    /**
     * [section]'s chart as a step line, since a threshold holds until it changes, running on to today at the right edge; null with fewer
     * than two dated values. [detailed]: a dot on each value (filled on the current one) with its value over it, and its lowest and
     * highest values on the y axis; otherwise a sparkline with neither, for a collapsed group's tile.
     */
    fun chart(section: ThresholdHistorySection, detailed: Boolean): LineChart? {
        val chart = section.chart ?: return null
        val placed = chart.points
        // Room above the highest point for its label (or a sparkline's even inset), in the 0–1 units of the points.
        val (top, bottom) = if (detailed) DETAILED_TOP to DETAILED_BOTTOM else SPARK_INSET to SPARK_INSET
        val span = 1.0 / (1 - top - bottom)
        val axis = ChartAxis(min = -bottom * span, max = 1 + top * span, step = span)
        val runsOn = placed.last().x < 1f
        val points = placed.map { LinePoint(it.x.toDouble(), it.y.toDouble()) } + listOfNotNull(
            LinePoint(1.0, placed.last().y.toDouble()).takeIf { runsOn },
        )
        val readouts = placed.map { value(section, it.value) } + listOfNotNull(value(section, placed.last().value).takeIf { runsOn })
        val yTicks =
            if (!detailed) emptyList()
            else
                listOf(chart.lowest, chart.highest).distinct().mapNotNull { extreme ->
                    placed.firstOrNull { it.value == extreme }?.let { ChartTick(it.y.toDouble(), pointLabel(section, extreme)) }
                }
        val dots =
            if (!detailed) emptyList()
            else placed.mapIndexed { i, point -> ChartDot(i, filled = point.current, label = pointLabel(section, point.value)) }
        return LineChart(
            points = points.toImmutableList(),
            xMin = 0.0,
            xMax = 1.0,
            axis = axis,
            yTicks = yTicks.toImmutableList(),
            xTicks = persistentListOf(),
            readouts = readouts.toImmutableList(),
            description = chartDescription(section, chart),
            steps = true,
            dots = dots.toImmutableList(),
        )
    }

    private fun count(name: String, quantity: Int): AppText =
        AppText.Plural(name, quantity, persistentListOf(AppText.written(formatWhole(quantity.toLong()))))

    private const val DETAILED_TOP = 0.22
    private const val DETAILED_BOTTOM = 0.16
    private const val SPARK_INSET = 0.12
}
