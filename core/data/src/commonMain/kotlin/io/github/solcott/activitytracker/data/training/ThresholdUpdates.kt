package io.github.solcott.activitytracker.data.training

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.activityfile.DeviceThresholds
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.TrainingSetAt
import kotlin.time.Instant

/** A threshold an import will update: [threshold] is a [Threshold] value, and [from] is null when it wasn't set. */
@Immutable data class ThresholdChange(val threshold: String, val from: Long?, val to: Long)

/** Which thresholds an imported activity updates from its device's settings, and the training details after it does. */
object ThresholdUpdates {
    /**
     * The changes [device] brings to [current]: a threshold the athlete lets imports update, that the device had set to something else,
     * from an activity that started after the threshold was last set (so an old file can't undo a newer value).
     */
    fun pending(current: TrainingDetails?, device: DeviceThresholds?, activityStart: Instant): List<ThresholdChange> {
        if (current == null || device == null) return emptyList()
        return current.autoUpdate.distinct().mapNotNull { threshold ->
            val to = deviceValue(device, threshold)?.toLong() ?: return@mapNotNull null
            val from = value(current, threshold)
            val setAt = current.setAt?.let { setAt(it, threshold) }
            ThresholdChange(threshold, from, to).takeIf { to != from && (setAt == null || activityStart > setAt) }
        }
    }

    /** [current] with [changes] made, each marked as set at [activityStart]. */
    fun apply(current: TrainingDetails, changes: List<ThresholdChange>, activityStart: Instant): TrainingDetails =
        changes.fold(current) { training, change ->
            withValue(training, change.threshold, change.to).copy(setAt = markSet(training.setAt, change.threshold, activityStart))
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
