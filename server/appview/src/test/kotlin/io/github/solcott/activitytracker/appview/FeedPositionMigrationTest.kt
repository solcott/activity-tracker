package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivitySummaryBackfill
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.tx
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll

/** V46 gives rows indexed before it their feed position (`feed/timeline-order`), without re-indexing them. */
class FeedPositionMigrationTest {
    private val source = JdbcDataSource().apply { setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1") }

    private fun execute(sql: String) = source.connection.use { it.createStatement().use { s -> s.execute(sql) } }

    private fun migrateTo45() =
        Flyway.configure()
            .dataSource(source)
            .locations("classpath:db/migration")
            .javaMigrations(ActivitySummaryBackfill)
            .target("45")
            .load()
            .migrate()

    @Test
    fun anEarlierImportSinksBelowARecentActivity() {
        migrateTo45()
        val day = 86_400_000L
        val posted = 1_000 * day
        // Both posted at the same time: one ended an hour before, one a year before, one has no elapsed time (from before V36).
        execute(
            "INSERT INTO activities (uri, cid, author_did, record, sport, started_at, sort_at, indexed_at, elapsed_time) VALUES " +
                "('at://did:plc:alice/recent', 'bafy', 'did:plc:alice', '{}', 'ride', ${posted - 7_200_000}, $posted, $posted, 3600), " +
                "('at://did:plc:alice/old', 'bafy', 'did:plc:alice', '{}', 'ride', ${posted - 365 * day}, ${posted + 1}, $posted, 3600), " +
                "('at://did:plc:alice/legacy', 'bafy', 'did:plc:alice', '{}', 'ride', ${posted - 400 * day}, $posted, $posted, NULL)",
        )

        DatabaseFactory.migrate(source)

        runBlocking {
            Database.connect(source).tx {
                val rows =
                    Activities.selectAll().orderBy(Activities.sortAt to SortOrder.DESC).associateBy {
                        it[Activities.uri].substringAfterLast('/')
                    }
                assertEquals(listOf("recent", "old", "legacy"), rows.keys.toList())
                val old = rows.getValue("old")
                assertEquals(posted + 1, old[Activities.postedAt], "the post time is the old sort position")
                assertEquals(posted - 365 * day + 3_600_000, old[Activities.endedAt])
                assertEquals(posted - 360 * day + 3_600_000, old[Activities.sortAt])
                assertEquals(posted, rows.getValue("recent")[Activities.sortAt])
                assertEquals(posted - 395 * day, rows.getValue("legacy")[Activities.sortAt], "no elapsed time ends at the start")
                assertNull(old[Activities.importBatch])
            }
        }
    }

    @Test
    fun anElapsedTimeTooLongToAddIsHeldToAYear() {
        migrateTo45()
        execute(
            "INSERT INTO activities (uri, cid, author_did, record, sport, started_at, sort_at, indexed_at, elapsed_time) VALUES " +
                "('at://did:plc:alice/endless', 'bafy', 'did:plc:alice', '{}', 'ride', 0, 0, 0, ${Long.MAX_VALUE}), " +
                "('at://did:plc:alice/negative', 'bafy', 'did:plc:alice', '{}', 'ride', 0, 0, 0, -5)",
        )

        DatabaseFactory.migrate(source)

        runBlocking {
            Database.connect(source).tx {
                val ended = Activities.selectAll().associate { it[Activities.uri].substringAfterLast('/') to it[Activities.endedAt] }
                assertEquals(mapOf("endless" to 31_536_000_000L, "negative" to 0L), ended)
            }
        }
    }
}
