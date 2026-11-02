package io.github.solcott.activitytracker.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * One saved batch import in the upload queue (data/upload-queue), keyed by its `importBatch` TID: what was imported ([source], a recognized
 * export's name or the picked files'), when it was saved, how many activities it [saved] and [skipped], and how many were selected
 * ([total]; 0 for one saved before it was counted).
 */
@Entity(tableName = "upload_batch")
data class UploadBatchEntity(
    @PrimaryKey val id: String,
    val did: String,
    val source: String,
    val savedAt: Long,
    val saved: Int = 0,
    val skipped: Int = 0,
    @ColumnInfo(defaultValue = "0") val total: Int = 0,
)

/**
 * One activity saved or edited (data/upload-queue), kept beside the outbox: the outbox forgets a write once it's sent, so this keeps what
 * the queue can't derive from it. It's live while [finishedAt] is null, at most one per [rkey]; its state is derived from the outbox's rows
 * for [rkey] ([UploadDao.items]). A file skipped at save has no [rkey] and is finished as it's logged.
 */
@Entity(tableName = "upload_item", indices = [Index("did", "rkey"), Index("did", "batchId")])
data class UploadItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val did: String,
    val rkey: String?,
    /** The import it belongs to ([UploadBatchEntity.id]), or null for the athlete's own. */
    val batchId: String?,
    /** [KIND_NEW] or [KIND_EDIT]. */
    val kind: String,
    val title: String?,
    val sport: String?,
    val subSport: String?,
    /** Epoch millis. */
    val startedAt: Long?,
    val queuedAt: Long,
    /** Writes of [rkey] sent since this item was logged. */
    val sentWrites: Int = 0,
    val finishedAt: Long? = null,
    val skipped: Boolean = false,
    /**
     * The activity record as queued, so an import is checked against it with the PDS unreachable (feed/import-duplicates). Never read by
     * the queue's observed queries.
     */
    val recordJson: String? = null,
) {
    companion object {
        const val KIND_NEW = "new"
        const val KIND_EDIT = "edit"
    }
}

/**
 * An [UploadItemEntity] with what the outbox still holds for its rkey: [remaining] writes, [rejected] of them refused, and [firstWrite],
 * the id of the first, which orders items as they'll be sent.
 */
data class UploadItemRow(
    val id: Long,
    val rkey: String?,
    val batchId: String?,
    val kind: String,
    val title: String?,
    val sport: String?,
    val subSport: String?,
    val startedAt: Long?,
    val queuedAt: Long,
    val sentWrites: Int,
    val finishedAt: Long?,
    val skipped: Boolean,
    val remaining: Int,
    val rejected: Int,
    val firstWrite: Long?,
)

/** [UploadDao.loggedRecords]: an activity's [rkey] and its record as queued. */
data class LoggedRecord(val rkey: String, val recordJson: String)

/** [UploadDao.bulkLeft]: an import's writes still to send, and how many of them carry a blob. */
data class BulkLeft(val writes: Int, val blobs: Int)

/**
 * The upload log ([UploadItemEntity], [UploadBatchEntity]) and the outbox writes it follows. Its observed queries read the outbox through
 * its `(did, rkey)` and `(did, rejectedAt, rkey)` indexes and never its blobs: Room re-runs them after every outbox write ([OutboxEntity]).
 */
@Dao
@Suppress("TooManyFunctions") // One query per use: logging, sending, cancelling, listing and pruning.
interface UploadDao {
    @Insert suspend fun insert(item: UploadItemEntity): Long

    @Upsert suspend fun putBatch(batch: UploadBatchEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertBatchIfAbsent(batch: UploadBatchEntity)

    @Query("UPDATE upload_batch SET total = :total WHERE id = :id") suspend fun setBatchTotal(id: String, total: Int)

    @Query("SELECT * FROM upload_batch WHERE id = :id") suspend fun batch(id: String): UploadBatchEntity?

    @Query("UPDATE upload_batch SET saved = :saved, skipped = :skipped WHERE id = :id")
    suspend fun countBatch(id: String, saved: Int, skipped: Int)

    @Query("SELECT * FROM upload_item WHERE did = :did AND rkey = :rkey AND finishedAt IS NULL LIMIT 1")
    suspend fun live(did: String, rkey: String): UploadItemEntity?

    @Query("UPDATE upload_item SET title = :title, sport = :sport, subSport = :subSport, recordJson = :recordJson WHERE id = :id")
    suspend fun retitle(id: Long, title: String?, sport: String?, subSport: String?, recordJson: String?)

    @Query("SELECT * FROM upload_item WHERE did = :did AND batchId = :batchId AND rkey = :rkey ORDER BY id DESC LIMIT 1")
    suspend fun ofBatch(did: String, batchId: String, rkey: String): UploadItemEntity?

    @Query("UPDATE upload_item SET finishedAt = NULL, sentWrites = 0 WHERE id = :id") suspend fun reopen(id: Long)

    /**
     * Logs [item], or for one already live for its rkey, refreshes that one's title and type (data/upload-queue: one item per activity). An
     * import's activity queued again (a resumed save, after the process stopped between its writes and marking it saved) reopens its item,
     * even once that item finished sending the writes queued before the stop.
     */
    @Transaction
    suspend fun queued(item: UploadItemEntity) {
        val rkey = item.rkey
        val live =
            rkey?.let { live(item.did, it) }
                ?: rkey?.let { key -> item.batchId?.let { ofBatch(item.did, it, key) } }?.also { reopen(it.id) }
        if (live == null) insert(item) else retitle(live.id, item.title, item.sport, item.subSport, item.recordJson ?: live.recordJson)
    }

    @Query("UPDATE upload_item SET sentWrites = sentWrites + 1 WHERE did = :did AND rkey = :rkey AND finishedAt IS NULL")
    suspend fun countSent(did: String, rkey: String)

    /** Finishes [rkey]'s live item once the outbox holds none of its writes. */
    @Query(
        "UPDATE upload_item SET finishedAt = :at WHERE did = :did AND rkey = :rkey AND finishedAt IS NULL " +
            "AND NOT EXISTS (SELECT 1 FROM outbox WHERE did = :did AND rkey = :rkey)"
    )
    suspend fun finishIfDone(did: String, rkey: String, at: Long)

    @Query("DELETE FROM outbox WHERE id = :id") suspend fun deleteWrite(id: Long)

    /** Write [id] of [rkey] was sent: it leaves the outbox, and its item counts it, and finishes with its last, in one transaction. */
    @Transaction
    suspend fun sent(id: Long, did: String, rkey: String, at: Long) {
        deleteWrite(id)
        countSent(did, rkey)
        finishIfDone(did, rkey, at)
    }

    /** Write [id] of [rkey] was dropped (an original the PDS refused as too large): it leaves the outbox, uncounted. */
    @Transaction
    suspend fun dropped(id: Long, did: String, rkey: String, at: Long) {
        deleteWrite(id)
        finishIfDone(did, rkey, at)
    }

    @Query("DELETE FROM upload_item WHERE did = :did AND rkey = :rkey AND finishedAt IS NULL")
    suspend fun deleteLive(did: String, rkey: String)

    @Query("DELETE FROM outbox WHERE did = :did AND rkey = :rkey") suspend fun deleteWrites(did: String, rkey: String)

    @Query("DELETE FROM outbox_blob WHERE `key` NOT IN (SELECT blobKey FROM outbox WHERE blobKey IS NOT NULL)")
    suspend fun deleteUnreferencedBlobs()

    /** Drops every write of [rkey] and its live item, with the shared blobs no write names any more: a cancel. */
    @Transaction
    suspend fun cancel(did: String, rkey: String) {
        deleteWrites(did, rkey)
        deleteLive(did, rkey)
        deleteUnreferencedBlobs()
    }

    /** Every item with what the outbox still holds for it, in the order logged. */
    @Query(
        "SELECT i.id, i.rkey, i.batchId, i.kind, i.title, i.sport, i.subSport, i.startedAt, i.queuedAt, i.sentWrites, i.finishedAt, " +
            "i.skipped, " +
            "(SELECT COUNT(*) FROM outbox o WHERE o.did = i.did AND o.rkey = i.rkey) AS remaining, " +
            "(SELECT COUNT(*) FROM outbox o WHERE o.did = i.did AND o.rejectedAt IS NOT NULL AND o.rkey = i.rkey) AS rejected, " +
            "(SELECT MIN(o.id) FROM outbox o WHERE o.did = i.did AND o.rkey = i.rkey) AS firstWrite " +
            "FROM upload_item i WHERE i.did = :did ORDER BY i.id"
    )
    fun items(did: String): Flow<List<UploadItemRow>>

    @Query("SELECT * FROM upload_batch WHERE did = :did ORDER BY savedAt DESC") fun batches(did: String): Flow<List<UploadBatchEntity>>

    /** [did]'s activities logged as new, with their records, started in [since, until) (epoch millis): the duplicate check's local log. */
    @Query(
        "SELECT rkey, recordJson FROM upload_item WHERE did = :did AND kind = 'new' AND rkey IS NOT NULL AND recordJson IS NOT NULL " +
            "AND startedAt >= :since AND startedAt < :until ORDER BY startedAt"
    )
    suspend fun loggedRecords(did: String, since: Long, until: Long): List<LoggedRecord>

    /** The activities import [batchId] logged as new, with their records, in the order saved. */
    @Query(
        "SELECT rkey, recordJson FROM upload_item WHERE did = :did AND batchId = :batchId AND kind = 'new' AND rkey IS NOT NULL " +
            "AND recordJson IS NOT NULL ORDER BY id"
    )
    suspend fun recordsOfBatch(did: String, batchId: String): List<LoggedRecord>

    @Query("SELECT rkey FROM upload_item WHERE did = :did AND batchId = :batchId AND rkey IS NOT NULL ORDER BY id")
    suspend fun rkeysOfBatch(did: String, batchId: String): List<String>

    /** Changes (rkeys) with writes still to send that aren't live items: likes, comments, deletes, and writes queued before the log. */
    @Query(
        "SELECT COUNT(DISTINCT o.rkey) FROM outbox o WHERE o.did = :did AND o.rejectedAt IS NULL " +
            "AND o.rkey NOT IN (SELECT rkey FROM outbox WHERE did = :did AND rejectedAt IS NOT NULL) " +
            "AND o.rkey NOT IN (SELECT rkey FROM upload_item WHERE did = :did AND finishedAt IS NULL AND rkey IS NOT NULL)"
    )
    fun otherChanges(did: String): Flow<Int>

    /** Bulk writes still to send, not held by a refused one, and those of them of [blobCollections], for the time left. */
    @Query(
        "SELECT COUNT(*) AS writes, COALESCE(SUM(collection IN (:blobCollections)), 0) AS blobs FROM outbox " +
            "WHERE did = :did AND bulk = 1 AND rejectedAt IS NULL " +
            "AND rkey NOT IN (SELECT rkey FROM outbox WHERE did = :did AND rejectedAt IS NOT NULL)"
    )
    fun bulkLeft(did: String, blobCollections: List<String>): Flow<BulkLeft>

    @Query("SELECT * FROM upload_item WHERE did = :did AND batchId = :batchId AND finishedAt IS NULL ORDER BY id")
    suspend fun liveOfBatch(did: String, batchId: String): List<UploadItemEntity>

    /** Live imported items, for the close warning and the notification. */
    @Query("SELECT COUNT(*) FROM upload_item WHERE did = :did AND finishedAt IS NULL AND batchId IS NOT NULL")
    fun liveImported(did: String): Flow<Int>

    /** Finishes live items whose writes all left the outbox but weren't counted (a crash between a send and its log). */
    @Query(
        "UPDATE upload_item SET finishedAt = :at WHERE did = :did AND finishedAt IS NULL AND rkey IS NOT NULL " +
            "AND NOT EXISTS (SELECT 1 FROM outbox o WHERE o.did = :did AND o.rkey = upload_item.rkey)"
    )
    suspend fun finishOrphans(did: String, at: Long)

    @Query("DELETE FROM upload_item WHERE did = :did AND finishedAt IS NOT NULL AND finishedAt < :before")
    suspend fun deleteFinishedBefore(did: String, before: Long)

    /** Drops [did]'s imports with no items left, keeping those whose save is still there (saving, or stopped before saving any). */
    @Query(
        "DELETE FROM upload_batch WHERE did = :did AND id NOT IN (SELECT batchId FROM upload_item WHERE batchId IS NOT NULL) " +
            "AND id NOT IN (SELECT batchId FROM import_save)"
    )
    suspend fun deleteEmptyBatches(did: String)

    /** Drops items finished before [before] and the imports left without items; first finishes those the outbox holds nothing of. */
    @Transaction
    suspend fun prune(did: String, now: Long, before: Long) {
        finishOrphans(did, now)
        deleteFinishedBefore(did, before)
        deleteEmptyBatches(did)
    }
}
