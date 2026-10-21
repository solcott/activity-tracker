package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.ModerationAccessLog
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.ModerationAccess
import io.github.solcott.activitytracker.appview.db.tx
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll

/** `Every moderator view is audit-logged` (`moderation/private-subjects`): entries are kept at least a year, then pruned. */
class ModerationAccessLogTest {
    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private var now = Instant.parse("2026-10-21T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = now
        }
    private val log = ModerationAccessLog(db, clock)

    private suspend fun subjects() = db.tx { ModerationAccess.selectAll().map { it[ModerationAccess.subjectUri] }.sorted() }

    @Test
    fun entriesOlderThan400DaysArePruned() = runTest {
        log.record("did:plc:mod", "at://did:plc:bob/app.activitytracker.feed.activity/old", "7")
        now += 30.days
        log.record("did:plc:mod", "at://did:plc:bob/app.activitytracker.feed.activity/new", "8")

        now += 365.days
        assertEquals(0, log.prune(), "395 days on, both stay: a year is the minimum")

        now += 6.days
        assertEquals(1, log.prune())
        assertEquals(listOf("at://did:plc:bob/app.activitytracker.feed.activity/new"), subjects())
    }

    @Test
    fun entriesHoldNoContent() = runTest {
        log.record("did:plc:mod", "at://did:plc:bob/app.activitytracker.feed.activity/3k", "12")
        val row = db.tx { ModerationAccess.selectAll().single() }
        assertEquals("did:plc:mod", row[ModerationAccess.moderatorDid])
        assertEquals("12", row[ModerationAccess.reportRef])
        assertEquals(now.toEpochMilliseconds(), row[ModerationAccess.at])
    }
}
