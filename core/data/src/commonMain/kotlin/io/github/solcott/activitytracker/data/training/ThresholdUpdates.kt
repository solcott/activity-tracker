package io.github.solcott.activitytracker.data.training

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.activityfile.DeviceThresholds
import io.github.solcott.activitytracker.lexicon.app.SportTraining
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.TrainingSetAt
import kotlin.time.Instant

/**
 * A threshold an import will update: [threshold] is a [Threshold] value, and [from] is null when it wasn't set. [sport] is the override it
 * updates, or null for the defaults.
 */
@Immutable data class ThresholdChange(val threshold: String, val from: Long?, val to: Long, val sport: String? = null)

/** Which thresholds an imported activity updates from its device's settings, and the training details after it does. */
object ThresholdUpdates {
    /**
     * The changes [device] brings to [current]: a threshold the athlete lets imports update, that the device had set to something else,
     * from an activity that started after the threshold was last set (so an old file can't undo a newer value).
     *
     * Devices keep thresholds per sport, so when [current] has an override for the activity's [sport], the device's heart rate and power
     * thresholds update that override, compared with the value the sport uses. Resting heart rate always updates the defaults.
     */
    fun pending(
        current: TrainingDetails?,
        device: DeviceThresholds?,
        activityStart: Instant,
        sport: String? = null,
    ): List<ThresholdChange> {
        if (current == null || device == null) return emptyList()
        val override = current.override(sport)
        return current.autoUpdate.distinct().mapNotNull { threshold ->
            val to = deviceValue(device, threshold)?.toLong() ?: return@mapNotNull null
            val target = override?.takeIf { threshold != Threshold.RESTING_HEART_RATE }
            val own = target?.let { sportValue(it, threshold) }
            val from = own ?: value(current, threshold)
            // The default's time counts while the override inherits the default's value.
            val setAt = (if (own != null) target.setAt else current.setAt)?.let { setAt(it, threshold) }
            ThresholdChange(threshold, from, to, target?.sport).takeIf { to != from && (setAt == null || activityStart > setAt) }
        }
    }

    /** [current] with [changes] made, each marked as set at [activityStart]. */
    fun apply(current: TrainingDetails, changes: List<ThresholdChange>, activityStart: Instant): TrainingDetails =
        changes.fold(current) { training, change ->
            val target = change.sport?.let { training.override(it) }
            if (target == null) {
                withValue(training, change.threshold, change.to).copy(setAt = markSet(training.setAt, change.threshold, activityStart))
            } else {
                val updated = withSportValue(target, change.threshold, change.to)
                training.withOverride(target.sport, updated.copy(setAt = markSet(target.setAt, change.threshold, activityStart)))
            }
        }

    /** [sport]'s value of [threshold], a [Threshold] value it can override; null when it inherits the default. */
    fun sportValue(sport: SportTraining, threshold: String): Long? =
        when (threshold) {
            Threshold.FUNCTIONAL_THRESHOLD_POWER -> sport.functionalThresholdPower
            Threshold.MAX_HEART_RATE -> sport.maxHeartRate
            Threshold.THRESHOLD_HEART_RATE -> sport.thresholdHeartRate
            else -> null
        }

    /** [sport] with [threshold], a [Threshold] value, set to [value]; resting heart rate isn't per sport, so it's left alone. */
    fun withSportValue(sport: SportTraining, threshold: String, value: Long?): SportTraining =
        when (threshold) {
            Threshold.FUNCTIONAL_THRESHOLD_POWER -> sport.copy(functionalThresholdPower = value)
            Threshold.MAX_HEART_RATE -> sport.copy(maxHeartRate = value)
            Threshold.THRESHOLD_HEART_RATE -> sport.copy(thresholdHeartRate = value)
            else -> sport
        }

    /** [training] with [threshold], a [Threshold] value, set to [value]. */
    fun withValue(training: TrainingDetails, threshold: String, value: Long?): TrainingDetails =
        when (threshold) {
            Threshold.FUNCTIONAL_THRESHOLD_POWER -> training.copy(functionalThresholdPower = value)
            Threshold.MAX_HEART_RATE -> training.copy(maxHeartRate = value)
            Threshold.THRESHOLD_HEART_RATE -> training.copy(thresholdHeartRate = value)
            Threshold.RESTING_HEART_RATE -> training.copy(restingHeartRate = value)
            else -> training
        }

    /** [training]'s value of [threshold], a [Threshold] value; null for one this client doesn't know. */
    fun value(training: TrainingDetails, threshold: String): Long? =
        when (threshold) {
            Threshold.FUNCTIONAL_THRESHOLD_POWER -> training.functionalThresholdPower
            Threshold.MAX_HEART_RATE -> training.maxHeartRate
            Threshold.THRESHOLD_HEART_RATE -> training.thresholdHeartRate
            Threshold.RESTING_HEART_RATE -> training.restingHeartRate
            else -> null
        }

    /** [setAt] with [threshold] marked as set at [at]. */
    fun markSet(setAt: TrainingSetAt?, threshold: String, at: Instant): TrainingSetAt {
        val times = setAt ?: TrainingSetAt()
        return when (threshold) {
            Threshold.FUNCTIONAL_THRESHOLD_POWER -> times.copy(functionalThresholdPower = at)
            Threshold.MAX_HEART_RATE -> times.copy(maxHeartRate = at)
            Threshold.THRESHOLD_HEART_RATE -> times.copy(thresholdHeartRate = at)
            Threshold.RESTING_HEART_RATE -> times.copy(restingHeartRate = at)
            else -> times
        }
    }

    private fun setAt(setAt: TrainingSetAt, threshold: String): Instant? =
        when (threshold) {
            Threshold.FUNCTIONAL_THRESHOLD_POWER -> setAt.functionalThresholdPower
            Threshold.MAX_HEART_RATE -> setAt.maxHeartRate
            Threshold.THRESHOLD_HEART_RATE -> setAt.thresholdHeartRate
            Threshold.RESTING_HEART_RATE -> setAt.restingHeartRate
            else -> null
        }

    private fun deviceValue(device: DeviceThresholds, threshold: String): Int? =
        when (threshold) {
            Threshold.FUNCTIONAL_THRESHOLD_POWER -> device.functionalThresholdPower
            Threshold.MAX_HEART_RATE -> device.maxHeartRate
            Threshold.THRESHOLD_HEART_RATE -> device.thresholdHeartRate
            Threshold.RESTING_HEART_RATE -> device.restingHeartRate
            else -> null
        }
}
