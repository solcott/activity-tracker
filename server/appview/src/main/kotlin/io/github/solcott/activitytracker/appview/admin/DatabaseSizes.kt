package io.github.solcott.activitytracker.appview.admin

import io.github.solcott.activitytracker.appview.db.tx
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.jdbc.Database

/** The database's size and its tables', where the database reports them (admin/ingest-health). */
fun interface DatabaseSizes {
    /** Table sizes include their indexes; [Table.estimatedRows] is the planner's estimate, null before the table was first analysed. */
    data class Table(val name: String, val bytes: Long, val estimatedRows: Long?)

    data class Sizes(val databaseBytes: Long, val tables: List<Table>)

    /** The sizes, largest table first, or null when the database doesn't report them. */
    suspend fun read(): Sizes?

    /** A database that reports no sizes. */
    object Unavailable : DatabaseSizes {
        override suspend fun read(): Sizes? = null
    }

    companion object {
        /** Postgres's catalog, read at most once a minute, for a Postgres [db]; [Unavailable] for any other (H2 included). */
        fun of(db: Database): DatabaseSizes =
            if (db.dialect is PostgreSQLDialect) CachedDatabaseSizes(PostgresDatabaseSizes(db)) else Unavailable
    }
}

/** Postgres's catalog statistics: no table is scanned. */
class PostgresDatabaseSizes(private val db: Database) : DatabaseSizes {
    override suspend fun read(): DatabaseSizes.Sizes = db.tx {
        val total = exec("select pg_database_size(current_database())") { rs -> if (rs.next()) rs.getLong(1) else 0L } ?: 0L
        val tables =
            exec(
                "select c.relname, pg_total_relation_size(c.oid), c.reltuples from pg_class c " +
                    "join pg_namespace n on n.oid = c.relnamespace where n.nspname = current_schema() and c.relkind = 'r' order by 2 desc",
            ) { rs ->
                buildList {
                    while (rs.next()) {
                        val rows = rs.getDouble(3)
                        add(DatabaseSizes.Table(rs.getString(1), rs.getLong(2), rows.takeIf { it >= 0 }?.toLong()))
                    }
                }
            }
        DatabaseSizes.Sizes(total, tables.orEmpty())
    }
}

/** Keeps [delegate]'s last read for [maxAge], so however often the page loads, the database is asked at most that often. */
class CachedDatabaseSizes(
    private val delegate: DatabaseSizes,
    private val maxAge: Duration = 1.minutes,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : DatabaseSizes {
    private val lock = Mutex()
    private var readAt: TimeMark? = null
    private var last: DatabaseSizes.Sizes? = null

    override suspend fun read(): DatabaseSizes.Sizes? = lock.withLock {
        if (readAt?.let { it.elapsedNow() < maxAge } != true) {
            last = delegate.read()
            readAt = timeSource.markNow()
        }
        last
    }
}
