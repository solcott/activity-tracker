package io.github.solcott.activitytracker.data.db

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
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
    addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

/** Runs one statement: `execSQL` blocks on most platforms and suspends on the web, so it has no common form. */
internal expect suspend fun SQLiteConnection.runSql(sql: String)

/** 1 → 2: an outbox write can target one of the athlete's tier spaces. */
private val MIGRATION_1_2 = Migration(1, 2) { it.runSql("ALTER TABLE outbox ADD COLUMN space TEXT") }

/** 2 → 3: one blob shared by several outbox writes (a logbook's original file, named by each of its dives' sources). */
private val MIGRATION_2_3 =
    Migration(2, 3) {
        it.runSql(
            "CREATE TABLE IF NOT EXISTS `outbox_blob` (`key` TEXT NOT NULL, `did` TEXT NOT NULL, `bytes` BLOB NOT NULL, " +
                "`mimeType` TEXT NOT NULL, `ref` TEXT, PRIMARY KEY(`key`))"
        )
        it.runSql("ALTER TABLE outbox ADD COLUMN blobKey TEXT")
    }

/** 3 → 4: pairs of activities the athlete said not to combine. */
private val MIGRATION_3_4 =
    Migration(3, 4) {
        it.runSql(
            "CREATE TABLE IF NOT EXISTS `combine_dismissals` (`did` TEXT NOT NULL, `uriA` TEXT NOT NULL, `uriB` TEXT NOT NULL, " +
                "PRIMARY KEY(`did`, `uriA`, `uriB`))"
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
        ],
    version = 4,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun keyValue(): KeyValueDao

    abstract fun pendingAuth(): PendingAuthDao

    abstract fun feed(): FeedDao

    abstract fun outbox(): OutboxDao

    abstract fun combineDismissals(): CombineDismissalDao
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
 * Keys are client-generated TIDs so retries are idempotent (`putRecord` overwrites).
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val did: String,
    /** `put` or `delete`. */
    val operation: String,
    val collection: String,
    val rkey: String,
    val recordJson: String?,
    /** Blob to upload first; its ref is written into the record's [blobField]. */
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

@Dao
interface OutboxDao {
    @Insert suspend fun insert(entity: OutboxEntity): Long

    @Query("SELECT * FROM outbox WHERE did = :did ORDER BY id") suspend fun pending(did: String): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox WHERE did = :did") fun count(did: String): Flow<Int>

    @Query("UPDATE outbox SET attempts = attempts + 1, lastError = :error WHERE id = :id") suspend fun markFailed(id: Long, error: String)

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
