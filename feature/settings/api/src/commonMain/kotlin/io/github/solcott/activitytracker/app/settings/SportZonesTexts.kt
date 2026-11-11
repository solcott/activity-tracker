package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.training.PaceUnit

/** What a sport's zones form says, chosen here for the shared and the native UI (dev/shared-display-text). */
object SportZonesTexts {
    /** "Run zones". */
    fun title(sport: String): AppText = AppText.named("sport_zones_title", LabelText.sport(sport))

    /** That the sport's activities use these, and a blank threshold the default. */
    fun help(sport: String): AppText = AppText.named("sport_zones_help", LabelText.sport(sport))

    /** "Threshold pace (m:ss /km)". */
    fun paceLabel(unit: PaceUnit): AppText = AppText.named("sport_zones_threshold_pace", ZoneTexts.paceUnit(unit))

    /** "Default: 190", what a blank [threshold] falls back to on [state]'s sport; null when the defaults have none (or for pace). */
    fun defaultHint(state: SportZonesState, threshold: ZoneThreshold): AppText? {
        val value =
            when (threshold) {
                ZoneThreshold.ThresholdHeartRate -> state.defaults.thresholdHeartRate
                ZoneThreshold.MaxHeartRate -> state.defaults.maxHeartRate
                ZoneThreshold.RestingHeartRate -> state.defaults.restingHeartRate
                ZoneThreshold.Power -> state.defaults.functionalThresholdPower
                ZoneThreshold.Pace -> null
            }
        return value?.let { AppText.named("sport_zones_default", AppText.written(it.toString())) }
    }

    /** "Remove Run zones". */
    fun remove(sport: String): AppText = AppText.named("sport_zones_remove", LabelText.sport(sport))
}
