package io.github.solcott.activitytracker.appview.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

object DatabaseFactory {
    fun connect(url: String, user: String, password: String): Database {
        val dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = url
                    username = user
                    this.password = password
                    maximumPoolSize = 10
                }
            )
        migrate(dataSource)
        return Database.connect(dataSource)
    }

    /**
     * Runs the portable migrations, plus `db/vendor/<database>` (e.g. `postgresql`) for SQL only that database can run, and the Kotlin ones
     * (backfills from record JSON).
     */
    fun migrate(dataSource: DataSource) {
        val vendor = dataSource.connection.use { it.metaData.databaseProductName.lowercase() }
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration", "classpath:db/vendor/$vendor")
            .javaMigrations(ActivitySummaryBackfill)
            .load()
            .migrate()
    }
}

suspend fun <T> Database.tx(block: JdbcTransaction.() -> T): T = suspendTransaction(this) { block() }
