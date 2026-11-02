package io.github.solcott.activitytracker.data.importsave

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.activityfile.ImportSource
import io.github.solcott.activitytracker.data.activity.UploadLog
import io.github.solcott.activitytracker.data.activity.UploadSource
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.ImportSaveCounts
import io.github.solcott.activitytracker.data.db.ImportSaveEntity
import io.github.solcott.activitytracker.data.db.ImportSaveItemEntity
import io.github.solcott.activitytracker.data.db.ImportSaveSourceEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import kotlin.time.Clock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** A batch save as the athlete tapped Save: the [items] to save from the picked [sources], of [total] selected, under [batchId]. */
class NewImportSave(
    val batchId: String,
    val source: UploadSource,
    val sources: List<ImportSource>,
    val items: List<ImportSaveItemEntity>,
    /** What every activity shares, as the saving feature encodes it. */
    val choicesJson: String,
)

/** One picked file of a save as a run finds it: open to read, gone (its activities are skipped), or never kept (the save stops). */
sealed interface SaveSource {
    class Open(val source: ImportSource, val close: () -> Unit = {}) : SaveSource

    data object Gone : SaveSource

    data object NotKept : SaveSource
}

/**
 * The signed-in athlete's persisted batch saves (feed/batch-import): written whole when they tap Save, worked through by an
 * [ImportSaveRunner], and dropped with what keeps their files once every activity is saved or skipped, or the rest is cancelled. A save
 * that can't reopen a file it never kept (the web, after the tab closed) stops, and is shown as stopped until the athlete dismisses it.
 */
@SingleIn(SessionScope::class)
@Inject
@Suppress("TooManyFunctions") // One method per step of a save.
class ImportSaveStore(
    private val db: AppDatabase,
    private val clients: SessionClients,
    private val keeper: SourceKeeper,
    private val saves: ImportSaves,
    private val clock: Clock,
) {
    private val did: String
        get() = clients.did.value

    /** The account whose saves these are. */
    val account: Did
        get() = clients.did

    /** Each of the athlete's saves with how far it got, for progress and stopped imports. */
    val counts: Flow<List<ImportSaveCounts>>
        get() = db.importSaves().counts(did)

    /**
     * Writes [save] so it survives the process: keeps its picked files where the platform can, writes its items, and lists the import in
     * the upload queue with its selected count. Its picked files stay at hand for runs in this process.
     */
    suspend fun start(save: NewImportSave) {
        val kept = keeper.keep(save.batchId, save.sources)
        val sources =
            save.sources.mapIndexed { idx, source ->
                ImportSaveSourceEntity(
                    save.batchId,
                    idx,
                    source.name,
                    source.size,
                    kept.getOrNull(idx)?.kind?.name,
                    kept.getOrNull(idx)?.data,
                )
            }
        val now = clock.now()
        db.importSaves()
            .plan(
                ImportSaveEntity(save.batchId, did, total = save.items.size, createdAt = now.toEpochMilliseconds(), save.choicesJson),
                sources,
                save.items,
            )
        UploadLog.batch(db, did, save.batchId, save.source.encode(), save.items.size, now)
        saves.hold(save.batchId, save.sources)
    }

    /** Where save [batchId]'s next items go, after every item it has. */
    suspend fun nextPosition(batchId: String): Int = db.importSaves().nextPosition(batchId)

    /**
     * Replaces save [batchId]'s pending activities with [items] before it runs again after a failure: the athlete may have changed the
     * selection. Its import counts the new selection.
     */
    suspend fun replacePending(batchId: String, items: List<ImportSaveItemEntity>) {
        db.importSaves().replacePending(batchId, items)
        db.importSaves().save(batchId)?.let { db.uploads().setBatchTotal(batchId, it.total) }
    }

    /** The batch indices of save [batchId]'s activities saved or skipped so far, which saving again leaves out. */
    suspend fun settledIndices(batchId: String): Set<Int> = db.importSaves().settledIndices(batchId).toSet()

    /** What import [batchId] saved and skipped, as its upload queue entry counts them. */
    suspend fun importCounts(batchId: String): Pair<Int, Int>? = db.uploads().batch(batchId)?.let { it.saved to it.skipped }

    /** The activities import [batchId] saved. */
    suspend fun savedUris(batchId: String): List<AtUri> =
        db.uploads().rkeysOfBatch(did, batchId).map { AtUri.of(clients.did, Nsid.FEED_ACTIVITY, it) }

    /** The activities import [batchId] saved, with their records as queued, in the order saved. */
    suspend fun savedRecords(batchId: String): List<Pair<AtUri, ActivityRecord>> =
        db.uploads().recordsOfBatch(did, batchId).map { logged ->
            AtUri.of(clients.did, Nsid.FEED_ACTIVITY, logged.rkey) to
                AtJson.decodeFromString(ActivityRecord.serializer(), logged.recordJson)
        }

    /** The athlete's saves still to run, oldest first. */
    suspend fun runnable(): List<ImportSaveEntity> = db.importSaves().running(did)

    suspend fun save(batchId: String): ImportSaveEntity? = db.importSaves().save(batchId)

    suspend fun pending(batchId: String): List<ImportSaveItemEntity> = db.importSaves().pending(batchId)

    suspend fun done(batchId: String): List<ImportSaveItemEntity> = db.importSaves().done(batchId)

    suspend fun countsOf(batchId: String): ImportSaveCounts? = db.importSaves().countsOf(batchId)

    suspend fun markDone(batchId: String, positions: List<Int>) {
        if (positions.isNotEmpty()) db.importSaves().markDone(batchId, positions)
    }

    suspend fun markSkipped(batchId: String, positions: List<Int>) {
        if (positions.isNotEmpty()) db.importSaves().markSkipped(batchId, positions)
    }

    /** Whether save [batchId] was cancelled (or dropped) since it started: the runner stops between activities. */
    suspend fun isCancelled(batchId: String): Boolean =
        db.importSaves().save(batchId)?.state.let { it == null || it == ImportSaveEntity.STATE_CANCELLED }

    /**
     * Save [batchId]'s picked files, by their place in the pick: those picked in this process as they are, else reopened from how they were
     * kept.
     */
    suspend fun open(batchId: String): List<SaveSource> {
        saves.picked(batchId)?.let { picked ->
            return picked.map { SaveSource.Open(it) }
        }
        return db.importSaves().sources(batchId).map { row ->
            val kept = row.kind?.let { kind -> row.handle?.let { KeptSource.of(kind, it) } }
            when {
                kept == null -> SaveSource.NotKept
                else -> {
                    val reopened = keeper.open(kept)
                    if (reopened == null) SaveSource.Gone
                    else {
                        reopened.refreshed?.let { refreshed ->
                            db.importSaves().updateHandle(batchId, row.idx, refreshed.kind.name, refreshed.data)
                        }
                        SaveSource.Open(reopened.source, reopened::close)
                    }
                }
            }
        }
    }

    /** Counts what save [batchId] saved and skipped into its import, and ends it once nothing is left to save. */
    suspend fun counted(batchId: String) {
        val counts = db.importSaves().countsOf(batchId) ?: return
        UploadLog.counted(db, batchId, saved = counts.done, skipped = counts.skipped)
        if (counts.done + counts.skipped >= counts.total) end(batchId)
    }

    /** Stops save [batchId], which can't reopen a file it never kept: it's shown as stopped, and lets go of the files it kept. */
    suspend fun stop(batchId: String) {
        db.importSaves().setState(batchId, ImportSaveEntity.STATE_STOPPED)
        release(batchId)
    }

    /** Cancels the rest of save [batchId] (data/upload-queue): a running save stops before its next activity. */
    suspend fun cancel(batchId: String) =
        withContext(NonCancellable) {
            db.importSaves().setState(batchId, ImportSaveEntity.STATE_CANCELLED)
            if (batchId !in saves.running.value) end(batchId)
        }

    /** Ends save [batchId]: lets go of its files and drops it. Its import stays in the upload queue with what it saved. */
    suspend fun end(batchId: String) = saves.end(batchId)

    /**
     * Dismisses a stopped import's notice: the import counts only what it saved and skipped, so it's no longer stopped, and the save is
     * dropped.
     */
    suspend fun dismiss(batchId: String) =
        withContext(NonCancellable) {
            val batch = db.uploads().batch(batchId)
            if (batch != null) db.uploads().setBatchTotal(batchId, batch.saved + batch.skipped)
            end(batchId)
        }

    /** Lets go of grants and copies that no save names any more: a crash between a save's end and its release. */
    suspend fun sweep() = saves.sweep()

    private suspend fun release(batchId: String) = saves.release(batchId)
}
