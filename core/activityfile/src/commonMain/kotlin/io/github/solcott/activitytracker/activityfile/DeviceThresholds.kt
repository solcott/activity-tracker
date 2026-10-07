package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.TrainingDetails

/** The athlete's thresholds as the recording device had them set when it recorded the activity. Each is null when unset. */
data class DeviceThresholds(
    /** Beats per minute. */
    val maxHeartRate: Int? = null,
    /** Lactate threshold heart rate, beats per minute. */
    val thresholdHeartRate: Int? = null,
    /** Cycling FTP, watts. */
    val functionalThresholdPower: Int? = null,
    /** Beats per minute. */
    val restingHeartRate: Int? = null,
) {
    companion object {
        /**
         * The device's settings, keeping only values the lexicon allows (devices write 0 for unset). FTP falls back to the session's
         * threshold power, which devices that leave out `zones_target` still write. Null when nothing is left.
         */
        fun interpret(athlete: DecodedAthleteSettings?, sessions: List<DecodedSummary>): DeviceThresholds? {
            val heartRate = TrainingDetails.MIN_HEART_RATE..TrainingDetails.MAX_HEART_RATE
            fun Int?.bpm() = this?.takeIf { it.toLong() in heartRate }
            fun Int?.watts() = this?.takeIf { it.toLong() in 1..TrainingDetails.MAX_FTP }
            return DeviceThresholds(
                    maxHeartRate = athlete?.maxHeartRate.bpm(),
                    thresholdHeartRate = athlete?.thresholdHeartRate.bpm(),
                    functionalThresholdPower =
                        athlete?.functionalThresholdPower.watts() ?: sessions.firstNotNullOfOrNull { it.thresholdPowerWatts.watts() },
                    restingHeartRate = athlete?.restingHeartRate.bpm(),
                )
                .takeUnless { it == DeviceThresholds() }
        }
    }
}
