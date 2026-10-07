package io.github.solcott.activitytracker.data.training

import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.ThresholdEntry
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.TrainingSetAt
import kotlin.time.Instant

/**
 * The history of the athlete's thresholds ([TrainingDetails.history]): every value each threshold of the defaults and of each sport has
 * had. The newest entry for a threshold is its current value, unless the athlete has cleared it since.
 */
object TrainingHistory {
    /** A threshold of the defaults when [sport] is null, else of [sport]'s override. [threshold] is a [Threshold] value. */
    data class Key(val threshold: String, val sport: String? = null)

    val ThresholdEntry.key: Key
        get() = Key(threshold, sport)

    /** The thresholds the defaults have. */
    private val defaultThresholds = Threshold.all

    /** The thresholds a sport can override; resting heart rate isn't per sport. */
    private val sportThresholds =
        listOf(Threshold.FUNCTIONAL_THRESHOLD_POWER, Threshold.MAX_HEART_RATE, Threshold.THRESHOLD_HEART_RATE, Threshold.THRESHOLD_SPEED)

    /** Every threshold [training] has a place for: the defaults', then each sport's. */
    fun keys(training: TrainingDetails): List<Key> =
        defaultThresholds.map { Key(it) } + training.sports.flatMap { sport -> sportThresholds.map { Key(it, sport.sport) } }

    /**
     * [training] with an entry for each current value its history lacks, as of when the value was set (unknown for one saved before set
     * times were kept), so values saved before history was kept show in it. The source is unknown.
     */
    fun backfilled(training: TrainingDetails): TrainingDetails {
        val known = training.history.mapTo(HashSet()) { it.key }
        val missing =
            keys(training)
                .filterNot { it in known }
                .mapNotNull { key -> value(training, key)?.let { ThresholdEntry(key.threshold, it, setAt(training, key), key.sport) } }
        return if (missing.isEmpty()) training else training.copy(history = capped(training.history + missing))
    }

    /**
     * [after] with a history entry for each threshold whose value differs from [before]'s, from [source] (a `ThresholdSource` value) and
     * [device]. Each entry takes effect when [after] says the value was set, or [now] when it doesn't say. A cleared value adds nothing.
     */
    fun record(
        before: TrainingDetails,
        after: TrainingDetails,
        source: String?,
        now: Instant,
        device: String? = null,
    ): TrainingDetails {
        val added =
            keys(after).mapNotNull { key ->
                val value = value(after, key)?.takeIf { it != value(before, key) } ?: return@mapNotNull null
                ThresholdEntry(key.threshold, value, setAt(after, key) ?: now, key.sport, source, device)
            }
        return if (added.isEmpty()) after else after.copy(history = capped(after.history + added))
    }

    /**
     * [training] without [entry]. When [entry] is its threshold's current value, the threshold goes back to the newest entry left, or is
     * cleared when none is.
     */
    fun remove(training: TrainingDetails, entry: ThresholdEntry): TrainingDetails {
        val index = training.history.indexOf(entry)
        if (index < 0) return training
        val remaining = training.history.toMutableList().apply { removeAt(index) }
        val key = entry.key
        val wasCurrent = latest(training.history, key) == entry && value(training, key) == entry.value
        val previous = latest(remaining, key)
        val removed = training.copy(history = remaining)
        return if (wasCurrent) withValue(removed, key, previous?.value, previous?.setAt) else removed
    }

    /** [history]'s entries for [key], newest first; undated ones count as oldest. */
    fun entries(history: List<ThresholdEntry>, key: Key): List<ThresholdEntry> =
        history.filter { it.key == key }.sortedWith(compareByDescending(nullsFirst()) { it.setAt })

    /** The newest of [history]'s entries for [key]; of two set at the same time (or both undated), the later in the list. */
    private fun latest(history: List<ThresholdEntry>, key: Key): ThresholdEntry? =
        history
            .withIndex()
            .filter { it.value.key == key }
            .maxWithOrNull(compareBy<IndexedValue<ThresholdEntry>, Instant?>(nullsFirst()) { it.value.setAt }.thenBy { it.index })
            ?.value

    /** [history] kept to the lexicon's limit, dropping the oldest entries (undated first), in the order they took effect. */
    private fun capped(history: List<ThresholdEntry>): List<ThresholdEntry> =
        history.sortedWith(compareBy(nullsFirst()) { it.setAt }).takeLast(TrainingDetails.MAX_HISTORY)

    /** [training]'s value for [key]; null when unset, or when [key]'s sport has no override. */
    fun value(training: TrainingDetails, key: Key): Long? {
        if (key.sport == null) return ThresholdUpdates.value(training, key.threshold)
        val override = training.override(key.sport)
        return when {
            override == null -> null
            key.threshold == Threshold.THRESHOLD_SPEED -> override.thresholdSpeed
            else -> ThresholdUpdates.sportValue(override, key.threshold)
        }
    }

    private fun setAt(training: TrainingDetails, key: Key): Instant? {
        val times = (if (key.sport == null) training.setAt else training.override(key.sport)?.setAt) ?: return null
        return when (key.threshold) {
            Threshold.FUNCTIONAL_THRESHOLD_POWER -> times.functionalThresholdPower
            Threshold.MAX_HEART_RATE -> times.maxHeartRate
            Threshold.THRESHOLD_HEART_RATE -> times.thresholdHeartRate
            Threshold.RESTING_HEART_RATE -> times.restingHeartRate
            Threshold.THRESHOLD_SPEED -> times.thresholdSpeed
            else -> null
        }
    }

    /** [training] with [key] set to [value], taking effect at [setAt]; null clears them. A sport without an override is left alone. */
    private fun withValue(training: TrainingDetails, key: Key, value: Long?, setAt: Instant?): TrainingDetails {
        val sport = key.sport
        val override = training.override(sport)
        return when {
            sport == null ->
                ThresholdUpdates.withValue(training, key.threshold, value).copy(setAt = withTime(training.setAt, key.threshold, setAt))
            override == null -> training
            else -> {
                val updated =
                    if (key.threshold == Threshold.THRESHOLD_SPEED) override.copy(thresholdSpeed = value)
                    else ThresholdUpdates.withSportValue(override, key.threshold, value)
                training.withOverride(sport, updated.copy(setAt = withTime(override.setAt, key.threshold, setAt)))
            }
        }
    }

    private fun withTime(setAt: TrainingSetAt?, threshold: String, at: Instant?): TrainingSetAt? {
        val times = setAt ?: TrainingSetAt()
        val updated =
            when (threshold) {
                Threshold.FUNCTIONAL_THRESHOLD_POWER -> times.copy(functionalThresholdPower = at)
                Threshold.MAX_HEART_RATE -> times.copy(maxHeartRate = at)
                Threshold.THRESHOLD_HEART_RATE -> times.copy(thresholdHeartRate = at)
                Threshold.RESTING_HEART_RATE -> times.copy(restingHeartRate = at)
                Threshold.THRESHOLD_SPEED -> times.copy(thresholdSpeed = at)
                else -> times
            }
        return updated.takeUnless { it == TrainingSetAt() }
    }
}
