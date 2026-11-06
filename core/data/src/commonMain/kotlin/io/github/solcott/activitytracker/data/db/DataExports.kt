package io.github.solcott.activitytracker.data.db

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
 * A whole-account data export (auth/data-export): what any session graph in any process needs to resume it, since it outlives the screen
 * that started it. An account has at most one. Deleted with its listings and items when it's cancelled, saved, expires or the account signs
 * out; its staged files live under `exports/<id>/` in app-private storage.
 */
@Entity(tableName = "data_export", indices = [Index("did")])
data class DataExportEntity(
    @PrimaryKey val id: String,
    val did: String,
    /** The handle and PDS host as the session knew them when it started, for the manifest and the archive's name. */
    val handle: String,
    val pdsHost: String,
    /** Epoch millis. */
    val createdAt: Long,
    /** Epoch millis it became [STATE_READY]. */
    val finishedAt: Long? = null,
    /** [STATE_RUNNING], [STATE_PAUSED], [STATE_READY] or [STATE_FAILED]. */
    val state: String = STATE_RUNNING,
    /** Why it's paused or failed: one of the `REASON_` values. */
    val reason: String? = null,
    /** Changes waiting to upload on this device when it started, which the PDS doesn't hold yet. */
    val unsentChanges: Int = 0,
    /** Whether the session could read the athlete's spaces; without them only the repo is exported. */
    val spacesIncluded: Boolean = true,
    /** While paused for space: the free bytes it needs to go on. */
    val neededBytes: Long? = null,
) {
    companion object {
        const val STATE_RUNNING = "running"
        const val STATE_PAUSED = "paused"
        const val STATE_READY = "ready"
        const val STATE_FAILED = "failed"

        const val REASON_NO_SPACE = "noSpace"
        const val REASON_OFFLINE = "offline"
        const val REASON_SIGNED_OUT = "signedOut"
        const val REASON_STORAGE = "storage"
    }
}

/**
 * How far an export has listed one collection in one place ([place]: `repo` or a space's key): the cursor of the next page, so a resumed
 * export goes on from there, and how many records it has written.
 */
@Entity(tableName = "data_export_list", primaryKeys = ["exportId", "place", "collection"])
data class DataExportListEntity(
    val exportId: String,
    val place: String,
    val collection: String,
    val cursor: String? = null,
    val done: Boolean = false,
    val records: Int = 0,
)

/**
 * Something an export fetches or builds after listing: a blob ([KIND_BLOB], [key] its CID) or an activity's file ([KIND_ACTIVITY], [key]
 * its rkey). [path] is where it was staged, relative to the export's folder; [reason] why it was skipped.
 */
@Entity(tableName = "data_export_item", primaryKeys = ["exportId", "kind", "place", "key"], indices = [Index("exportId", "kind", "state")])
data class DataExportItemEntity(
    val exportId: String,
    val kind: String,
    val place: String,
    val key: String,
    /** A blob's size as its record declares it, in bytes, for the space estimate. */
    val size: Long? = null,
    val state: String = STATE_PENDING,
    val path: String? = null,
    val reason: String? = null,
) {
    companion object {
        const val KIND_BLOB = "blob"
        const val KIND_ACTIVITY = "activity"

        const val STATE_PENDING = "pending"
        const val STATE_DONE = "done"
        const val STATE_SKIPPED = "skipped"
    }
}

/** An export's items by kind and state. */
data class DataExportItemCount(val kind: String, val state: String, val count: Int)

/** The persisted data exports ([DataExportEntity]), their listings and items. */
@Dao
@Suppress("TooManyFunctions") // One query per step of an export: starting, listing, fetching, counting and dropping.
interface DataExportDao {
    @Insert suspend fun insert(export: DataExportEntity)

    @Query("SELECT * FROM data_export WHERE id = :id") suspend fun export(id: String): DataExportEntity?

    /** [did]'s export, if any (there's at most one). */
    @Query("SELECT * FROM data_export WHERE did = :did ORDER BY createdAt DESC LIMIT 1")
    suspend fun exportOf(did: String): DataExportEntity?

    @Query("SELECT * FROM data_export WHERE did = :did ORDER BY createdAt DESC LIMIT 1")
    fun exportFlow(did: String): Flow<DataExportEntity?>

    @Query("SELECT * FROM data_export") suspend fun all(): List<DataExportEntity>

    @Query("UPDATE data_export SET state = :state, reason = :reason, neededBytes = :neededBytes WHERE id = :id")
    suspend fun setState(id: String, state: String, reason: String? = null, neededBytes: Long? = null)

    @Query("UPDATE data_export SET state = 'ready', reason = NULL, neededBytes = NULL, finishedAt = :at WHERE id = :id")
    suspend fun ready(id: String, at: Long)

    @Query("SELECT * FROM data_export_list WHERE exportId = :id") suspend fun lists(id: String): List<DataExportListEntity>

    @Upsert suspend fun putList(list: DataExportListEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addItems(items: List<DataExportItemEntity>)

    /** Writes a listed page's progress and the items it named at once, so a resumed export neither skips nor repeats them. */
    @Transaction
    suspend fun listed(list: DataExportListEntity, items: List<DataExportItemEntity>) {
        addItems(items)
        putList(list)
    }

    @Query("SELECT * FROM data_export_item WHERE exportId = :id AND kind = :kind AND state = 'pending' ORDER BY place, `key` LIMIT :limit")
    suspend fun pending(id: String, kind: String, limit: Int): List<DataExportItemEntity>

    @Query("SELECT * FROM data_export_item WHERE exportId = :id AND kind = :kind")
    suspend fun items(id: String, kind: String): List<DataExportItemEntity>

    @Query("SELECT * FROM data_export_item WHERE exportId = :id AND state = 'skipped' ORDER BY kind, place, `key`")
    suspend fun skipped(id: String): List<DataExportItemEntity>

    @Query("SELECT path FROM data_export_item WHERE exportId = :id AND kind = 'activity' AND path IS NOT NULL")
    suspend fun activityPaths(id: String): List<String>

    @Query(
        "UPDATE data_export_item SET state = 'done', path = :path WHERE exportId = :id AND kind = :kind AND place = :place AND `key` = :key"
    )
    suspend fun done(id: String, kind: String, place: String, key: String, path: String?)

    @Query(
        "UPDATE data_export_item SET state = 'skipped', reason = :reason " +
            "WHERE exportId = :id AND kind = :kind AND place = :place AND `key` = :key"
    )
    suspend fun skip(id: String, kind: String, place: String, key: String, reason: String)

    @Query("SELECT kind, state, COUNT(*) AS count FROM data_export_item WHERE exportId = :id GROUP BY kind, state")
    fun countsFlow(id: String): Flow<List<DataExportItemCount>>

    @Query("SELECT kind, state, COUNT(*) AS count FROM data_export_item WHERE exportId = :id GROUP BY kind, state")
    suspend fun counts(id: String): List<DataExportItemCount>

    @Query("SELECT COALESCE(SUM(records), 0) FROM data_export_list WHERE exportId = :id") suspend fun recordCount(id: String): Int

    @Query("SELECT COALESCE(SUM(size), 0) FROM data_export_item WHERE exportId = :id AND kind = 'blob' AND state = 'pending'")
    suspend fun pendingBlobBytes(id: String): Long

    @Query("DELETE FROM data_export WHERE id = :id") suspend fun deleteExport(id: String)

    @Query("DELETE FROM data_export_list WHERE exportId = :id") suspend fun deleteLists(id: String)

    @Query("DELETE FROM data_export_item WHERE exportId = :id") suspend fun deleteItems(id: String)

    /** Drops export [id] with its listings and items. */
    @Transaction
    suspend fun drop(id: String) {
        deleteItems(id)
        deleteLists(id)
        deleteExport(id)
    }
}
