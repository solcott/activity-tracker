package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.GearItems
import io.github.solcott.activitytracker.appview.db.tx
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll

/** V37 fills the gear usage columns V36 added, for rows indexed before them (`gear/gear-usage`). */
class ActivitySummaryBackfillTest {
    private val source = JdbcDataSource().apply { setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1") }

    private fun execute(sql: String) = source.connection.use { it.createStatement().use { s -> s.execute(sql) } }

    @Test
    fun rowsFromBeforeV36GetTheirSummaryAndStartingUsage() {
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("36").load().migrate()
        // 23:30 UTC on the 4th is the 5th at UTC+2.
        val ride =
            """{"sport":"ride","startedAt":"2026-10-04T23:30:00Z","utcOffset":120,"elapsedTime":3700,"movingTime":3600,""" +
                """"distance":40000,"elevationGain":500,"elevationLoss":-3,"createdAt":"2026-10-05T08:00:00Z"}"""
        execute(
            "INSERT INTO activities (uri, cid, author_did, record, sport, started_at, sort_at, indexed_at) VALUES " +
                "('at://did:plc:alice/ride', 'bafy', 'did:plc:alice', '$ride', 'ride', 0, 0, 0), " +
                "('at://did:plc:alice/broken', 'bafy', 'did:plc:alice', '{\"sport\":1}', 'ride', 0, 0, 0)"
        )
        val bike = """{"name":"Tarmac","type":"bike","startingDistance":2000000,"createdAt":"2026-10-01T09:00:00Z"}"""
        execute(
            "INSERT INTO gear_items (uri, origin, cid, author_did, visibility, item_name, item_type, record, indexed_at) VALUES " +
                "('at://did:plc:alice/bike', 'repo', 'bafy', 'did:plc:alice', 'public', 'Tarmac', 'bike', '$bike', 0)"
        )

        DatabaseFactory.migrate(source)

        runBlocking {
            Database.connect(source).tx {
                val row = Activities.selectAll().where { Activities.uri eq "at://did:plc:alice/ride" }.single()
                assertEquals(3700L, row[Activities.elapsedTime])
                assertEquals(3600L, row[Activities.movingTime])
                assertEquals(40_000L, row[Activities.distance])
                assertEquals(500L, row[Activities.elevationGain])
                assertNull(row[Activities.elevationLoss], "a negative value is dropped")
                assertEquals(LocalDate.of(2026, 10, 5).toEpochDay(), row[Activities.startedOn])
                assertEquals(2026, row[Activities.startedYear])
                val broken = Activities.selectAll().where { Activities.uri eq "at://did:plc:alice/broken" }.single()
                assertNull(broken[Activities.startedOn])
                val gear = GearItems.selectAll().single()
                assertEquals(2_000_000L, gear[GearItems.startingDistance])
                assertNull(gear[GearItems.startingTime])
            }
        }
    }
}
