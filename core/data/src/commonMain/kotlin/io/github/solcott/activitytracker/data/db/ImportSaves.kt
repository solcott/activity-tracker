package io.github.solcott.activitytracker.data.db

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * A batch import's save as written when the athlete taps Save (feed/batch-import): what any session graph in any process needs to finish
 * it, so leaving the screen, the app being frozen or killed never loses it. Keyed by the save's `importBatch` TID, the same as its
 * [UploadBatchEntity]. Deleted with its sources and items once every item is saved or skipped, or it's cancelled.
 */
@Entity(tableName = "import_save")
data class ImportSaveEntity(
    @PrimaryKey val batchId: String,
    val did: String,
    /** Activities selected, saved and skipped included. */
    val total: Int,
    /** Epoch millis. */
    val createdAt: Long,
    /** What every activity of the save shares (training, gear), as the saving feature encodes it. */
    val choicesJson: String,
    /** [STATE_RUNNING], [STATE_STOPPED] (a source it can't reopen) or [STATE_CANCELLED]. */
    val state: String = STATE_RUNNING,
) {
    companion object {
        const val STATE_RUNNING = "running"
        const val STATE_STOPPED = "stopped"
        const val STATE_CANCELLED = "cancelled"
    }
}

/**
 * One picked file of a save, by its place in the pick ([idx], an `EntryRef.source`): how it's kept so a later process can reopen it ([kind]
 * and [handle], from `SourceKeeper`), or neither when it can't be (the web).
 */
@Entity(tableName = "import_save_source", primaryKeys = ["batchId", "idx"])
data class ImportSaveSourceEntity(
    val batchId: String,
    val idx: Int,
    val name: String,
    val size: Long,
    val kind: String?,
    val handle: ByteArray?,
) {
    // A ByteArray's equals is identity: compare the handle's bytes.
    override fun equals(other: Any?): Boolean =
        other is ImportSaveSourceEntity &&
            batchId == other.batchId &&
            idx == other.idx &&
            name == other.name &&
            size == other.size &&
            kind == other.kind &&
            handle.contentEquals(other.handle)

    override fun hashCode(): Int = (batchId.hashCode() * 31 + idx) * 31 + handle.contentHashCode()
}

/**
 * One selected activity of a save, in save order ([position]): its file ([sourceIdx], [pathJson] through any archives, the activity's
 * [entry] in it), its record key, chosen at Save so resuming rewrites the same records, and the rest of its draft as the saving feature
 * resolved it ([planJson]). [batchIndex] is its row in the reviewed batch. A combine group's members share [groupKey], saved together.
 */
@Entity(tableName = "import_save_item", primaryKeys = ["batchId", "position"], indices = [Index("batchId", "done", "skipped")])
data class ImportSaveItemEntity(
    val batchId: String,
    val position: Int,
    val batchIndex: Int,
    val sourceIdx: Int,
    val pathJson: String,
    val entry: Int,
    val rkey: String,
    val planJson: String,
    val groupKey: Int? = null,
    val done: Boolean = false,
    val skipped: Boolean = false,
)

/** A save's items saved ([done]) and [skipped] so far, of its [total]. */
data class ImportSaveCounts(val batchId: String, val did: String, val total: Int, val state: String, val done: Int, val skipped: Int)

/** The persisted batch saves ([ImportSaveEntity]) and their sources and items. */
@Dao
@Suppress("TooManyFunctions") // One query per step of a save: writing, resuming, marking, counting and dropping.
interface ImportSaveDao {
    @Insert suspend fun insertSave(save: ImportSaveEntity)

    @Insert suspend fun insertSources(sources: List<ImportSaveSourceEntity>)

    @Insert suspend fun insertItems(items: List<ImportSaveItemEntity>)

    /** Writes a save whole, so it's never resumed half-written. */
    @Transaction
    suspend fun plan(save: ImportSaveEntity, sources: List<ImportSaveSourceEntity>, items: List<ImportSaveItemEntity>) {
        insertSave(save)
        insertSources(sources)
        insertItems(items)
    }

    @Query("SELECT * FROM import_save WHERE batchId = :batchId") suspend fun save(batchId: String): ImportSaveEntity?

    @Query("SELECT * FROM import_save WHERE batchId = :batchId") fun saveFlow(batchId: String): Flow<ImportSaveEntity?>

    /** [did]'s saves still to run, oldest first. */
    @Query("SELECT * FROM import_save WHERE did = :did AND state = 'running' ORDER BY createdAt")
    suspend fun running(did: String): List<ImportSaveEntity>

    @Query("SELECT batchId FROM import_save") suspend fun allIds(): List<String>

    @Query("SELECT * FROM import_save_source WHERE batchId = :batchId ORDER BY idx")
    suspend fun sources(batchId: String): List<ImportSaveSourceEntity>

    @Query("SELECT * FROM import_save_source") suspend fun allSources(): List<ImportSaveSourceEntity>

    /** Items neither saved nor skipped yet, in save order. */
    @Query("SELECT * FROM import_save_item WHERE batchId = :batchId AND done = 0 AND skipped = 0 ORDER BY position")
    suspend fun pending(batchId: String): List<ImportSaveItemEntity>

    @Query("SELECT * FROM import_save_item WHERE batchId = :batchId AND done = 1 ORDER BY position")
    suspend fun done(batchId: String): List<ImportSaveItemEntity>

    /** The batch indices of items saved or skipped. */
    @Query("SELECT batchIndex FROM import_save_item WHERE batchId = :batchId AND (done = 1 OR skipped = 1)")
    suspend fun settledIndices(batchId: String): List<Int>

    @Query("UPDATE import_save_item SET done = 1 WHERE batchId = :batchId AND position IN (:positions)")
    suspend fun markDone(batchId: String, positions: List<Int>)

    @Query("UPDATE import_save_item SET skipped = 1 WHERE batchId = :batchId AND position IN (:positions)")
    suspend fun markSkipped(batchId: String, positions: List<Int>)

    @Query("UPDATE import_save SET state = :state WHERE batchId = :batchId") suspend fun setState(batchId: String, state: String)

    @Query("UPDATE import_save SET total = (SELECT COUNT(*) FROM import_save_item WHERE batchId = :batchId) WHERE batchId = :batchId")
    suspend fun recount(batchId: String)

    /** Every save's counts for [did], for progress and stopped imports. */
    @Query(
        "SELECT s.batchId, s.did, s.total, s.state, " +
            "(SELECT COUNT(*) FROM import_save_item i WHERE i.batchId = s.batchId AND i.done = 1) AS done, " +
            "(SELECT COUNT(*) FROM import_save_item i WHERE i.batchId = s.batchId AND i.skipped = 1) AS skipped " +
            "FROM import_save s WHERE s.did = :did ORDER BY s.createdAt",
    )
    fun counts(did: String): Flow<List<ImportSaveCounts>>

    @Query(
        "SELECT s.batchId, s.did, s.total, s.state, " +
            "(SELECT COUNT(*) FROM import_save_item i WHERE i.batchId = s.batchId AND i.done = 1) AS done, " +
            "(SELECT COUNT(*) FROM import_save_item i WHERE i.batchId = s.batchId AND i.skipped = 1) AS skipped " +
            "FROM import_save s WHERE s.batchId = :batchId",
    )
    suspend fun countsOf(batchId: String): ImportSaveCounts?

    @Query("DELETE FROM import_save_item WHERE batchId = :batchId") suspend fun deleteItems(batchId: String)

    @Query("DELETE FROM import_save_source WHERE batchId = :batchId") suspend fun deleteSources(batchId: String)

    @Query("DELETE FROM import_save WHERE batchId = :batchId") suspend fun deleteSave(batchId: String)

    /** Drops a save with its sources and items. */
    @Transaction
    suspend fun delete(batchId: String) {
        deleteItems(batchId)
        deleteSources(batchId)
        deleteSave(batchId)
    }

    @Query("SELECT batchId FROM import_save WHERE did = :did") suspend fun idsOf(did: String): List<String>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM import_save_item WHERE batchId = :batchId")
    suspend fun nextPosition(batchId: String): Int

    @Query("DELETE FROM import_save_item WHERE batchId = :batchId AND done = 0 AND skipped = 0") suspend fun deletePending(batchId: String)

    /** Replaces save [batchId]'s pending items with [items], as the athlete changed the selection after a failure, and recounts it. */
    @Transaction
    suspend fun replacePending(batchId: String, items: List<ImportSaveItemEntity>) {
        deletePending(batchId)
        insertItems(items)
        recount(batchId)
        setState(batchId, ImportSaveEntity.STATE_RUNNING)
    }

    @Query("UPDATE import_save_source SET kind = :kind, handle = :handle WHERE batchId = :batchId AND idx = :idx")
    suspend fun updateHandle(batchId: String, idx: Int, kind: String, handle: ByteArray)

    @Query("UPDATE import_save_source SET kind = NULL, handle = NULL WHERE batchId = :batchId") suspend fun clearHandles(batchId: String)
}
