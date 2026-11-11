package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.training.PaceText
import io.github.solcott.activitytracker.data.training.ZoneKind

/**
 * One threshold as Training zones lists it: "Threshold heart rate" and "170 bpm". [muted] when it has no value ("Not set") or is the
 * default's, which a UI shows in its secondary colour.
 */
@Immutable data class ZoneThresholdLine(val label: AppText, val value: AppText, val muted: Boolean)

/** What Training zones says about each set of zones, chosen here for the shared and the native UI (dev/shared-display-text). */
object TrainingZonesTexts {
    /** "Default zones", or "Run zones". */
    fun heading(summary: ZoneSummary): AppText =
        summary.sport?.let { AppText.named("zones_overview_heading", LabelText.sport(it)) } ?: AppText.named("zones_overview_default")

    /** What the set applies to. */
    fun help(summary: ZoneSummary): AppText =
        summary.sport?.let { AppText.named("zones_overview_sport_help", LabelText.sport(it)) }
            ?: AppText.named("zones_overview_default_help")

    /**
     * The thresholds [summary]'s zones use, in the order they show: threshold pace where it offers pace zones, the heart rates (resting
     * only for the defaults, since it is never per sport), then threshold power where it offers power zones. For a sport, a heart rate or
     * power it doesn't set itself is the default's and says so; pace is only ever a sport's own.
     */
    fun thresholds(summary: ZoneSummary): List<ZoneThresholdLine> {
        val training = summary.training
        val own = summary.override
        val isSport = summary.sport != null
        val unit = summary.paceUnit
        return buildList {
            if (summary.offers(ZoneKind.Speed)) {
                val pace =
                    training.thresholdSpeed?.let {
                        AppText.named("zones_summary_value_pace", AppText.written(PaceText.format(it, unit)), ZoneTexts.paceUnit(unit))
                    }
                add(line("zones_summary_threshold_pace", pace, inherited = false))
            }
            add(line("zones_summary_threshold_heart_rate", bpm(training.thresholdHeartRate), isSport && own?.thresholdHeartRate == null))
            add(line("zones_summary_max_heart_rate", bpm(training.maxHeartRate), isSport && own?.maxHeartRate == null))
            if (!isSport) add(line("zones_summary_resting_heart_rate", bpm(training.restingHeartRate), inherited = false))
            if (summary.offers(ZoneKind.Power)) {
                val watts =
                    training.functionalThresholdPower?.let { AppText.named("zones_summary_value_watts", AppText.written(it.toString())) }
                add(line("zones_summary_threshold_power", watts, isSport && own?.functionalThresholdPower == null))
            }
        }
    }

    private fun bpm(value: Long?): AppText? = value?.let { AppText.named("zones_summary_value_bpm", AppText.written(it.toString())) }

    /** "Not set" without a value, and "170 bpm (default)" for one taken from the defaults. */
    private fun line(label: String, value: AppText?, inherited: Boolean): ZoneThresholdLine =
        ZoneThresholdLine(
            label = AppText.named(label),
            value =
                when {
                    value == null -> AppText.named("zones_summary_unset")
                    inherited -> AppText.named("zones_overview_from_default", value)
                    else -> value
                },
            muted = value == null || inherited,
        )
}
