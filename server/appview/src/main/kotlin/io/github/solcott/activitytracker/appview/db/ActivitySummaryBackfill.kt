package io.github.solcott.activitytracker.appview.db

import io.github.solcott.activitytracker.appview.index.ActivitySummary
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types
import org.flywaydb.core.api.MigrationVersion
import org.flywaydb.core.api.migration.Context
import org.flywaydb.core.api.migration.JavaMigration

/**
 * V37: fills the columns V36 added from each row's stored record, which SQL can't read portably (H2 and Postgres parse JSON differently).
 * Rows indexed after V36 get them from the indexer. A record that no longer decodes keeps nulls, which the totals treat as nothing.
 */
internal object ActivitySummaryBackfill : JavaMigration {
    private const val BATCH = 500

    override fun getVersion(): MigrationVersion = MigrationVersion.fromVersion("37")

    override fun getDescription(): String = "backfill activity summary"

    override fun getChecksum(): Int? = null

    override fun canExecuteInTransaction(): Boolean = true

    override fun migrate(context: Context) {
        val connection = context.connection
        backfillActivities(connection)
        backfillGear(connection)
    }

    private fun backfillActivities(connection: Connection) {
        val sql =
            "UPDATE activities SET elapsed_time = ?, moving_time = ?, distance = ?, elevation_gain = ?, elevation_loss = ?, " +
                "dive_bottom_time = ?, started_on = ?, started_year = ? WHERE uri = ?"
        connection.prepareStatement(sql).use { update ->
            update.eachRecord(connection, "SELECT uri, record FROM activities") { uri, json ->
                val summary = runCatching { ActivitySummary.of(AtJson.decodeFromString(ActivityRecord.serializer(), json)) }.getOrNull()
                if (summary != null) {
                    with(summary) {
                        update.bind(
                            elapsedTime,
                            movingTime,
                            distance,
                            elevationGain,
                            elevationLoss,
                            diveBottomTime,
                            startedOn,
                            startedYear,
                            uri,
                        )
                    }
                    update.addBatch()
                }
                summary != null
            }
            update.executeBatch()
        }
    }

    private fun backfillGear(connection: Connection) {
        connection.prepareStatement("UPDATE gear_items SET starting_distance = ?, starting_time = ? WHERE uri = ?").use { update ->
            update.eachRecord(connection, "SELECT uri, record FROM gear_items") { uri, json ->
                val record = runCatching { AtJson.decodeFromString(GearItemRecord.serializer(), json) }.getOrNull()
                val distance = record?.startingDistance?.takeIf { it >= 0 }
                val time = record?.startingTime?.takeIf { it >= 0 }
                if (distance != null || time != null) {
                    update.bind(distance, time, uri)
                    update.addBatch()
                }
                distance != null || time != null
            }
            update.executeBatch()
        }
    }

    /** Calls [row] with each row's uri and record; every [BATCH] rows it queued (returned true for), runs [update]'s batch. */
    private inline fun PreparedStatement.eachRecord(connection: Connection, select: String, row: (String, String) -> Boolean) {
        var queued = 0
        connection.createStatement().use { statement ->
            statement.executeQuery(select).use { rows ->
                while (rows.next()) {
                    if (row(rows.getString(1), rows.getString(2)) && ++queued % BATCH == 0) executeBatch()
                }
            }
        }
    }

    /** Sets the statement's parameters, in order, to [values]: each a string, an int, or a long or null. */
    private fun PreparedStatement.bind(vararg values: Any?) {
        values.forEachIndexed { i, value ->
            val index = i + 1
            when (value) {
                null -> setNull(index, Types.BIGINT)
                is String -> setString(index, value)
                is Int -> setInt(index, value)
                is Long -> setLong(index, value)
                else -> error("Unsupported parameter ${value::class}")
            }
        }
    }
}
