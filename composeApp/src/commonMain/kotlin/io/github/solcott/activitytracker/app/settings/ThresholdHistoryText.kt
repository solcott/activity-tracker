package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.month_short_1
import io.github.solcott.activitytracker.composeApp.resources.month_short_10
import io.github.solcott.activitytracker.composeApp.resources.month_short_11
import io.github.solcott.activitytracker.composeApp.resources.month_short_12
import io.github.solcott.activitytracker.composeApp.resources.month_short_2
import io.github.solcott.activitytracker.composeApp.resources.month_short_3
import io.github.solcott.activitytracker.composeApp.resources.month_short_4
import io.github.solcott.activitytracker.composeApp.resources.month_short_5
import io.github.solcott.activitytracker.composeApp.resources.month_short_6
import io.github.solcott.activitytracker.composeApp.resources.month_short_7
import io.github.solcott.activitytracker.composeApp.resources.month_short_8
import io.github.solcott.activitytracker.composeApp.resources.month_short_9
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_device
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_device_named
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_garmin
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_intervals_icu
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_manual
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_strava
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_trainer_road
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_training_peaks
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_unknown
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_wahoo
import io.github.solcott.activitytracker.composeApp.resources.threshold_source_zwift
import io.github.solcott.activitytracker.composeApp.resources.zones_history_change_down
import io.github.solcott.activitytracker.composeApp.resources.zones_history_change_faster
import io.github.solcott.activitytracker.composeApp.resources.zones_history_change_none
import io.github.solcott.activitytracker.composeApp.resources.zones_history_change_slower
import io.github.solcott.activitytracker.composeApp.resources.zones_history_change_up
import io.github.solcott.activitytracker.composeApp.resources.zones_history_chart_description
import io.github.solcott.activitytracker.composeApp.resources.zones_history_date
import io.github.solcott.activitytracker.composeApp.resources.zones_history_date_short
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_max_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_resting_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_threshold_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_threshold_pace
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_threshold_power
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_value_bpm
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_value_pace
import io.github.solcott.activitytracker.composeApp.resources.zones_summary_value_watts
import io.github.solcott.activitytracker.data.training.PaceText
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.ThresholdEntry
import io.github.solcott.activitytracker.lexicon.app.ThresholdSource
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource

// Text the threshold history screen shows: labels, values, dates and changes.

@Composable
internal fun changeText(section: ThresholdHistorySection, change: ThresholdTrend): String {
    val since = shortDate(change.since)
    return when {
        change.to == change.from -> stringResource(Res.string.zones_history_change_none, since)
        section.threshold == Threshold.THRESHOLD_SPEED -> {
            val seconds = abs(PaceText.seconds(change.from, section.paceUnit) - PaceText.seconds(change.to, section.paceUnit)).toInt()
            val unit = paceUnitLabel(section.paceUnit)
            // A higher speed is a faster pace.
            if (change.to > change.from) stringResource(Res.string.zones_history_change_faster, seconds, unit, since)
            else stringResource(Res.string.zones_history_change_slower, seconds, unit, since)
        }
        else -> {
            val amount = thresholdValue(section.threshold, abs(change.to - change.from), section.paceUnit)
            stringResource(
                if (change.to > change.from) Res.string.zones_history_change_up else Res.string.zones_history_change_down,
                amount,
                since,
            )
        }
    }
}

@Composable
internal fun chartDescription(section: ThresholdHistorySection, chart: ThresholdChart): String =
    stringResource(
        Res.string.zones_history_chart_description,
        thresholdLabel(section.threshold),
        thresholdValue(section.threshold, chart.points.first().value, section.paceUnit),
        longDate(chart.start),
        thresholdValue(section.threshold, section.newest.entry.value, section.paceUnit),
    )

/** A chart value without its unit: "233", or "8:12" for a pace. */
internal fun pointLabel(section: ThresholdHistorySection, value: Long): String =
    if (section.threshold == Threshold.THRESHOLD_SPEED) PaceText.format(value, section.paceUnit) else value.toString()

@Composable
internal fun longDate(date: HistoryDate): String = stringResource(Res.string.zones_history_date, monthName(date.month), date.day, date.year)

@Composable
internal fun shortDate(date: HistoryDate): String = stringResource(Res.string.zones_history_date_short, monthName(date.month), date.day)

@Suppress("MagicNumber") // Months of the year.
@Composable
internal fun monthName(month: Int): String =
    stringResource(
        when (month) {
            1 -> Res.string.month_short_1
            2 -> Res.string.month_short_2
            3 -> Res.string.month_short_3
            4 -> Res.string.month_short_4
            5 -> Res.string.month_short_5
            6 -> Res.string.month_short_6
            7 -> Res.string.month_short_7
            8 -> Res.string.month_short_8
            9 -> Res.string.month_short_9
            10 -> Res.string.month_short_10
            11 -> Res.string.month_short_11
            else -> Res.string.month_short_12
        }
    )

@Composable
internal fun thresholdLabel(threshold: String): String =
    when (threshold) {
        Threshold.FUNCTIONAL_THRESHOLD_POWER -> stringResource(Res.string.zones_summary_threshold_power)
        Threshold.MAX_HEART_RATE -> stringResource(Res.string.zones_summary_max_heart_rate)
        Threshold.THRESHOLD_HEART_RATE -> stringResource(Res.string.zones_summary_threshold_heart_rate)
        Threshold.RESTING_HEART_RATE -> stringResource(Res.string.zones_summary_resting_heart_rate)
        Threshold.THRESHOLD_SPEED -> stringResource(Res.string.zones_summary_threshold_pace)
        else -> threshold
    }

@Composable
internal fun thresholdValue(threshold: String, value: Long, paceUnit: PaceUnit): String =
    when (threshold) {
        Threshold.FUNCTIONAL_THRESHOLD_POWER -> stringResource(Res.string.zones_summary_value_watts, value.toString())
        Threshold.THRESHOLD_SPEED ->
            stringResource(Res.string.zones_summary_value_pace, PaceText.format(value, paceUnit), paceUnitLabel(paceUnit))
        Threshold.MAX_HEART_RATE,
        Threshold.THRESHOLD_HEART_RATE,
        Threshold.RESTING_HEART_RATE -> stringResource(Res.string.zones_summary_value_bpm, value.toString())
        else -> value.toString()
    }

/** Where [entry] came from; a source this client doesn't know shows as its token. */
@Composable
internal fun sourceLabel(entry: ThresholdEntry): String {
    val device = entry.device
    return when (val source = entry.source) {
        null -> stringResource(Res.string.threshold_source_unknown)
        ThresholdSource.MANUAL -> stringResource(Res.string.threshold_source_manual)
        ThresholdSource.DEVICE ->
            if (device == null) stringResource(Res.string.threshold_source_device)
            else stringResource(Res.string.threshold_source_device_named, device)
        ThresholdSource.GARMIN -> stringResource(Res.string.threshold_source_garmin)
        ThresholdSource.TRAINER_ROAD -> stringResource(Res.string.threshold_source_trainer_road)
        ThresholdSource.TRAINING_PEAKS -> stringResource(Res.string.threshold_source_training_peaks)
        ThresholdSource.INTERVALS_ICU -> stringResource(Res.string.threshold_source_intervals_icu)
        ThresholdSource.ZWIFT -> stringResource(Res.string.threshold_source_zwift)
        ThresholdSource.WAHOO -> stringResource(Res.string.threshold_source_wahoo)
        ThresholdSource.STRAVA -> stringResource(Res.string.threshold_source_strava)
        else -> source
    }
}
