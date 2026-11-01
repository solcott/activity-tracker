package io.github.solcott.activitytracker.data.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The outbox's observed queries, and those the drain runs after each write, read indexes rather than scan the table ([OutboxEntity]): Room
 * re-runs them after every write, and a batch import's queue holds thousands of rows with their blob bytes. The SQL mirrors [OutboxDao]'s.
 */
class OutboxQueryPlanTest {
    private val connection: SQLiteConnection = BundledSQLiteDriver().open(":memory:").also(::createSchema)

    @AfterTest fun close() = connection.close()

    @Test
    fun theCountsReadAnIndexOnly() {
        assertIndexed("SELECT COUNT(*) FROM outbox WHERE did = 'd'", covering = true)
        assertIndexed("SELECT COUNT(*) AS size, MAX(id) AS latest FROM outbox WHERE did = 'd'", covering = true)
        assertIndexed(
            "SELECT COUNT(*) FROM outbox WHERE did = 'd' AND rejectedAt IS NULL " +
                "AND rkey NOT IN (SELECT rkey FROM outbox WHERE did = 'd' AND rejectedAt IS NOT NULL)",
            covering = true,
        )
    }

    @Test
    fun theRejectedListReadsOnlyRejectedWritesAndTheirRkeys() {
        assertIndexed(
            "SELECT o.rkey, o.collection, o.operation, o.rejectedAt, " +
                "(SELECT a.recordJson FROM outbox a WHERE a.did = o.did AND a.rkey = o.rkey AND a.collection = 'c' " +
                "AND a.operation = 'put' ORDER BY a.id DESC LIMIT 1) AS activityJson " +
                "FROM outbox o WHERE o.did = 'd' AND o.rejectedAt IS NOT NULL ORDER BY o.id"
        )
    }

    @Test
    fun sweepingSharedBlobsReadsAnIndexOfTheQueue() {
        assertIndexed("DELETE FROM outbox_blob WHERE `key` NOT IN (SELECT blobKey FROM outbox WHERE blobKey IS NOT NULL)", covering = true)
    }

    /** [sql] never scans the outbox, and with [covering] reads none of its rows. */
    private fun assertIndexed(sql: String, covering: Boolean = false) {
        val plan = buildList { connection.prepare("EXPLAIN QUERY PLAN $sql").use { while (it.step()) add(it.getText(3)) } }
        val outbox = plan.filter { Regex("""\b(SCAN|SEARCH) (outbox|o|a)\b""").containsMatchIn(it) }
        assertTrue(outbox.isNotEmpty() && outbox.none { it.startsWith("SCAN") }, "scans the outbox: $plan")
        if (covering) assertTrue(outbox.all { "COVERING INDEX" in it }, "reads outbox rows: $plan")
    }

    /** Today's schema, from the committed export Room checks it against. */
    private fun createSchema(connection: SQLiteConnection) {
        val schema = File("schemas/io.github.solcott.activitytracker.data.db.AppDatabase/8.json").readText()
        val database = Json.parseToJsonElement(schema).jsonObject.getValue("database").jsonObject
        database.getValue("entities").jsonArray.forEach { entity ->
            val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
            connection.execSQL(entity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                connection.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
    }
}
