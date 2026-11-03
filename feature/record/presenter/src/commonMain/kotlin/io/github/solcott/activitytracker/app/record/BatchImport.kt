package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.activityfile.AccountExportActivity
import io.github.solcott.activitytracker.activityfile.BatchReader
import io.github.solcott.activitytracker.activityfile.EntryRef
import io.github.solcott.activitytracker.activityfile.ExportKind
import io.github.solcott.activitytracker.activityfile.ExportVisibility
import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.activityfile.ScanEvent
import io.github.solcott.activitytracker.activityfile.localZone
import io.github.solcott.activitytracker.activityfile.summary
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityMatch
import io.github.solcott.activitytracker.data.activity.DuplicateOf
import io.github.solcott.activitytracker.data.activity.OwnActivity
import io.github.solcott.activitytracker.data.activity.UploadSource
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.CombinedActivity
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

/** A file a batch read activities from: [ref] finds it again. */
internal class BatchFile(
    val ref: EntryRef,
    val name: String,
    /** Its size as found, gzipped or not: at most what its original keeps until uploaded. */
    val size: Long,
    val originalTooLarge: Boolean,
    /** Its times have no zone, so the athlete places it. */
    val zoneless: Boolean,
) {
    /** At most what its original keeps on this device until uploaded: none when it's too large to keep. */
    val originalBytes: Long
        get() = if (originalTooLarge) 0 else size
}

/**
 * One activity a batch read, kept as a [summary] until it's previewed or saved: its [file] (by index), its place in that file ([entry]),
 * what its account export says of it, and the detail categories it has data in, noted while its data was at hand.
 */
internal class BatchItem(
    val file: Int,
    val entry: Int,
    val summary: ImportedActivity,
    val export: AccountExportActivity?,
    val categories: Set<String>,
    val runningForm: Boolean,
)

/** What a batch's files held: its [files], its [items] oldest first, and what wasn't read. */
internal class ScannedBatch(
    val files: List<BatchFile>,
    val items: List<BatchItem>,
    val skipped: ImmutableList<SkippedFile>,
    val ignored: Int,
    /** Each account export the files came in, in the order first read. */
    val exports: List<BatchExport>,
)

/** Reads every activity file [reader]'s sources hold, keeping each activity's summary, reporting [onProgress] as it goes. */
internal suspend fun BatchReader.collect(onProgress: (ReadProgress) -> Unit): ScannedBatch {
    val files = mutableListOf<BatchFile>()
    val items = mutableListOf<BatchItem>()
    val skipped = mutableListOf<SkippedFile>()
    var ignored = 0
    val exports = linkedMapOf<ExportKind, Boolean>()
    scan().collect { event ->
        when (event) {
            is ScanEvent.Read -> {
                val file = files.size
                val zoneless = event.read.activities.any { it.localStart != null }
                files += BatchFile(event.ref, event.name, event.size, event.originalTooLarge, zoneless)
                event.export?.kind?.let { kind ->
                    val privacy = event.read.activities.indices.any { event.exportOf(it)?.visibility != null }
                    exports[kind] = exports[kind] == true || privacy
                }
                event.read.activities.forEachIndexed { entry, activity ->
                    val draft = ActivityDraft.fromImport(activity, activity.type, null)
                    items +=
                        BatchItem(
                            file,
                            entry,
                            activity.summary(),
                            event.exportOf(entry),
                            draft.heldCategories(),
                            draft.hasRunningForm,
                        )
                }
            }
            is ScanEvent.Skipped -> skipped += SkippedFile(event.name, event.reason)
            is ScanEvent.Progress -> {
                ignored = event.ignored
                onProgress(ReadProgress(event.files, event.activities))
            }
        }
    }
    return ScannedBatch(
        files,
        items.sortedBy { it.summary.startedAt },
        skipped.toImmutableList(),
        ignored,
        exports.map { (kind, privacy) -> BatchExport(kind, privacy) },
    )
}

/** The batch's items placed in their files' zones: [zoneOf] a file index, for a zone-less file. */
internal fun ScannedBatch.placed(zoneOf: (Int) -> TimeZone): List<ImportedActivity> = items.map { item ->
    if (files[item.file].zoneless) item.summary.inZone(zoneOf(item.file)) else item.summary
}

/** What the athlete changed of one activity in a batch, in its preview. */
internal data class ItemOverride(val title: String? = null, val type: ActivityType? = null)

/**
 * Which of a batch's activities import, what to do with each duplicate, and the athlete's changes to each. One that isn't a duplicate
 * imports unless the athlete cleared it ([deselected]); a duplicate unless the athlete picked [DuplicateAction.Ignore] for it ([actions]).
 * A duplicate's action starts as Ignore for an exact copy; in a [batch], Import anyway for one of another sport, so a chain of defaults
 * never merges a day's different activities; else Combine, or Ignore while what it repeats is still uploading.
 */
internal data class BatchSelection(
    val deselected: Set<Int> = emptySet(),
    val actions: Map<Int, DuplicateAction> = emptyMap(),
    val overrides: Map<Int, ItemOverride> = emptyMap(),
    val batch: Boolean = false,
) {
    fun action(index: Int, match: ActivityMatch?): DuplicateAction =
        actions[index]?.takeIf { it != DuplicateAction.Combine || canCombine(match) } ?: defaultAction(match)

    private fun defaultAction(match: ActivityMatch?): DuplicateAction =
        when {
            match?.exactCopy == true -> DuplicateAction.Ignore
            batch && match?.sameSport == false -> DuplicateAction.ImportAnyway
            canCombine(match) -> DuplicateAction.Combine
            else -> DuplicateAction.Ignore
        }

    fun isSelected(index: Int, match: ActivityMatch?): Boolean =
        if (match == null) index !in deselected else action(index, match) != DuplicateAction.Ignore

    /** This selection without [indices] (each with its match from [matchOf]): an activity saved already leaves it. */
    fun without(indices: Collection<Int>, matchOf: (Int) -> ActivityMatch?): BatchSelection =
        indices.fold(this) { selection, index ->
            if (matchOf(index) == null) selection.copy(deselected = selection.deselected + index)
            else selection.withAction(index, DuplicateAction.Ignore)
        }

    /** Ticking a duplicate combines it, or imports it anyway when it can't be combined yet; unticking ignores it. */
    fun toggled(index: Int, match: ActivityMatch?): BatchSelection =
        when {
            match != null -> withAction(index, if (isSelected(index, match)) DuplicateAction.Ignore else selectedAction(match))
            index in deselected -> copy(deselected = deselected - index)
            else -> copy(deselected = deselected + index)
        }

    /**
     * Selects or clears [shown] (indices), each with its match from [matchOf]. Duplicates change only when [duplicates], the list showing
     * only them: otherwise Select all and Deselect all leave them as the athlete set them.
     */
    fun all(selected: Boolean, shown: List<Int>, matchOf: (Int) -> ActivityMatch?, duplicates: Boolean): BatchSelection {
        var next = this
        for (index in shown) {
            val match = matchOf(index)
            next =
                when {
                    match == null -> next.copy(deselected = if (selected) next.deselected - index else next.deselected + index)
                    !duplicates -> next
                    else -> next.withAction(index, if (selected) selectedAction(match) else DuplicateAction.Ignore)
                }
        }
        return next
    }

    fun withAction(index: Int, action: DuplicateAction): BatchSelection = copy(actions = actions + (index to action))

    fun withOverride(index: Int, override: ItemOverride): BatchSelection = copy(overrides = overrides + (index to override))

    /**
     * Sets [action] for each of [shown] that's a duplicate (its match from [matchOf]); Combine imports anyway one that can't combine yet.
     */
    fun withShownAction(action: DuplicateAction, shown: List<Int>, matchOf: (Int) -> ActivityMatch?): BatchSelection =
        shown.fold(this) { next, index ->
            val match = matchOf(index) ?: return@fold next
            next.withAction(index, if (action == DuplicateAction.Combine && !canCombine(match)) DuplicateAction.ImportAnyway else action)
        }

    /** What ticking a duplicate chooses: Combine where it can, else Import anyway, and Import anyway for an exact copy. */
    private fun selectedAction(match: ActivityMatch) =
        if (canCombine(match) && !match.exactCopy) DuplicateAction.Combine else DuplicateAction.ImportAnyway
}

/** [item]'s title and type as it saves: the athlete's change, else its export's, else its file's. */
internal fun BatchItem.title(override: ItemOverride?): String? = override?.title ?: export?.title ?: summary.title

internal fun BatchItem.type(override: ItemOverride?): ActivityType = override?.type ?: export?.type ?: summary.type

/** The visibility [item]'s account export narrows it to, below the batch's [batch] visibility; null when it doesn't. */
internal fun BatchItem.narrowedTo(batch: String): String? {
    val exported =
        when (export?.visibility) {
            ExportVisibility.OnlyMe -> Visibility.SELF
            ExportVisibility.Followers -> Visibility.FOLLOWERS
            ExportVisibility.Everyone,
            null -> return null
        }
    return exported.takeIf { Visibility.narrower(it, batch) == it && it != Visibility.known(batch) }
}

/** The batch's rows, oldest first, as the athlete sees them: [placed] in their zones, each with its match and the [selection]. */
@Suppress("LongParameterList") // The rows are everything the screen knows about the batch.
internal fun batchRows(
    batch: ScannedBatch,
    placed: List<ImportedActivity>,
    matches: List<ActivityMatch?>,
    selection: BatchSelection,
    visibility: String,
    zoneOf: (Int) -> TimeZone,
): ImmutableList<ImportRow> =
    batch.items
        .mapIndexed { index, item ->
            val activity = placed[index]
            val override = selection.overrides[index]
            val type = item.type(override)
            val local = activity.startedAt.toLocalDateTime(activity.localZone(zoneOf(item.file)))
            val match = matches.getOrNull(index)
            ImportRow(
                index = index,
                title = item.title(override),
                defaultTitle = DefaultTitle(partOfDay(local.hour), type),
                type = type,
                start = local.format(LOCAL_START),
                month = LocalDate(local.year, local.month, 1),
                elapsed = activity.elapsed,
                distanceMeters = activity.distanceMeters,
                dive = activity.dive?.let { DiveRowExtras(it.header.maxDepthMeters, it.header.diveNumber) },
                device = activity.device,
                fileName = batch.files[item.file].name,
                selected = selection.isSelected(index, match),
                duplicate = duplicateInfo(match, selection.action(index, match), zoneOf(item.file)),
                narrowedTo = item.narrowedTo(visibility),
            )
        }
        .toImmutableList()

/**
 * The batch's selected activities to save: groups to combine, each with a logged activity or among the batch's copies of one activity, and
 * the rest on their own, oldest first.
 */
internal class BatchPlan(val alone: List<Int>, val groups: List<BatchGroup>)

/** Batch items (by index) to combine with the logged activity [into], or, without one, with each other: the first is the earliest copy. */
internal class BatchGroup(val into: OwnActivity?, val items: List<Int>)

/** Plans saving the [selection] of a batch of [count] activities, oldest first, with their [matches]. */
internal fun batchPlan(count: Int, matches: List<ActivityMatch?>, selection: BatchSelection): BatchPlan {
    val roots = linkedMapOf<CombineRoot, MutableList<Int>>()
    for (index in 0 until count) {
        if (selection.isSelected(index, matches.getOrNull(index)))
            roots.getOrPut(rootOf(index, matches, selection)) { mutableListOf() } += index
    }
    val alone = mutableListOf<Int>()
    val groups = mutableListOf<BatchGroup>()
    for ((root, group) in roots) {
        when {
            root is CombineRoot.Logged -> group.chunked(CombinedActivity.MAX_MEMBERS - 1).forEach { groups += BatchGroup(root.into, it) }
            group.size == 1 -> alone += group
            else ->
                group.chunked(CombinedActivity.MAX_MEMBERS).forEach { if (it.size == 1) alone += it else groups += BatchGroup(null, it) }
        }
    }
    return BatchPlan(alone.sorted(), groups)
}

/**
 * What a selected activity is saved with: a logged activity it's combined with, or the earliest copy in the batch of the activity it is.
 */
private sealed interface CombineRoot {
    data class Logged(val into: OwnActivity) : CombineRoot

    data class InBatch(val index: Int) : CombineRoot
}

/**
 * Where a chain of Combine choices from [index] leads: to the logged activity it repeats, or back through earlier copies in the batch to
 * the first one not combined further. One that isn't combined is its own root. A copy the athlete left out stands for what it repeats
 * ([through]), so nothing is combined under an activity that isn't saved.
 */
private tailrec fun rootOf(index: Int, matches: List<ActivityMatch?>, selection: BatchSelection, through: Boolean = false): CombineRoot {
    val match = matches.getOrNull(index)
    return when (val of = match?.of.takeIf { through || selection.action(index, match) == DuplicateAction.Combine }) {
        is DuplicateOf.InBatch ->
            rootOf(of.index, matches, selection, through = !selection.isSelected(of.index, matches.getOrNull(of.index)))
        is DuplicateOf.Existing -> combineTarget(match, DuplicateAction.Combine)?.let(CombineRoot::Logged) ?: CombineRoot.InBatch(index)
        null -> CombineRoot.InBatch(index)
    }
}

/**
 * What the athlete chose for the whole batch: its [visibility] and [gear], and the zone each zone-less file's clock is in ([zoneOf], by
 * file), with their [training] for thresholds.
 */
internal class BatchChoices(
    val training: TrainingDetails?,
    val visibility: VisibilityChoice,
    val gear: List<AtUri>,
    val zoneOf: (Int) -> TimeZone,
    /** The save's [ActivityDraft.importBatch], new for each save and shared by every activity it publishes (feed/batch-import). */
    val importBatch: String = Tid.next(),
)

/** What the upload queue names a batch's import by (feed/batch-import): the exports recognized, else the files. */
internal val ScannedBatch.uploadSource: UploadSource
    get() =
        if (exports.isNotEmpty()) UploadSource.Export(exports.map { it.kind.name })
        else UploadSource.Files(files.firstOrNull()?.name.orEmpty(), files.size)
