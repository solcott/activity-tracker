package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.activityfile.ImportedFile
import io.github.solcott.activitytracker.activityfile.SourceFile
import io.github.solcott.activitytracker.app.edit.DetailsForm
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.DraftSource
import io.github.solcott.activitytracker.data.training.ThresholdChange
import io.github.solcott.activitytracker.data.training.ThresholdUpdates
import io.github.solcott.activitytracker.data.training.ZoneSnapshots
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet

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

/** This draft with what the athlete wrote in [form]: its description and photos, and for a dive ([isDive]) its site. */
internal fun ActivityDraft.withDetails(form: DetailsForm, isDive: Boolean): ActivityDraft =
    withDetails(
        description = form.description.text.toString(),
        diveSite = form.diveSite.text.toString().takeIf { isDive },
        photos = form.newPhotos(),
    )

/** The detail categories an import has data in ([ActivityDraft.heldCategories]), and whether its cadence comes with running form. */
internal data class ImportCategories(val categories: ImmutableSet<String>, val runningForm: Boolean)

/**
 * What an import offers is what it will publish: the data of [single] as [type], or else of each of a logbook's [dives] that is [selected]
 * (by index).
 */
internal fun importCategories(
    single: ImportedActivity?,
    type: ActivityType,
    dives: List<ImportedActivity>?,
    selected: (Int) -> Boolean,
): ImportCategories {
    val drafts =
        single?.let { listOf(ActivityDraft.fromImport(it, type, null)) }
            ?: dives.orEmpty().filterIndexed { i, _ -> selected(i) }.map { ActivityDraft.fromImport(it, it.type, null) }
    return ImportCategories(drafts.flatMap { it.heldCategories() }.toImmutableSet(), drafts.any { it.hasRunningForm })
}
