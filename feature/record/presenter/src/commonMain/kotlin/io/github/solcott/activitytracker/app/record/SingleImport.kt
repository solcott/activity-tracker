package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.activityfile.localZone
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityMatch
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.OwnActivity
import io.github.solcott.activitytracker.lexicon.AtUri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format

/** The file's only activity ([placed] in [zone]), and whether it's already in the athlete's log. */
internal fun singlePreview(
    placed: Pair<String, ImportedActivity>?,
    zone: TimeZone,
    zones: ImmutableList<TimeZoneOption>?,
    matches: List<ActivityMatch?>,
    selection: BatchSelection,
): ImportPreview? {
    val (name, activity) = placed ?: return null
    val match = matches.firstOrNull()
    // An existing activity's start reads in the file's own zone, as the file's does.
    val duplicate = duplicateInfo(match, selection.action(0, match), activity.localZone(zone))
    return activity.toPreview(name, zone, zones).copy(duplicate = duplicate)
}

internal fun importClock(start: LocalDateTime, zone: TimeZone, zones: ImmutableList<TimeZoneOption>?) =
    ImportClock(start.format(LOCAL_START), zones?.find { it.id == zone.id } ?: zoneOption(zone, start), zones ?: persistentListOf())

/** The file's only activity, by its name; null for a file of several. */
internal fun singleActivity(file: Pair<String, List<ImportedActivity>>?): Pair<String, ImportedActivity>? = file?.let { (name, read) ->
    read.singleOrNull()?.let { name to it }
}

/** What saving a single file did: published it as [uri], or [combined] it into an activity at [uri]. */
internal data class SavedSingle(val uri: AtUri, val combined: Boolean)

/** Publishes a single file's activity, or combines it with the logged activity [into]. */
internal suspend fun saveSingle(
    activities: ActivityRepository,
    manager: ActivityManager,
    draft: ActivityDraft,
    into: OwnActivity?,
): SavedSingle =
    if (into == null) SavedSingle(activities.publish(draft), combined = false)
    else SavedSingle(manager.combine(into, draft), combined = true)

/** A zone choice for each of [batch]'s zone-less files, from its first activity's clock. */
internal fun batchClocks(batch: ScannedBatch, placed: List<ImportedActivity>, zoneOf: (Int) -> TimeZone): ImmutableList<FileClock> =
    batch.files.indices
        .filter { batch.files[it].zoneless }
        .mapNotNull { file ->
            val first = batch.items.withIndex().firstOrNull { it.value.file == file } ?: return@mapNotNull null
            val local = placed.getOrNull(first.index)?.localStart ?: first.value.summary.localStart ?: return@mapNotNull null
            FileClock(file, batch.files[file].name, importClock(local, zoneOf(file), zoneOptions(local)))
        }
        .toImmutableList()
