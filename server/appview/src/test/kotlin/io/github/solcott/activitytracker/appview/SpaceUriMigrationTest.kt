package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityDetails
import io.github.solcott.activitytracker.appview.db.ActivitySummaryBackfill
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll

/** V79 moves stored space URIs to the 2026-11-24 alpha's `at:space/` form, touching only old-form values. */
class SpaceUriMigrationTest {
    private val source = JdbcDataSource().apply { setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1") }

    private fun execute(sql: String) = source.connection.use { it.createStatement().use { s -> s.execute(sql) } }

    private fun migrateTo78() =
        Flyway.configure()
            .dataSource(source)
            .locations("classpath:db/migration")
            .javaMigrations(ActivitySummaryBackfill)
            .target("78")
            .load()
            .migrate()

    @Test
    fun oldFormSpaceUrisAreRewrittenAndOthersKept() {
        migrateTo78()
        val old = "at://did:plc:alice/space/${Nsid.SPACE_ATHLETE}/followers"
        val followers = SpaceUri.of(Did("did:plc:alice"), Nsid.SPACE_ATHLETE, "followers")
        val mutuals = SpaceUri.of(Did("did:plc:alice"), Nsid.SPACE_ATHLETE, "mutuals")
        execute(
            "INSERT INTO space_sync (space_uri, authority_did, notify_token, next_attempt_at, created_at) VALUES " +
                "('$old', 'did:plc:alice', 'token', 0, 0), ('${mutuals.value}', 'did:plc:alice', NULL, 0, 0)",
        )
        execute(
            "INSERT INTO activities (uri, cid, author_did, record, sport, started_at, sort_at, posted_at, ended_at, indexed_at, origin) " +
                "VALUES " +
                "('at://did:plc:alice/a/1', 'bafy', 'did:plc:alice', '{}', 'ride', 0, 0, 0, 0, 0, '$old'), " +
                "('at://did:plc:alice/a/2', 'bafy', 'did:plc:alice', '{}', 'ride', 0, 0, 0, 0, 0, 'repo')",
        )
        execute(
            "INSERT INTO activity_details (uri, origin, cid, author_did, activity_uri, visibility, record, indexed_at) VALUES " +
                "('at://did:plc:alice/d/1', '$old', 'bafy', 'did:plc:alice', 'at://did:plc:alice/a/1', 'followers', '{}', 0)",
        )

        DatabaseFactory.migrate(source)

        runBlocking {
            Database.connect(source).tx {
                val spaces = SpaceSync.selectAll().associate { SpaceUri(it[SpaceSync.space]) to it[SpaceSync.notifyToken] }
                assertEquals(mapOf(followers to "token", mutuals to null), spaces, "the token stays with its space")
                val origins = Activities.selectAll().associate { it[Activities.uri].substringAfterLast('/') to it[Activities.origin] }
                assertEquals(mapOf("1" to followers.value, "2" to "repo"), origins)
                assertEquals(listOf(followers.value), ActivityDetails.selectAll().map { it[ActivityDetails.origin] })
            }
        }
    }

    @Test
    fun everyOriginTableIsRewritten() {
        migrateTo78()
        val old = "at://did:plc:alice/space/${Nsid.SPACE_ATHLETE}/followers"
        val followers = SpaceUri.of(Did("did:plc:alice"), Nsid.SPACE_ATHLETE, "followers")
        ORIGIN_TABLES.forEach { insertWithOrigin(it, old) }

        DatabaseFactory.migrate(source)

        val origins = ORIGIN_TABLES.associateWith { table -> query("SELECT origin FROM $table") }
        assertEquals(ORIGIN_TABLES.associateWith { listOf(followers.value) }, origins)
    }

    /** One row of [table] holding [origin], with every other required column given a placeholder of its type. */
    private fun insertWithOrigin(table: String, origin: String) {
        val required =
            source.connection.use { connection ->
                connection
                    .prepareStatement(
                        "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = ? AND is_nullable = 'NO' " +
                            "AND column_default IS NULL AND column_name <> 'ORIGIN'",
                    )
                    .use { statement ->
                        statement.setString(1, table.uppercase())
                        statement.executeQuery().use { rows ->
                            buildList { while (rows.next()) add(rows.getString(1) to rows.getString(2)) }
                        }
                    }
            }
        val columns = required.map { it.first } + "ORIGIN"
        val values = required.map { (_, type) -> placeholder(type) } + "'$origin'"
        execute("INSERT INTO $table (${columns.joinToString()}) VALUES (${values.joinToString()})")
    }

    private fun placeholder(type: String): String =
        when (type) {
            "BIGINT",
            "INTEGER",
            "SMALLINT",
            "TINYINT",
            "DOUBLE PRECISION",
            "REAL",
            "NUMERIC" -> "0"
            "BOOLEAN" -> "FALSE"
            "BINARY VARYING",
            "BINARY LARGE OBJECT" -> "X'00'"
            else -> "'x'"
        }

    private fun query(sql: String): List<String> =
        source.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
            }
        }

    private companion object {
        /** Every table V79 rewrites the `origin` of. */
        val ORIGIN_TABLES =
            listOf(
                "activities",
                "posts",
                "likes",
                "reposts",
                "comments",
                "comment_hides",
                "blocks",
                "mutes",
                "activity_streams",
                "activity_details",
                "activity_zones",
                "gear_items",
                "activity_gear",
                "activity_stream_fetch_failures",
                "record_shadows",
            )
    }
}
