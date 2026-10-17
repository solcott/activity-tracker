package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.activityfile.ImportedFile
import io.github.solcott.activitytracker.activityfile.SourceFile
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.DraftSource
import io.github.solcott.activitytracker.data.training.ThresholdChange
import io.github.solcott.activitytracker.data.training.ThresholdUpdates
import io.github.solcott.activitytracker.data.training.ZoneSnapshots
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails

/**
 * [activity] ready to publish, with its zones as the recording device had the thresholds, where the athlete lets imports update them
 * ([thresholdChanges]), and the file it was read from ([source]) to keep.
 */
@Suppress("LongParameterList") // Each is a part of the draft.
internal fun importDraft(
    activity: ImportedActivity,
    type: ActivityType,
    title: String?,
    training: TrainingDetails?,
    thresholdChanges: List<ThresholdChange>,
    source: DraftSource?,
): ActivityDraft {
    val zonesFrom = training?.let { ThresholdUpdates.apply(it, thresholdChanges, activity.startedAt) }
    return ActivityDraft.fromImport(activity, type, title)
        .copy(
            heartRateZones = ZoneSnapshots.heartRate(zonesFrom, type.sport),
            powerZones = ZoneSnapshots.power(zonesFrom, type.sport),
            source = source,
        )
}

/** Reads the picked file [name] and prepares it to be kept with what's saved from it; null for one too large to keep. */
internal suspend fun ActivityFileReader.readKeeping(name: String, bytes: ByteArray): Pair<ImportedFile, SourceFile?> {
    val read = readFile(bytes)
    return read to SourceFile.prepare(bytes, name, read)
}

/** This file as a single activity's source; a logbook of one dive still names its entry. */
internal fun SourceFile.single(): DraftSource = DraftSource(this, entry = if (format.isLogbook) 0 else null)
