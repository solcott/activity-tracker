package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.atproto.xrpc.RateLimit
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.BulkWrites
import io.github.solcott.activitytracker.data.activity.OutboxPacing
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Paced uploads (data/outbox): a batch import's writes are bulk and go after the athlete's own, a 429 pauses the outbox until the PDS says,
 * and bulk writes leave a tenth of the limit for the athlete.
 */
class OutboxPacingTest {
    private val pds = FakeAthletePds()
    private val start = Instant.parse("2026-10-14T07:00:00Z")
    private val clock = TestClock(Instant.parse("2026-10-30T14:00:00Z"))

    private class TestClock(var now: Instant) : Clock {
        override fun now() = now
    }

    private fun draft(minute: Int) =
        ActivityDraft(
            type = ActivityType(Sport.RUN),
            title = "Run",
            description = null,
            startedAt = start + minute.minutes,
            elapsed = 30.minutes,
            gpx = "<gpx/>".encodeToByteArray(),
        )

    private suspend fun OutboxWriter.put(collection: String, rkey: String) =
        put(collection, rkey, JsonObject.serializer(), buildJsonObject { put("\$type", collection) })

    private suspend fun FakeAthletePds.Session.rows() = db.outbox().pending(pds.did.value)

    @Test
    fun aBatchsWritesAreBulkAndASinglePublishsAreNot() = runTest {
        val session = pds.session()
        val uris = session.activities.publishBatch(flowOf(draft(0), draft(60)), drain = false)
        val batch = session.rows()
        assertEquals(uris.map { it.rkey }.toSet(), batch.map { it.rkey }.toSet())
        assertTrue(batch.size > 2, "each activity queues several records")
        assertTrue(batch.all { it.bulk }, "every write of a batch is bulk")

        pds.offline = true
        val single = session.activities.publish(draft(120))
        assertTrue(session.rows().filter { it.rkey == single.rkey }.none { it.bulk })
    }

    @Test
    fun theAthletesOwnWritesGoFirstExceptForARecordStillUploading() = runTest {
        val session = pds.session()
        val writer = OutboxWriter(session.db, session.clients, Clock.System)
        val rkeys = (1..50).map { "3kimport${it.toString().padStart(2, '0')}" }
        BulkWrites.run {
            for (rkey in rkeys) {
                writer.put(Nsid.FEED_ACTIVITY_DETAILS, rkey)
                writer.put(Nsid.FEED_ACTIVITY, rkey)
            }
        }
        writer.put(Nsid.FEED_LIKE, "3klike")
        writer.put(Nsid.FEED_ACTIVITY, rkeys[9])

        assertEquals(0, session.outbox.drain())

        assertEquals("put repo ${Nsid.FEED_LIKE}/3klike", pds.writes.first(), "the like goes before the import")
        val tenth = pds.writes.withIndex().filter { it.value.endsWith("/${rkeys[9]}") }.map { it.index }
        assertEquals(3, tenth.size)
        assertEquals(tenth.last(), pds.writes.lastIndex, "the edit keeps its place behind the activity's own writes")
    }

    @Test
    fun twoSessionsOfOneAccountNeverSendAWriteTwice() = runTest {
        val pacing = OutboxPacing()
        val session = pds.session(pacing = pacing)
        // A background job's session: its own clients over the same database, sharing the account's pacing.
        val other = signedInTestSession(pds.http, did = pds.did, pacing = pacing)
        val writer = OutboxWriter(session.db, session.clients, Clock.System)
        repeat(40) { writer.put(Nsid.FEED_LIKE, "3klike$it") }

        @Suppress("InjectDispatcher") // Real threads, so the two drains race for the lock.
        withContext(Dispatchers.Default) {
            listOf(session.outbox, OutboxProcessor(session.db, other.clients, Logger)).map { outbox -> async { outbox.drain() } }.awaitAll()
        }

        assertEquals(40, pds.writes.size)
        assertEquals(pds.writes.toSet().size, pds.writes.size, "each write is sent once")
    }

    @Test
    fun aRateLimitPausesTheOutboxWithoutCountingAnAttempt() = runTest {
        val reset = clock.now + 1.hours
        pds.rateLimit = FakeRateLimit(limit = 4, resetAt = reset, now = { clock.now() })
        val session = pds.session(clock = clock)
        val writer = OutboxWriter(session.db, session.clients, clock)
        repeat(4) { writer.put(Nsid.FEED_LIKE, "3klike$it") }

        assertEquals(2, session.outbox.drain())
        assertEquals(2, pds.writes.size)
        assertEquals(1, pds.rateLimited)
        assertEquals(listOf(0, 0), session.rows().map { it.attempts }, "a 429 isn't a failed attempt")
        assertEquals(reset, session.clients.pacing.pausedUntil.value)

        val requests = pds.requests.size
        assertEquals(2, session.outbox.drain())
        assertEquals(requests, pds.requests.size, "nothing is sent while the pause lasts")

        clock.now = reset + 1.minutes
        assertEquals(0, session.outbox.drain())
        assertEquals(4, pds.writes.size)
    }

    @Test
    fun bulkWritesLeaveATenthOfTheLimitForTheAthlete() = runTest {
        val reset = clock.now + 1.hours
        pds.rateLimit = FakeRateLimit(limit = 5000, resetAt = reset, now = { clock.now() }).apply { remaining = 402 }
        val session = pds.session(clock = clock)
        val writer = OutboxWriter(session.db, session.clients, clock)
        BulkWrites.run { repeat(3) { writer.put(Nsid.FEED_ACTIVITY, "3kimport$it") } }
        writer.put(Nsid.FEED_COMMENT, "3kcomment")

        assertEquals(3, session.outbox.drain())

        assertEquals(listOf("put repo ${Nsid.FEED_COMMENT}/3kcomment"), pds.writes, "the comment goes; the import waits")
        assertEquals(reset, session.clients.pacing.bulkPausedUntil.value)
        assertEquals(0, pds.rateLimited)

        clock.now = reset + 1.minutes
        assertEquals(0, session.outbox.drain())
        assertEquals(4, pds.writes.size)
    }

    @Test
    fun aLowBlobLimitHoldsBulkWritesThoughRecordWritesHaveRoom() = runTest {
        val session = pds.session(clock = clock)
        val writer = OutboxWriter(session.db, session.clients, clock)
        BulkWrites.run { writer.put(Nsid.FEED_ACTIVITY, "3kimport") }
        val tomorrow = clock.now + 20.hours
        // A blob upload stated the daily blob limit nearly spent; a record put since stated the hourly record limit with room to spare.
        session.clients.pacing.heard(RateLimit(1000, 40, tomorrow, "1000;w=86400"))
        session.clients.pacing.heard(RateLimit(5000, 4800, clock.now + 30.minutes, "5000;w=3600"))

        assertEquals(1, session.outbox.drain())

        assertEquals(emptyList(), pds.writes)
        assertEquals(tomorrow, session.clients.pacing.bulkPausedUntil.value)
    }
}
