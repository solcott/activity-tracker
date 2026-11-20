package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.admin.CachedDatabaseSizes
import io.github.solcott.activitytracker.appview.admin.DatabaseSizes
import io.github.solcott.activitytracker.appview.admin.HealthSources
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.ops.WorkerRegistry
import io.github.solcott.activitytracker.appview.ops.WorkerSupervisor
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TestTimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert

/** The health page's reads (admin/ingest-health): the space syncer's summary and the cached database sizes. */
class HealthSourcesTest {
    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val clock = MutableClock(Instant.parse("2026-11-18T12:00:00Z"))
    private val now = clock.now.toEpochMilliseconds()

    private val sources =
        HealthSources(
            WorkerSupervisor(CoroutineScope(SupervisorJob()), WorkerRegistry()),
            StartedWorkers(null, emptyList()),
            db,
            24.hours,
            syncing = true,
            clock = clock,
        )

    private fun seed(name: String, failures: Int, lastErrorAt: Long? = null, notFoundSince: Long? = null, nextAttemptAt: Long = now + 1) =
        runBlocking {
            db.tx {
                SpaceSync.insert {
                    it[space] = "at://did:plc:$name/app.activitytracker.space.athlete/self"
                    it[authorityDid] = "did:plc:$name"
                    it[SpaceSync.failures] = failures
                    it[lastError] = "IOException: $name".takeIf { failures > 0 }
                    it[SpaceSync.lastErrorAt] = lastErrorAt
                    it[SpaceSync.notFoundSince] = notFoundSince
                    it[SpaceSync.nextAttemptAt] = nextAttemptAt
                    it[syncedAt] = now - 1
                    it[createdAt] = 0
                }
            }
        }

    @Test
    fun spaceCountsAndTheMostFailingSpacesFirst() = runBlocking {
        seed("healthy", failures = 0)
        seed("due", failures = 0, nextAttemptAt = now)
        seed("failing", failures = 2, lastErrorAt = now - 10, nextAttemptAt = now - 5)
        seed("worse", failures = 5, lastErrorAt = now - 20)
        seed("recent", failures = 2, lastErrorAt = now - 1)
        seed("missing", failures = 1, lastErrorAt = now - 30, notFoundSince = now - 60)
        assertEquals(HealthSources.SpaceCounts(synced = 6, failing = 4, notFound = 1, due = 2, failingOther = 3), sources.spaceCounts())
        val failing = sources.failingSpaces()
        assertEquals(listOf("did:plc:worse", "did:plc:recent", "did:plc:failing", "did:plc:missing"), failing.map { it.authorityDid })
        assertEquals("IOException: worse", failing.first().lastError)
        assertEquals(Instant.fromEpochMilliseconds(now - 20), failing.first().lastErrorAt)
        assertEquals(listOf("did:plc:worse"), sources.failingSpaces(limit = 1).map { it.authorityDid })
    }

    @Test fun noSpacesCountsZero() = runBlocking { assertEquals(HealthSources.SpaceCounts(0, 0, 0, 0), sources.spaceCounts()) }

    @Test
    fun sizesAreReadAtMostOnceAMinute() = runBlocking {
        var reads = 0
        val time = TestTimeSource()
        val cached =
            CachedDatabaseSizes(
                {
                    reads++
                    DatabaseSizes.Sizes(reads.toLong(), emptyList())
                },
                timeSource = time,
            )
        repeat(10) {
            cached.read()
            time += 5.seconds
        }
        assertEquals(1, reads)
        time += 20.seconds
        assertEquals(2L, cached.read()?.databaseBytes)
        assertEquals(2, reads)
    }

    @Test
    fun h2InPostgresModeHasNoSizes() {
        assertEquals(DatabaseSizes.Unavailable, DatabaseSizes.of(db))
    }
}
