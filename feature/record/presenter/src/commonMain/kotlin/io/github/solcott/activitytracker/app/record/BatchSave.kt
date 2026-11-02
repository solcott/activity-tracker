package io.github.solcott.activitytracker.app.record

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.activityfile.ActivityFileException
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.BatchReader
import io.github.solcott.activitytracker.activityfile.EntryRef
import io.github.solcott.activitytracker.activityfile.FoundFile
import io.github.solcott.activitytracker.activityfile.ImportSource
import io.github.solcott.activitytracker.activityfile.ImportedFile
import io.github.solcott.activitytracker.activityfile.SourceFile
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.BulkWrites
import io.github.solcott.activitytracker.data.activity.DraftSource
import io.github.solcott.activitytracker.data.activity.UploadSource
import io.github.solcott.activitytracker.data.db.ImportSaveEntity
import io.github.solcott.activitytracker.data.db.ImportSaveItemEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.importsave.ImportSaveRunner
import io.github.solcott.activitytracker.data.importsave.ImportSaveStore
import io.github.solcott.activitytracker.data.importsave.ImportSaves
import io.github.solcott.activitytracker.data.importsave.NewImportSave
import io.github.solcott.activitytracker.data.importsave.PlannedActivity
import io.github.solcott.activitytracker.data.importsave.PlannedChoices
import io.github.solcott.activitytracker.data.importsave.PlannedInto
import io.github.solcott.activitytracker.data.importsave.SaveSource
import io.github.solcott.activitytracker.data.importsave.decodePath
import io.github.solcott.activitytracker.data.importsave.encodePath
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.TimeZone

/**
 * The persisted save of [scan]'s selected activities (feed/batch-import), as the athlete reviewed them: each resolved to its title, type,
 * visibility, description and zone, in [plan]'s order (combine groups first, then the rest oldest first), under a record key chosen now so
 * resuming rewrites the same records. [sources] are the picked files, which the save keeps where the platform can. Saving again after a
 * failure passes the save's [pending] items: an activity among them keeps its record key, and a group its combined one, since the failed
 * run may have queued them already.
 */
@Suppress("LongParameterList") // What the screen reviewed.
internal fun newBatchSave(
    scan: ScannedBatch,
    sources: List<ImportSource>,
    plan: BatchPlan,
    selection: BatchSelection,
    defaultTitles: Map<Int, String>,
    choices: BatchChoices,
    source: UploadSource,
    /** Where the items start: after a save's earlier items, when saving again after a failure. */
    firstPosition: Int = 0,
    pending: List<ImportSaveItemEntity> = emptyList(),
): NewImportSave {
    val keptRkeys = pending.associate { it.batchIndex to it.rkey }
    val keptCombined = pending.associate { it.batchIndex to PlannedActivity.decode(it.planJson).combinedRkey }
    val combinedTaken = mutableSetOf<String>()
    val items = mutableListOf<ImportSaveItemEntity>()
    fun add(index: Int, groupKey: Int?, into: PlannedInto?, combinedRkey: String?) {
        val item = scan.items[index]
        val file = scan.files[item.file]
        val override = selection.overrides[index]
        val visibility = item.narrowedTo(choices.visibility.activity)?.let(choices.visibility::withActivity) ?: choices.visibility
        val planned =
            PlannedActivity(
                title = item.title(override) ?: defaultTitles[index]?.ifBlank { null },
                type = item.type(override),
                visibility = visibility.activity,
                details = visibility.details,
                description = item.export?.description,
                zone = if (file.zoneless) choices.zoneOf(item.file).id else null,
                originalTooLarge = file.originalTooLarge,
                startedAt = item.summary.startedAt.toEpochMilliseconds(),
                into = into,
                combinedRkey = combinedRkey,
            )
        items +=
            ImportSaveItemEntity(
                batchId = choices.importBatch,
                position = firstPosition + items.size,
                batchIndex = index,
                sourceIdx = file.ref.source,
                pathJson = encodePath(file.ref.path),
                entry = item.entry,
                rkey = keptRkeys[index] ?: Tid.next(),
                planJson = planned.encode(),
                groupKey = groupKey,
            )
    }
    plan.groups.forEachIndexed { key, group ->
        val combinedRkey = group.items.firstNotNullOfOrNull { keptCombined[it]?.takeIf(combinedTaken::add) } ?: Tid.next()
        val into = group.into?.let(PlannedInto::of)
        group.items.forEach { add(it, key, into, combinedRkey) }
    }
    plan.alone.forEach { add(it, null, null, null) }
    return NewImportSave(
        batchId = choices.importBatch,
        source = source,
        sources = sources,
        items = items,
        choicesJson = PlannedChoices(choices.training, choices.gear).encode(),
    )
}

/** Plans saving [selection] of [scan] with its [matches] and writes it, then starts it running where the platform keeps it alive. */
@Suppress("LongParameterList") // What the screen reviewed.
internal suspend fun startBatchSave(
    store: ImportSaveStore,
    saves: ImportSaves,
    runner: ImportSaveRunner,
    clients: SessionClients,
    save: NewImportSave,
) {
    store.start(save)
    saves.launch(clients.did) { runner.runAll() }
}

/**
 * Works through the signed-in athlete's persisted batch saves (feed/batch-import), whichever session graph runs it: the screen's, or a
 * background job's after the app was frozen or killed. Each activity is read again from its file, drafted as planned and queued under its
 * planned record key, then marked saved, so a run stopped anywhere resumes where it was and never queues an activity under a second key.
 */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
@Inject
class BatchSaveRunner(
    private val store: ImportSaveStore,
    private val saves: ImportSaves,
    private val clients: SessionClients,
    private val activities: ActivityRepository,
    private val manager: ActivityManager,
    private val files: ActivityFileReader,
) : ImportSaveRunner {
    override suspend fun runAll() {
        saves.exclusive(clients.did) {
            // A failed run leaves its save for the next launch: the screen offers saving again, a background job retries.
            var next = store.runnable().firstOrNull()
            while (next != null && run(next)) next = store.runnable().firstOrNull()
        }
    }

    /** Runs [save] to its end; false when it failed (it stays to run again). */
    private suspend fun run(save: ImportSaveEntity): Boolean {
        saves.started(save.batchId)
        var failure: Throwable? = null
        try {
            // Every write the save queues, combines included, waits behind the athlete's own (data/outbox).
            BulkWrites.run { runSave(save) }
        } catch (_: SaveCancelled) {
            store.end(save.batchId)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Any failure queueing stops the run where it was; what's saved stays saved.
            failure = e
        } finally {
            saves.ended(save.batchId, failure)
        }
        return failure == null
    }

    private suspend fun runSave(save: ImportSaveEntity) {
        val batchId = save.batchId
        val choices = PlannedChoices.decode(save.choicesJson)
        val sources = store.open(batchId)
        try {
            val pending = store.pending(batchId)
            val (reachable, unreachable) = pending.partition { sources.getOrNull(it.sourceIdx) is SaveSource.Open }
            // A file kept but gone (deleted, access revoked) is skipped, as a file that vanished before saving is; one never kept stops the
            // save, since the file may still be there to pick again.
            skip(batchId, unreachable.filter { sources.getOrNull(it.sourceIdx) !is SaveSource.NotKept })
            val drafting =
                PlanDrafting(BatchReader(sources.map { (it as? SaveSource.Open)?.source ?: Unreachable }, files), files, choices, batchId)
            for (group in reachable.filter { it.groupKey != null }.groupBy { it.groupKey }.values) {
                if (store.isCancelled(batchId)) throw SaveCancelled()
                saveGroup(batchId, drafting, group)
            }
            val alone = reachable.filter { it.groupKey == null }
            if (alone.isNotEmpty()) saveAlone(batchId, drafting, alone)
            store.counted(batchId)
            if (unreachable.any { sources.getOrNull(it.sourceIdx) is SaveSource.NotKept }) store.stop(batchId)
        } finally {
            sources.forEach { (it as? SaveSource.Open)?.close?.invoke() }
        }
    }

    /**
     * Saves a combine group: with the logged activity it repeats, or the batch's copies of one activity with each other. Members share
     * their combined activity's visibility, so none may join one wider than its own: copies within the batch combine at their narrowest,
     * and a copy narrower than the logged activity it repeats is saved on its own instead.
     */
    private suspend fun saveGroup(batchId: String, drafting: PlanDrafting, group: List<ImportSaveItemEntity>) {
        val read = drafting.draftsOf(group)
        val drafts = read.mapNotNull { it.second }
        skip(batchId, read.filter { it.second == null }.map { it.first })
        val plan = PlannedActivity.decode(group.first().planJson)
        val into = plan.into?.own()
        if (into != null) {
            val (joining, alone) = drafts.partition { Visibility.narrower(it.visibility, into.visibility) == into.visibility }
            if (joining.isNotEmpty()) manager.combine(into, joining, rkey = plan.combinedRkey)
            if (alone.isNotEmpty()) activities.publishAll(alone)
        } else if (drafts.size > 1) {
            val narrowest = drafts.map { it.visibility }.reduce(Visibility::narrower)
            manager.combine(drafts.map { if (it.visibility == narrowest) it else it.atVisibility(narrowest) }, rkey = plan.combinedRkey)
        } else if (drafts.size == 1) {
            activities.publishAll(drafts)
        }
        store.markDone(batchId, read.filter { it.second != null }.map { it.first.position })
        store.counted(batchId)
    }

    /** Saves the rest, read again in one pass over their files and queued one at a time, each marked saved once queued. */
    private suspend fun saveAlone(batchId: String, drafting: PlanDrafting, items: List<ImportSaveItemEntity>) {
        val byRkey = items.associateBy { it.rkey }
        val bulk = flow {
            drafting.each(items) { item, draft ->
                if (draft == null) skip(batchId, listOf(item))
                else {
                    if (store.isCancelled(batchId)) throw SaveCancelled()
                    emit(draft)
                }
            }
        }
        activities.publishBatch(bulk, drain = false) { _, uri -> store.markDone(batchId, listOfNotNull(byRkey[uri.rkey]?.position)) }
    }

    /** Marks [items] skipped, each listed in the upload queue as a file that couldn't be read again. */
    private suspend fun skip(batchId: String, items: List<ImportSaveItemEntity>) {
        if (items.isEmpty()) return
        items.forEach { item ->
            val plan = PlannedActivity.decode(item.planJson)
            activities.skippedImport(batchId, plan.title, plan.type.sport, plan.start)
        }
        store.markSkipped(batchId, items.map { it.position })
    }

    /**
     * The athlete cancelled the rest of the save: the run stops before its next activity. A [CancellationException], so reading a file
     * passes it on rather than taking it for that file's failure; caught by [run], it never cancels the job.
     */
    private class SaveCancelled : CancellationException("The save was cancelled")

    /** A picked file the save can't open, so its activities fail to read. */
    private object Unreachable : ImportSource {
        override val name = ""
        override val size = 0L

        override suspend fun read(offset: Long, length: Int): ByteArray = throw ActivityFileException("The file can't be opened")
    }
}

/**
 * How a persisted save's activities are drafted: each read again from its file, as planned, under its planned record key. Each file's
 * original is kept until its last pending activity is drafted, so a file several share is stored once; only one file is held at a time.
 */
internal class PlanDrafting(
    private val reader: BatchReader,
    private val files: ActivityFileReader,
    private val choices: PlannedChoices,
    private val batchId: String,
) {
    private val originals = mutableMapOf<EntryRef, SourceFile?>()
    private val remaining = mutableMapOf<EntryRef, Int>()

    private val ImportSaveItemEntity.ref: EntryRef
        get() = EntryRef(sourceIdx, decodePath(pathJson))

    /** [items]' drafts, in order; null for one whose file can't be read again. */
    suspend fun draftsOf(items: List<ImportSaveItemEntity>): List<Pair<ImportSaveItemEntity, ActivityDraft?>> {
        val drafts = mutableMapOf<Int, ActivityDraft?>()
        each(items) { item, draft -> drafts[item.position] = draft }
        return items.map { it to drafts[it.position] }
    }

    /**
     * Drafts [items] in one pass over their files, so a nested archive is inflated once, handing each to [onDraft] as its file is read;
     * null for one whose file can't be read again.
     */
    suspend fun each(items: List<ImportSaveItemEntity>, onDraft: suspend (ImportSaveItemEntity, ActivityDraft?) -> Unit) {
        val byFile = items.groupBy { it.ref }
        byFile.forEach { (ref, of) -> remaining[ref] = (remaining[ref] ?: 0) + of.size }
        val done = mutableSetOf<EntryRef>()
        reader.readEach(byFile.keys) { ref, result ->
            // A file is reported again as failed when its archive fails after it: the first report stands.
            if (!done.add(ref)) return@readEach
            val read = result.getOrNull()?.let { found -> runCatchingRead { found to files.readFile(found.bytes) } }
            byFile.getValue(ref).forEach { item ->
                onDraft(item, read?.let { (found, file) -> draft(item, found, file) })
                drafted(ref)
            }
        }
        byFile
            .filterKeys { it !in done }
            .forEach { (ref, missed) ->
                missed.forEach { item ->
                    onDraft(item, null)
                    drafted(ref)
                }
            }
    }

    private fun drafted(ref: EntryRef) {
        val left = (remaining[ref] ?: 1) - 1
        if (left <= 0) {
            remaining.remove(ref)
            originals.remove(ref)
        } else remaining[ref] = left
    }

    private suspend fun draft(item: ImportSaveItemEntity, found: FoundFile, read: ImportedFile): ActivityDraft? {
        val plan = PlannedActivity.decode(item.planJson)
        val full = read.activities.getOrNull(item.entry) ?: return null
        val placed = plan.zone?.let { full.inZone(TimeZone.of(it)) } ?: full
        val ref = item.ref
        val source =
            if (plan.originalTooLarge) null
            else if (ref in originals) originals[ref]
            else runCatchingRead { SourceFile.prepare(found.bytes, found.name, read) }.also { originals[ref] = it }
        return importDraft(
                placed,
                plan.type,
                plan.title,
                choices.training,
                emptyList(),
                source?.let { DraftSource(it, entry = if (read.format.isLogbook) item.entry else null) },
            )
            // A file's own notes (a logbook's) are personal and the batch never shows them; an account export's description is the
            // athlete's own words, which they wrote to share.
            .copy(description = plan.description, gear = choices.gear, importBatch = batchId, rkey = item.rkey)
            .withVisibility(plan.visibilityChoice)
    }
}

/** [block]'s result, or null when reading a file failed: that file's activities are skipped. Cancellation still propagates. */
@Suppress("TooGenericExceptionCaught") // Any failure reading one file is that file's.
private inline fun <T> runCatchingRead(block: () -> T): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
