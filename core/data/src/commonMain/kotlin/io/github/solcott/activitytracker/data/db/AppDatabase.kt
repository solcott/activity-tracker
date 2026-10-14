package io.github.solcott.activitytracker.data.db

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
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
fun <T : RoomDatabase> RoomDatabase.Builder<T>.addAppMigrations(): RoomDatabase.Builder<T> = addMigrations(MIGRATION_1_2)

/** Runs one statement: `execSQL` blocks on most platforms and suspends on the web, so it has no common form. */
internal expect suspend fun SQLiteConnection.runSql(sql: String)

/** 1 → 2: an outbox write can target one of the athlete's tier spaces. */
private val MIGRATION_1_2 = Migration(1, 2) { it.runSql("ALTER TABLE outbox ADD COLUMN space TEXT") }

@Database(
    entities =
        [
            KeyValueEntity::class,
            PendingAuthEntity::class,
            FeedItemEntity::class,
            OutboxEntity::class,
        ],
    version = 2,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun keyValue(): KeyValueDao

    abstract fun pendingAuth(): PendingAuthDao

    abstract fun feed(): FeedDao

    abstract fun outbox(): OutboxDao
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
)

@Dao
interface OutboxDao {
    @Insert suspend fun insert(entity: OutboxEntity): Long

    @Query("SELECT * FROM outbox WHERE did = :did ORDER BY id") suspend fun pending(did: String): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox WHERE did = :did") fun count(did: String): Flow<Int>

    @Query("UPDATE outbox SET attempts = attempts + 1, lastError = :error WHERE id = :id") suspend fun markFailed(id: Long, error: String)

    @Query("DELETE FROM outbox WHERE id = :id") suspend fun delete(id: Long)
}
