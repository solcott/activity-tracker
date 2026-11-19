package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import java.util.UUID
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * A fresh, migrated in-memory H2 database in PostgreSQL mode. `DB_CLOSE_DELAY=-1` keeps it while no connection is open, so a test that
 * makes one calls [close] after it: otherwise every test's database stays in the test JVM's heap until the run ends.
 */
class TestH2 {
    val dataSource =
        JdbcDataSource().apply {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
        }
    val db: Database = Database.connect(dataSource)

    /** Drops the database. */
    fun close() {
        dataSource.connection.use { it.createStatement().execute("SHUTDOWN") }
    }
}
