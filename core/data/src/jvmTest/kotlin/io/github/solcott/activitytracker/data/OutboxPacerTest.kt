package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.activity.BulkWrites
import io.github.solcott.activitytracker.data.activity.OutboxPacer
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.lexicon.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** [OutboxPacer] sends queued writes on its own (data/outbox), across rate-limit pauses and dropped connections, in virtual time. */
class OutboxPacerTest {
    private val base = Instant.parse("2026-10-30T14:00:00Z")

    /** The test's virtual time, as a clock. */
    private fun TestScope.clock() =
        object : Clock {
            override fun now() = base + testScheduler.currentTime.milliseconds
        }

    private class Pacing(val pds: FakeAthletePds, val session: FakeAthletePds.Session, val pacer: OutboxPacer, val writer: OutboxWriter)

    /** A fake PDS and session whose network and database run on the test's dispatcher, so every wait is in virtual time. */
    private suspend fun TestScope.pacing(limit: (Clock) -> FakeRateLimit? = { null }): Pacing {
        val clock = clock()
        val pds = FakeAthletePds(dispatcher = StandardTestDispatcher(testScheduler)).apply { rateLimit = limit(clock) }
        val session = pds.session(clock = clock)
        return Pacing(
            pds,
            session,
            OutboxPacer(session.db, session.clients, session.activities, clock, Logger),
            OutboxWriter(session.db, session.clients, clock),
        )
    }

    private suspend fun OutboxWriter.put(collection: String, rkey: String) =
        put(collection, rkey, JsonObject.serializer(), buildJsonObject { put("\$type", collection) })

    private suspend fun Pacing.awaitQueued(size: Int) = session.db.outbox().queueState(pds.did.value).first { it.size == size }

    @Test
    fun aLargeImportIsSentAcrossManyHoursOfLimits() = runTest {
        val p = pacing { FakeRateLimit(limit = 1000, resetAt = base + 1.hours, now = it::now) }
        BulkWrites.run { repeat(2000) { p.writer.put(Nsid.FEED_ACTIVITY, "3kimport$it") } }

        val job = backgroundScope.launch { p.pacer.run() }
        p.awaitQueued(0)
        job.cancel()

        assertEquals(2000, p.pds.writes.size)
        assertEquals(0, p.pds.rateLimited, "bulk writes stop short of the limit, so the PDS never has to refuse one")
        assertTrue(clock().now() >= base + 4.hours, "2,000 puts at 1,000 points an hour take more than four hours")
    }

    @Test
    fun aWriteQueuedWhileIdleIsSentWithoutAsking() = runTest {
        val p = pacing()
        backgroundScope.launch { p.pacer.run() }
        p.awaitQueued(0)

        p.writer.put(Nsid.FEED_LIKE, "3klike")
        p.awaitQueued(0)

        assertEquals(listOf("put repo ${Nsid.FEED_LIKE}/3klike"), p.pds.writes)
    }

    @Test
    fun anUploadStoppedOfflineGoesOnOnceTheConnectionIsBack() = runTest {
        val p = pacing()
        p.pds.pdsUnreachable = true
        p.writer.put(Nsid.FEED_LIKE, "3klike")
        backgroundScope.launch { p.pacer.run() }
        while (p.session.db.outbox().pending(p.pds.did.value).single().attempts == 0) delay(1.seconds)

        p.pds.pdsUnreachable = false
        p.awaitQueued(0)

        assertEquals(1, p.pds.writes.size)
        assertTrue(clock().now() - base <= OutboxPacer.MAX_BACKOFF, "it tries again within the longest backoff")
    }

    @Test
    fun stoppingLeavesWhatsLeftQueued() = runTest {
        val p = pacing { FakeRateLimit(limit = 4, resetAt = base + 1.hours, now = it::now) }
        repeat(5) { p.writer.put(Nsid.FEED_LIKE, "3klike$it") }

        val job = backgroundScope.launch { p.pacer.run() }
        while (p.pds.rateLimited == 0) delay(1.seconds)
        job.cancel()

        assertEquals(2, p.pds.writes.size)
        assertEquals(3, p.session.db.outbox().pending(p.pds.did.value).size)
    }
}
