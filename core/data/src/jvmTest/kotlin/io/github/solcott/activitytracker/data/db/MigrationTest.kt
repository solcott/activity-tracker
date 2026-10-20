package io.github.solcott.activitytracker.data.db

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A database written by an older version of the app opens with today's schema through [addAppMigrations], keeping its rows. */
class MigrationTest {
    @Test
    fun version1OutboxRowsSurviveAsRepoWrites() = runTest {
        val file = File(createTempDirectory("migration").toFile(), DATABASE_NAME)
        createVersion1(file)

        val db = Room.databaseBuilder<AppDatabase>(file.path).addAppMigrations().setDriver(BundledSQLiteDriver()).build()
        val pending = db.outbox().pending("did:plc:alice")
        assertEquals(listOf("3kold"), pending.map { it.rkey })
        assertEquals(null, pending.single().space, "a write from before spaces goes to the repo")
        db.close()
    }

    @Test
    fun version2OutboxRowsSurviveWithoutASharedBlob() = runTest {
        val file = File(createTempDirectory("migration").toFile(), DATABASE_NAME)
        createVersion(file, 2)

        val db = Room.databaseBuilder<AppDatabase>(file.path).addAppMigrations().setDriver(BundledSQLiteDriver()).build()
        val pending = db.outbox().pending("did:plc:alice")
        assertEquals(listOf("3kold"), pending.map { it.rkey })
        assertEquals(null, pending.single().blobKey)
        assertEquals(emptyList(), db.outbox().blobs())
        db.close()
    }

    @Test
    fun version3GainsAnEmptyDismissalsTable() = runTest {
        val file = File(createTempDirectory("migration").toFile(), DATABASE_NAME)
        createVersion(file, 3)

        val db = Room.databaseBuilder<AppDatabase>(file.path).addAppMigrations().setDriver(BundledSQLiteDriver()).build()
        assertEquals(listOf("3kold"), db.outbox().pending("did:plc:alice").map { it.rkey })
        assertEquals(false, db.combineDismissals().dismissed("did:plc:alice", "at://a", "at://b"))
        db.close()
    }

    private fun createVersion1(file: File) = createVersion(file, 1)

    /** The [version] schema, from the committed schema export, with one queued write. */
    private fun createVersion(file: File, version: Int) {
        val schema = File("schemas/io.github.solcott.activitytracker.data.db.AppDatabase/$version.json").readText()
        val database = Json.parseToJsonElement(schema).jsonObject.getValue("database").jsonObject
        val connection = BundledSQLiteDriver().open(file.path)
        database.getValue("entities").jsonArray.forEach { entity ->
            val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
            connection.execSQL(entity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
        }
        database.getValue("setupQueries").jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
        connection.execSQL(
            "INSERT INTO outbox (did, operation, collection, rkey, recordJson, attempts, createdAt) " +
                "VALUES ('did:plc:alice', 'put', 'app.activitytracker.feed.activity', '3kold', '{}', 0, 0)"
        )
        connection.execSQL("PRAGMA user_version = $version")
        connection.close()
    }
}
