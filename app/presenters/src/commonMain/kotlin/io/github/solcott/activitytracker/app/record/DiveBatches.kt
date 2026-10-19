package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.activityfile.SourceFile
import io.github.solcott.activitytracker.activityfile.localZone
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityMatch
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.DraftSource
import io.github.solcott.activitytracker.data.activity.DuplicateOf
import io.github.solcott.activitytracker.data.activity.OwnActivity
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.CombinedActivity
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

/**
 * Which of a file's activities import. One that isn't a duplicate imports unless the athlete cleared it ([deselected]); a duplicate unless
 * the athlete picked [DuplicateAction.Ignore] for it ([actions]). A duplicate's action starts as Combine, or Ignore while what it repeats
 * is still uploading.
 */
internal data class DiveSelection(val deselected: Set<Int> = emptySet(), val actions: Map<Int, DuplicateAction> = emptyMap()) {
    fun action(index: Int, match: ActivityMatch?): DuplicateAction =
        actions[index]?.takeIf { it != DuplicateAction.Combine || canCombine(match) }
            ?: if (canCombine(match)) DuplicateAction.Combine else DuplicateAction.Ignore

    fun isSelected(index: Int, match: ActivityMatch?): Boolean =
        if (match == null) index !in deselected else action(index, match) != DuplicateAction.Ignore

    /** Ticking a duplicate combines it, or imports it anyway when it can't be combined yet; unticking ignores it. */
    fun toggled(index: Int, match: ActivityMatch?): DiveSelection =
        when {
            match != null ->
                withAction(
                    index,
                    when {
                        isSelected(index, match) -> DuplicateAction.Ignore
                        canCombine(match) -> DuplicateAction.Combine
                        else -> DuplicateAction.ImportAnyway
                    },
                )
            index in deselected -> copy(deselected = deselected - index)
            else -> copy(deselected = deselected + index)
        }

    /** Every activity that isn't a duplicate, of [count]; duplicates keep their actions. */
    fun all(selected: Boolean, count: Int): DiveSelection = copy(deselected = if (selected) emptySet() else (0 until count).toSet())

    fun withAction(index: Int, action: DuplicateAction): DiveSelection = copy(actions = actions + (index to action))
}

/** The file's only activity ([placed] in [zone]), and whether it's already in the athlete's log. */
internal fun singlePreview(
    placed: Pair<String, ImportedActivity>?,
    zone: TimeZone,
    zones: ImmutableList<TimeZoneOption>?,
    matches: List<ActivityMatch?>,
    selection: DiveSelection,
): ImportPreview? {
    val (name, activity) = placed ?: return null
    val match = matches.firstOrNull()
    // An existing activity's start reads in the file's own zone, as the file's does.
    val duplicate = duplicateInfo(match, selection.action(0, match), activity.localZone(zone))
    return activity.toPreview(name, zone, zones).copy(duplicate = duplicate)
}

/** A logbook's [dives], placed in [zone], as the athlete picks from them; null until a logbook ([fileName]) is read. */
@Suppress("LongParameterList") // The batch is everything the screen knows about the file.
internal fun diveBatch(
    fileName: String?,
    dives: List<ImportedActivity>?,
    selection: DiveSelection,
    matches: List<ActivityMatch?>,
    zone: TimeZone,
    zones: ImmutableList<TimeZoneOption>?,
): DiveBatch? {
    if (fileName == null || dives == null) return null
    return DiveBatch(
        fileName = fileName,
        dives =
            dives
                .mapIndexed { index, dive ->
                    val start = dive.startedAt.toLocalDateTime(zone)
                    val match = matches.getOrNull(index)
                    DiveRow(
                        index = index,
                        title = dive.title,
                        defaultTitle = DefaultTitle(partOfDay(start.hour), dive.type),
                        start = start.format(LOCAL_START),
                        elapsed = dive.elapsed,
                        maxDepthMeters = dive.dive?.maxDepthMeters,
                        diveNumber = dive.dive?.diveNumber,
                        device = dive.device,
                        selected = selection.isSelected(index, match),
                        duplicate = duplicateInfo(match, selection.action(index, match), zone),
                    )
                }
                .toImmutableList(),
        clock = dives.firstNotNullOfOrNull { it.localStart }?.let { importClock(it, zone, zones) },
    )
}

internal fun importClock(start: LocalDateTime, zone: TimeZone, zones: ImmutableList<TimeZoneOption>?) =
    ImportClock(start.format(LOCAL_START), zones?.find { it.id == zone.id } ?: zoneOption(zone, start), zones ?: persistentListOf())

/**
 * A logbook's selected dives: those saved on their own ([drafts]), and those combined ([combines]), each group either with a logged
 * activity or among the file's copies of one dive.
 */
internal class DiveImport(val drafts: List<ActivityDraft>, val combines: List<CombineGroup>)

/** Drafts to combine with the logged activity [into], or, without one, with each other: the first is the file's earliest copy. */
internal class CombineGroup(val into: OwnActivity?, val drafts: List<ActivityDraft>)

/**
 * The selected [dives] ready to save, oldest first, each with its own type and its site as the title, else its [defaultTitles] entry, and
 * the logbook ([source]) naming its entry. Dives carry no thresholds to update.
 */
@Suppress("LongParameterList") // Each is a part of the draft.
internal fun diveImport(
    dives: List<ImportedActivity>?,
    selection: DiveSelection,
    matches: List<ActivityMatch?>,
    defaultTitles: Map<Int, String>,
    training: TrainingDetails?,
    visibility: VisibilityChoice,
    source: SourceFile?,
): DiveImport {
    val roots = linkedMapOf<CombineRoot, MutableList<Pair<Int, ActivityDraft>>>()
    for ((index, dive) in dives.orEmpty().withIndex().sortedBy { it.value.startedAt }) {
        val match = matches.getOrNull(index)
        if (!selection.isSelected(index, match)) continue
        val title = dive.title ?: defaultTitles[index]?.ifBlank { null }
        // A logbook's notes are personal and the batch never shows them, so none are published unseen.
        val draft =
            importDraft(dive, dive.type, title, training, emptyList(), source?.let { DraftSource(it, entry = index) })
                .copy(description = null)
                .withVisibility(visibility)
        roots.getOrPut(rootOf(index, matches, selection)) { mutableListOf() } += index to draft
    }
    val drafts = mutableListOf<ActivityDraft>()
    val combines = mutableListOf<CombineGroup>()
    for ((root, group) in roots) {
        // The file's earliest copy first: the combined dive starts from its title.
        val ordered = group.sortedBy { it.first }.map { it.second }
        when {
            root is CombineRoot.Logged ->
                ordered.chunked(CombinedActivity.MAX_MEMBERS - 1).forEach { combines += CombineGroup(root.into, it) }
            ordered.size == 1 -> drafts += ordered
            else ->
                ordered.chunked(CombinedActivity.MAX_MEMBERS).forEach {
                    if (it.size == 1) drafts += it else combines += CombineGroup(null, it)
                }
        }
    }
    return DiveImport(drafts, combines)
}

/** What a selected dive is saved with: a logged activity it's combined with, or the earliest copy in the file of the dive it is. */
private sealed interface CombineRoot {
    data class Logged(val into: OwnActivity) : CombineRoot

    data class InFile(val index: Int) : CombineRoot
}

/**
 * Where a chain of Combine choices from [index] leads: to the logged activity a dive repeats, or back through earlier copies in the file to
 * the first one not combined further. A dive that isn't combined is its own root.
 */
private tailrec fun rootOf(index: Int, matches: List<ActivityMatch?>, selection: DiveSelection): CombineRoot {
    val match = matches.getOrNull(index)
    return when (val of = match?.of.takeIf { selection.action(index, match) == DuplicateAction.Combine }) {
        is DuplicateOf.InFile -> rootOf(of.index, matches, selection)
        is DuplicateOf.Existing -> combineTarget(match, DuplicateAction.Combine)?.let(CombineRoot::Logged) ?: CombineRoot.InFile(index)
        null -> CombineRoot.InFile(index)
    }
}

/** The file's only activity, by its name; null for a logbook of several. */
internal fun singleActivity(file: Pair<String, List<ImportedActivity>>?): Pair<String, ImportedActivity>? = file?.let { (name, read) ->
    read.singleOrNull()?.let { name to it }
}

/**
 * Saves a logbook's selected dives (those on their own in one batch, then each combined group), then [onPublished]; nothing when none are
 * selected.
 */
internal suspend fun saveDives(activities: ActivityRepository, manager: ActivityManager, import: DiveImport, onPublished: () -> Unit) {
    if (import.drafts.isEmpty() && import.combines.isEmpty()) return
    if (import.drafts.isNotEmpty()) activities.publishAll(import.drafts)
    for (group in import.combines) {
        if (group.into != null) manager.combine(group.into, group.drafts) else manager.combine(group.drafts)
    }
    onPublished()
}

/** Publishes a single file's activity, or combines it with the logged activity [into]; returns the combined activity's URI then. */
internal suspend fun saveSingle(
    activities: ActivityRepository,
    manager: ActivityManager,
    draft: ActivityDraft,
    into: OwnActivity?,
): AtUri? =
    if (into == null) {
        activities.publish(draft)
        null
    } else {
        manager.combine(into, draft)
    }
