package io.github.solcott.activitytracker.data.db

import androidx.room3.ColumnInfo
import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Transaction
import androidx.room3.Upsert
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import kotlinx.coroutines.flow.Flow

const val DATABASE_NAME = "activity-tracker.db"

/**
 * Adds every schema migration to a platform's builder. Hand-written rather than `@Database(autoMigrations = ...)`: Kotlin/Native's backend
 * crashes lowering the `AutoMigration` annotation.
 */
fun <T : RoomDatabase> RoomDatabase.Builder<T>.addAppMigrations(): RoomDatabase.Builder<T> =
    addMigrations(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
    )

/** Runs one statement: `execSQL` blocks on most platforms and suspends on the web, so it has no common form. */
internal expect suspend fun SQLiteConnection.runSql(sql: String)

/** 1 → 2: an outbox write can target one of the athlete's tier spaces. */
private val MIGRATION_1_2 = Migration(1, 2) { it.runSql("ALTER TABLE outbox ADD COLUMN space TEXT") }

/** 2 → 3: one blob shared by several outbox writes (a logbook's original file, named by each of its dives' sources). */
private val MIGRATION_2_3 =
    Migration(2, 3) {
        it.runSql(
            "CREATE TABLE IF NOT EXISTS `outbox_blob` (`key` TEXT NOT NULL, `did` TEXT NOT NULL, `bytes` BLOB NOT NULL, " +
                "`mimeType` TEXT NOT NULL, `ref` TEXT, PRIMARY KEY(`key`))",
        )
        it.runSql("ALTER TABLE outbox ADD COLUMN blobKey TEXT")
    }

/** 3 → 4: pairs of activities the athlete said not to combine. */
private val MIGRATION_3_4 =
    Migration(3, 4) {
        it.runSql(
            "CREATE TABLE IF NOT EXISTS `combine_dismissals` (`did` TEXT NOT NULL, `uriA` TEXT NOT NULL, `uriB` TEXT NOT NULL, " +
                "PRIMARY KEY(`did`, `uriA`, `uriB`))",
        )
    }

/** 4 → 5: GPS recordings saved as they happen (record/gps-recording), so a killed app recovers them. */
private val MIGRATION_4_5 =
    Migration(4, 5) {
        it.runSql(
            "CREATE TABLE IF NOT EXISTS `recording` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `did` TEXT NOT NULL, " +
                "`startedAt` INTEGER NOT NULL)",
        )
        it.runSql(
            "CREATE TABLE IF NOT EXISTS `recording_event` (`recordingId` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                "`at` INTEGER NOT NULL, `latitude` REAL, `longitude` REAL, `altitude` REAL, `speed` REAL, `accuracy` REAL, " +
                "PRIMARY KEY(`recordingId`, `seq`))",
        )
    }

/** 5 → 6: a write the PDS refused is kept as rejected (data/outbox), so the writes behind it go on. */
private val MIGRATION_5_6 = Migration(5, 6) { it.runSql("ALTER TABLE outbox ADD COLUMN rejectedAt INTEGER") }

/** 6 → 7: a batch import's writes are bulk (data/outbox), sent after the athlete's own; a write queued before is not. */
private val MIGRATION_6_7 = Migration(6, 7) { it.runSql("ALTER TABLE outbox ADD COLUMN bulk INTEGER NOT NULL DEFAULT 0") }

/**
 * 7 → 8: indexes for the outbox's observed counts and lists ([OutboxEntity]), which otherwise scan every queued row, blob bytes included,
 * after each write.
 */
private val MIGRATION_7_8 =
    Migration(7, 8) {
        it.runSql("CREATE INDEX IF NOT EXISTS `index_outbox_did_rejectedAt_rkey` ON `outbox` (`did`, `rejectedAt`, `rkey`)")
        it.runSql("CREATE INDEX IF NOT EXISTS `index_outbox_did_rkey` ON `outbox` (`did`, `rkey`)")
        it.runSql("CREATE INDEX IF NOT EXISTS `index_outbox_blobKey` ON `outbox` (`blobKey`)")
    }

/**
 * 8 → 9: the upload queue's log beside the outbox (data/upload-queue), and an index for an import's writes left. Writes queued before have
 * no item and count as other changes.
 */
private val MIGRATION_8_9 =
    Migration(8, 9) {
        it.runSql(
            "CREATE TABLE IF NOT EXISTS `upload_batch` (`id` TEXT NOT NULL, `did` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                "`savedAt` INTEGER NOT NULL, `saved` INTEGER NOT NULL, `skipped` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        it.runSql(
            "CREATE TABLE IF NOT EXISTS `upload_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `did` TEXT NOT NULL, " +
                "`rkey` TEXT, `batchId` TEXT, `kind` TEXT NOT NULL, `title` TEXT, `sport` TEXT, `subSport` TEXT, `startedAt` INTEGER, " +
                "`queuedAt` INTEGER NOT NULL, `sentWrites` INTEGER NOT NULL, `finishedAt` INTEGER, `skipped` INTEGER NOT NULL)",
        )
        it.runSql("CREATE INDEX IF NOT EXISTS `index_upload_item_did_rkey` ON `upload_item` (`did`, `rkey`)")
        it.runSql("CREATE INDEX IF NOT EXISTS `index_upload_item_did_batchId` ON `upload_item` (`did`, `batchId`)")
        it.runSql(
            "CREATE INDEX IF NOT EXISTS `index_outbox_did_bulk_rejectedAt_rkey_collection` ON `outbox` " +
                "(`did`, `bulk`, `rejectedAt`, `rkey`, `collection`)",
        )
    }

/**
 * 9 → 10: batch saves persisted as they're tapped (feed/batch-import), so one resumes after the app is killed; an import's selected count;
 * the record of each logged activity, for checking imports against with the PDS unreachable (feed/import-duplicates).
 */
private val MIGRATION_9_10 =
    Migration(9, 10) {
        it.runSql(
            "CREATE TABLE IF NOT EXISTS `import_save` (`batchId` TEXT NOT NULL, `did` TEXT NOT NULL, `total` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `choicesJson` TEXT NOT NULL, `state` TEXT NOT NULL, PRIMARY KEY(`batchId`))",
        )
        it.runSql(
            "CREATE TABLE IF NOT EXISTS `import_save_source` (`batchId` TEXT NOT NULL, `idx` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                "`size` INTEGER NOT NULL, `kind` TEXT, `handle` BLOB, PRIMARY KEY(`batchId`, `idx`))",
        )
        it.runSql(
            "CREATE TABLE IF NOT EXISTS `import_save_item` (`batchId` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "`batchIndex` INTEGER NOT NULL, `sourceIdx` INTEGER NOT NULL, `pathJson` TEXT NOT NULL, `entry` INTEGER NOT NULL, " +
                "`rkey` TEXT NOT NULL, `planJson` TEXT NOT NULL, `groupKey` INTEGER, `done` INTEGER NOT NULL, " +
                "`skipped` INTEGER NOT NULL, PRIMARY KEY(`batchId`, `position`))",
        )
        it.runSql(
            "CREATE INDEX IF NOT EXISTS `index_import_save_item_batchId_done_skipped` ON `import_save_item` (`batchId`, `done`, `skipped`)",
        )
        it.runSql("ALTER TABLE upload_batch ADD COLUMN total INTEGER NOT NULL DEFAULT 0")
        it.runSql("ALTER TABLE upload_item ADD COLUMN recordJson TEXT")
    }

/** 10 → 11: persisted whole-account data exports (auth/data-export), their listings and items. */
private val MIGRATION_10_11 =
    Migration(10, 11) {
        it.runSql(
            "CREATE TABLE IF NOT EXISTS `data_export` (`id` TEXT NOT NULL, `did` TEXT NOT NULL, `handle` TEXT NOT NULL, " +
                "`pdsHost` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `finishedAt` INTEGER, `state` TEXT NOT NULL, `reason` TEXT, " +
                "`unsentChanges` INTEGER NOT NULL, `spacesIncluded` INTEGER NOT NULL, `neededBytes` INTEGER, PRIMARY KEY(`id`))",
        )
        it.runSql("CREATE INDEX IF NOT EXISTS `index_data_export_did` ON `data_export` (`did`)")
        it.runSql(
            "CREATE TABLE IF NOT EXISTS `data_export_list` (`exportId` TEXT NOT NULL, `place` TEXT NOT NULL, `collection` TEXT NOT NULL, " +
                "`cursor` TEXT, `done` INTEGER NOT NULL, `records` INTEGER NOT NULL, PRIMARY KEY(`exportId`, `place`, `collection`))",
        )
        it.runSql(
            "CREATE TABLE IF NOT EXISTS `data_export_item` (`exportId` TEXT NOT NULL, `kind` TEXT NOT NULL, `place` TEXT NOT NULL, " +
                "`key` TEXT NOT NULL, `size` INTEGER, `state` TEXT NOT NULL, `path` TEXT, `reason` TEXT, " +
                "PRIMARY KEY(`exportId`, `kind`, `place`, `key`))",
        )
        it.runSql(
            "CREATE INDEX IF NOT EXISTS `index_data_export_item_exportId_kind_state` ON `data_export_item` (`exportId`, `kind`, `state`)",
        )
    }

@Database(
    entities =
        [
            KeyValueEntity::class,
            PendingAuthEntity::class,
            FeedItemEntity::class,
            OutboxEntity::class,
            OutboxBlobEntity::class,
            CombineDismissalEntity::class,
            RecordingEntity::class,
            RecordingEventEntity::class,
            UploadBatchEntity::class,
            UploadItemEntity::class,
            ImportSaveEntity::class,
            ImportSaveSourceEntity::class,
            ImportSaveItemEntity::class,
            DataExportEntity::class,
            DataExportListEntity::class,
            DataExportItemEntity::class,
        ],
    version = 11,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun keyValue(): KeyValueDao

    abstract fun pendingAuth(): PendingAuthDao

    abstract fun feed(): FeedDao

    abstract fun outbox(): OutboxDao

    abstract fun combineDismissals(): CombineDismissalDao

    abstract fun recordings(): RecordingDao

    abstract fun uploads(): UploadDao

    abstract fun importSaves(): ImportSaveDao

    abstract fun dataExports(): DataExportDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** Small persisted values, e.g. the serialized OAuth session. */
@Entity(tableName = "key_value")
data class KeyValueEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface KeyValueDao {
    @Query("SELECT value FROM key_value WHERE `key` = :key") suspend fun get(key: String): String?

    @Query("SELECT value FROM key_value WHERE `key` = :key") fun observe(key: String): Flow<String?>

    @Upsert suspend fun put(entity: KeyValueEntity)

    @Query("DELETE FROM key_value WHERE `key` = :key") suspend fun delete(key: String)
}

@Entity(tableName = "pending_auth")
data class PendingAuthEntity(
    @PrimaryKey val state: String,
    val json: String,
    val createdAt: Long,
)

@Dao
interface PendingAuthDao {
    @Upsert suspend fun put(entity: PendingAuthEntity)

    @Query("SELECT * FROM pending_auth WHERE state = :state") suspend fun get(state: String): PendingAuthEntity?

    @Query("DELETE FROM pending_auth WHERE state = :state OR createdAt < :olderThan") suspend fun delete(state: String, olderThan: Long)
}

/** Cached AppView views per feed (`timeline`, `author:<did>`), in display order. */
@Entity(tableName = "feed_item", primaryKeys = ["feedKey", "uri"])
data class FeedItemEntity(
    val feedKey: String,
    val uri: String,
    val position: Int,
    /** Serialized `ActivityView`. */
    val json: String,
)

@Dao
interface FeedDao {
    @Query("SELECT * FROM feed_item WHERE feedKey = :feedKey ORDER BY position") fun observe(feedKey: String): Flow<List<FeedItemEntity>>

    @Query("SELECT * FROM feed_item WHERE uri = :uri") suspend fun byUri(uri: String): List<FeedItemEntity>

    /** Every cached item whose URI starts with [prefix] (compared literally, not as a LIKE pattern): an author's activities. */
    @Query("SELECT * FROM feed_item WHERE substr(uri, 1, length(:prefix)) = :prefix")
    suspend fun byUriPrefix(prefix: String): List<FeedItemEntity>

    @Upsert suspend fun upsert(items: List<FeedItemEntity>)

    @Query("DELETE FROM feed_item WHERE feedKey = :feedKey") suspend fun clear(feedKey: String)

    @Query("DELETE FROM feed_item WHERE uri = :uri") suspend fun deleteByUri(uri: String)

    @Transaction
    suspend fun replace(feedKey: String, items: List<FeedItemEntity>) {
        clear(feedKey)
        upsert(items)
    }

    @Query("DELETE FROM feed_item") suspend fun clearAll()
}

/**
 * Pending writes for the signed-in account, applied in order by `OutboxProcessor`: to their repo, or to one of their tier spaces ([space]).
 * Keys are client-generated TIDs so retries are idempotent (`putRecord` overwrites). A write the PDS refused is kept with [rejectedAt] set
 * until the athlete retries or discards it, and holds back the later writes with its [rkey] (data/outbox).
 *
 * Room re-runs every observed query on the table after each write, so those read indexes, never the rows: a batch import queues thousands
 * of writes with their blob bytes inline, and scanning them on every write idles the drain on the web, where one SQLite worker runs it all.
 * The indexes cover the counts and the pending count ([OutboxDao.pendingCount]), find the rejected writes and an rkey's writes, and the
 * shared blobs still named ([OutboxDao.deleteUnreferencedBlobs]), and an import's writes left ([UploadDao.bulkLeft]).
 */
@Entity(
    tableName = "outbox",
    indices =
        [
            Index("did", "rejectedAt", "rkey"),
            Index("did", "rkey"),
            Index("blobKey"),
            Index("did", "bulk", "rejectedAt", "rkey", "collection"),
        ],
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val did: String,
    /** `put` or `delete`. */
    val operation: String,
    val collection: String,
    val rkey: String,
    val recordJson: String?,
    /** Blob to upload first; its ref is written into the record's [blobField]. [OutboxDao.pending] leaves it out. */
    val blob: ByteArray? = null,
    val blobMimeType: String? = null,
    val blobField: String? = null,
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long,
    /** The athlete's tier space to write to (a space URI), or null for their repo. */
    val space: String? = null,
    /** A shared blob ([OutboxBlobEntity.key]) to write into [blobField] instead of an inline [blob]. */
    val blobKey: String? = null,
    /** When the PDS refused this write (epoch millis), or null while it's still to send. */
    val rejectedAt: Long? = null,
    /** Queued by a batch import (`BulkWrites`): sent after the athlete's own writes, and only while the PDS's limit has room to spare. */
    @ColumnInfo(defaultValue = "0") val bulk: Boolean = false,
)

/** [OutboxDao.queueState]: how many writes are queued and the newest's id. */
data class OutboxQueueState(val size: Int, val latest: Long?)

/** A rejected outbox write ([OutboxDao.rejected]), without its record or blob. */
data class RejectedOutboxWrite(
    val rkey: String,
    val collection: String,
    val operation: String,
    val rejectedAt: Long,
    /** The record of the activity this write belongs to, while its `put` is queued. */
    val activityJson: String?,
)

/**
 * A blob several outbox writes share, stored once ([OutboxEntity.blobKey]). [ref] is its `BlobRef` JSON once a write naming it succeeded,
 * so later writes reuse it rather than upload it again. Deleted once no pending write names it.
 */
@Entity(tableName = "outbox_blob")
data class OutboxBlobEntity(
    @PrimaryKey val key: String,
    val did: String,
    val bytes: ByteArray,
    val mimeType: String,
    val ref: String? = null,
)

/** Every [OutboxEntity] column, with a null `blob` ([OutboxDao.pending]). */
private const val OUTBOX_WITHOUT_BLOB =
    "id, did, operation, collection, rkey, recordJson, NULL AS blob, blobMimeType, blobField, attempts, lastError, createdAt, space, " +
        "blobKey, rejectedAt, bulk"

@Dao
@Suppress("TooManyFunctions") // One query per use: the queue, its shared blobs, and the writes the PDS rejected (data/outbox).
interface OutboxDao {
    @Insert suspend fun insert(entity: OutboxEntity): Long

    /**
     * The account's queued writes in order, without their inline blob bytes: [OutboxEntity.blob] is null here, and [inlineBlob] reads one
     * write's. A batch import's queue holds hundreds of megabytes of them, and on the web every row read crosses to the SQLite worker.
     */
    @Query("SELECT $OUTBOX_WITHOUT_BLOB FROM outbox WHERE did = :did ORDER BY id") suspend fun pending(did: String): List<OutboxEntity>

    /** [pending] for one [rkey]: an activity's queued writes, in order. */
    @Query("SELECT $OUTBOX_WITHOUT_BLOB FROM outbox WHERE did = :did AND rkey = :rkey ORDER BY id")
    suspend fun pending(did: String, rkey: String): List<OutboxEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM outbox WHERE id = :id)") suspend fun exists(id: Long): Boolean

    /** The bytes stored on write [id] to upload before its record ([OutboxEntity.blob]); null when it has none. */
    @Query("SELECT blob FROM outbox WHERE id = :id") suspend fun inlineBlob(id: Long): ByteArray?

    @Query("SELECT COUNT(*) FROM outbox WHERE did = :did") fun count(did: String): Flow<Int>

    /** The account's queue at a glance: changes whenever a write is queued or leaves it. */
    @Query("SELECT COUNT(*) AS size, MAX(id) AS latest FROM outbox WHERE did = :did") fun queueState(did: String): Flow<OutboxQueueState>

    @Query("UPDATE outbox SET attempts = attempts + 1, lastError = :error WHERE id = :id") suspend fun markFailed(id: Long, error: String)

    @Query("UPDATE outbox SET attempts = attempts + 1, lastError = :error, rejectedAt = :at WHERE id = :id")
    suspend fun markRejected(id: Long, at: Long, error: String)

    /** Writes still to send: not rejected, and not held back by a rejected write with their rkey. */
    @Query(
        "SELECT COUNT(*) FROM outbox WHERE did = :did AND rejectedAt IS NULL " +
            "AND rkey NOT IN (SELECT rkey FROM outbox WHERE did = :did AND rejectedAt IS NOT NULL)",
    )
    fun pendingCount(did: String): Flow<Int>

    /**
     * The account's rejected writes, oldest first, each with the record of the latest `put` of [activityCollection] under its rkey, if any:
     * the activity it belongs to.
     */
    @Query(
        "SELECT o.rkey, o.collection, o.operation, o.rejectedAt, " +
            "(SELECT a.recordJson FROM outbox a WHERE a.did = o.did AND a.rkey = o.rkey AND a.collection = :activityCollection " +
            "AND a.operation = 'put' ORDER BY a.id DESC LIMIT 1) AS activityJson " +
            "FROM outbox o WHERE o.did = :did AND o.rejectedAt IS NOT NULL ORDER BY o.id",
    )
    fun rejected(did: String, activityCollection: String): Flow<List<RejectedOutboxWrite>>

    @Query("UPDATE outbox SET rejectedAt = NULL WHERE did = :did AND rkey = :rkey") suspend fun clearRejected(did: String, rkey: String)

    @Query("SELECT * FROM outbox WHERE did = :did AND rkey = :rkey ORDER BY id")
    suspend fun group(did: String, rkey: String): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE did = :did AND rkey = :rkey") suspend fun deleteGroupRows(did: String, rkey: String)

    /** Deletes every write with [rkey], and the shared blobs no write names any more. */
    @Transaction
    suspend fun deleteGroup(did: String, rkey: String) {
        deleteGroupRows(did, rkey)
        deleteUnreferencedBlobs()
    }

    @Query("DELETE FROM outbox WHERE id = :id") suspend fun delete(id: Long)

    /** Stores a shared blob; a blob already stored under its key is kept, so each write naming it adds nothing. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertBlob(blob: OutboxBlobEntity)

    /** Queues [entity] naming [blob], storing the blob first if it isn't yet. */
    @Transaction
    suspend fun insert(entity: OutboxEntity, blob: OutboxBlobEntity): Long {
        insertBlob(blob)
        return insert(entity)
    }

    @Query("SELECT * FROM outbox_blob WHERE `key` = :key") suspend fun blob(key: String): OutboxBlobEntity?

    @Query("SELECT * FROM outbox_blob") suspend fun blobs(): List<OutboxBlobEntity>

    @Query("UPDATE outbox_blob SET ref = :ref WHERE `key` = :key") suspend fun setBlobRef(key: String, ref: String?)

    @Query("DELETE FROM outbox_blob WHERE `key` NOT IN (SELECT blobKey FROM outbox WHERE blobKey IS NOT NULL)")
    suspend fun deleteUnreferencedBlobs()
}

/**
 * Two activities the athlete said not to combine (`feed/combine-suggestions`), stored on the device only: [uriA] sorts before [uriB], so
 * either order finds it. Build one with [CombineDismissalEntity.of].
 */
@Entity(tableName = "combine_dismissals", primaryKeys = ["did", "uriA", "uriB"])
data class CombineDismissalEntity(val did: String, val uriA: String, val uriB: String) {
    companion object {
        fun of(did: String, x: String, y: String) = if (x <= y) CombineDismissalEntity(did, x, y) else CombineDismissalEntity(did, y, x)
    }
}

@Dao
interface CombineDismissalDao {
    @Upsert suspend fun put(entity: CombineDismissalEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM combine_dismissals WHERE did = :did AND uriA = :uriA AND uriB = :uriB)")
    suspend fun exists(did: String, uriA: String, uriB: String): Boolean
}

/** Whether the athlete [did] said not to combine [x] and [y], in either order. */
suspend fun CombineDismissalDao.dismissed(did: String, x: String, y: String): Boolean =
    CombineDismissalEntity.of(did, x, y).let { exists(it.did, it.uriA, it.uriB) }

/** A GPS recording saved as it happens (record/gps-recording), for the account [did] that started it, until it's published or discarded. */
@Entity(tableName = "recording")
data class RecordingEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val did: String, val startedAt: Long)

/**
 * One event of a [RecordingEntity], in [seq] order: a [KIND_SAMPLE] with its position, or a [KIND_PAUSED]/[KIND_RESUMED] with none. [at] is
 * epoch millis, a sample's time for a sample.
 */
@Entity(tableName = "recording_event", primaryKeys = ["recordingId", "seq"])
data class RecordingEventEntity(
    val recordingId: Long,
    val seq: Int,
    val kind: String,
    val at: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val speed: Double? = null,
    val accuracy: Double? = null,
) {
    companion object {
        const val KIND_SAMPLE = "sample"
        const val KIND_PAUSED = "paused"
        const val KIND_RESUMED = "resumed"
    }
}

@Dao
interface RecordingDao {
    @Insert suspend fun begin(entity: RecordingEntity): Long

    /** Saves an event after the recording's last one. */
    @Query(
        "INSERT INTO recording_event (recordingId, seq, kind, at, latitude, longitude, altitude, speed, accuracy) " +
            "SELECT :recordingId, COALESCE(MAX(seq) + 1, 0), :kind, :at, :latitude, :longitude, :altitude, :speed, :accuracy " +
            "FROM recording_event WHERE recordingId = :recordingId",
    )
    @Suppress("LongParameterList") // One bind parameter per column.
    suspend fun append(
        recordingId: Long,
        kind: String,
        at: Long,
        latitude: Double?,
        longitude: Double?,
        altitude: Double?,
        speed: Double?,
        accuracy: Double?,
    )

    @Query("SELECT * FROM recording WHERE did = :did ORDER BY id DESC LIMIT 1") suspend fun latest(did: String): RecordingEntity?

    @Query("SELECT * FROM recording_event WHERE recordingId = :recordingId ORDER BY seq")
    suspend fun events(recordingId: Long): List<RecordingEventEntity>

    @Query("DELETE FROM recording_event WHERE recordingId = :recordingId") suspend fun deleteEvents(recordingId: Long)

    @Query("DELETE FROM recording WHERE id = :recordingId") suspend fun deleteRecording(recordingId: Long)

    @Transaction
    suspend fun delete(recordingId: Long) {
        deleteEvents(recordingId)
        deleteRecording(recordingId)
    }
}
