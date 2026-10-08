package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.training.ThresholdChange
import io.github.solcott.activitytracker.data.training.ThresholdUpdates
import io.github.solcott.activitytracker.data.training.ZoneSnapshots
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails

/**
 * [activity] ready to publish, with its zones as the recording device had the thresholds, where the athlete lets imports update them
 * ([thresholdChanges]).
 */
internal fun importDraft(
    activity: ImportedActivity,
    type: ActivityType,
    title: String?,
    training: TrainingDetails?,
    thresholdChanges: List<ThresholdChange>,
): ActivityDraft {
    val zonesFrom = training?.let { ThresholdUpdates.apply(it, thresholdChanges, activity.startedAt) }
    return ActivityDraft.fromImport(activity, type, title)
        .copy(
            heartRateZones = ZoneSnapshots.heartRate(zonesFrom, type.sport),
            powerZones = ZoneSnapshots.power(zonesFrom, type.sport),
        )
}
