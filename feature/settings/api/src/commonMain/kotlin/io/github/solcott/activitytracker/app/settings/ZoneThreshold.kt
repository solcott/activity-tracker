package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.data.training.PaceText
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.data.training.ThresholdInput
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails

/** The most digits a heart rate takes: the lexicon's highest, 250. */
private val HEART_RATE_DIGITS = TrainingDetails.MAX_HEART_RATE.toString().length

/** The most digits a threshold power takes: the lexicon's highest, 2,500 W. */
private val POWER_DIGITS = TrainingDetails.MAX_FTP.toString().length

/** The longest pace [PaceText] writes: the lexicon's slowest threshold speed, in the unit that makes it longest. */
private val PACE_CHARS = PaceUnit.entries.maxOf { PaceText.format(TrainingDetails.MIN_THRESHOLD_SPEED, it).length }

/**
 * A threshold the zones forms take as text: which `Threshold` it is ([token]), what a field holds of it, and whether a text is a value it
 * takes. A native field applies [digitsOnly] and [maxLength] as it is typed; the shared fields show a value that isn't valid in the error
 * colour. An enum is a type Swift reads as its own, so a native view names a field by this rather than by its token
 * (dev/ios-swift-interop).
 */
enum class ZoneThreshold(val token: String, val digitsOnly: Boolean, val maxLength: Int) {
    ThresholdHeartRate(Threshold.THRESHOLD_HEART_RATE, digitsOnly = true, HEART_RATE_DIGITS),
    MaxHeartRate(Threshold.MAX_HEART_RATE, digitsOnly = true, HEART_RATE_DIGITS),
    RestingHeartRate(Threshold.RESTING_HEART_RATE, digitsOnly = true, HEART_RATE_DIGITS),
    Power(Threshold.FUNCTIONAL_THRESHOLD_POWER, digitsOnly = true, POWER_DIGITS),

    /** Threshold pace, as `m:ss` (or whole seconds) per a sport's pace unit. */
    Pace(Threshold.THRESHOLD_SPEED, digitsOnly = false, PACE_CHARS);

    /** Whether [text] is blank or a value this threshold takes, a pace read per [paceUnit]. */
    fun isValid(text: String, paceUnit: PaceUnit): Boolean =
        when (this) {
            ThresholdHeartRate,
            MaxHeartRate,
            RestingHeartRate -> ThresholdInput.isValidHeartRate(text)
            Power -> ThresholdInput.isValidPower(text)
            Pace -> ThresholdInput.isValidPace(text, paceUnit)
        }
}
