package io.github.solcott.activitytracker.appview.ops

import io.github.solcott.activitytracker.appview.MutableClock
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class WorkerRegistryTest {
    private val clock = MutableClock(Instant.parse("2026-11-18T12:00:00Z"))
    private val registry = WorkerRegistry(clock)

    @Test
    fun aCompletedRoundIsRunningWithItsTimeAndCount() {
        val handle = registry.handle("segment-matcher", WorkerKind.LOOP)
        assertEquals(WorkerState.STARTING, registry["segment-matcher"]!!.state)
        handle.success()
        val status = registry["segment-matcher"]!!
        assertEquals(WorkerState.RUNNING, status.state)
        assertEquals(clock.now, status.lastSuccessAt)
        assertEquals(1, status.iterations)
        assertNull(status.lastError)
    }

    @Test
    fun aFailedRoundThenASuccessKeepsTheError() {
        val handle = registry.handle("bsky-block-backfill", WorkerKind.LOOP)
        val failedAt = clock.now
        handle.failure(IOException("database unreachable"))
        registry["bsky-block-backfill"]!!.let {
            assertEquals(WorkerState.BACKING_OFF, it.state)
            assertEquals("IOException: database unreachable", it.lastError)
            assertEquals(failedAt, it.lastErrorAt)
        }
        clock.now += 1.minutes
        handle.success()
        registry["bsky-block-backfill"]!!.let {
            assertEquals(WorkerState.RUNNING, it.state)
            assertEquals(clock.now, it.lastSuccessAt)
            assertEquals("IOException: database unreachable", it.lastError)
            assertEquals(failedAt, it.lastErrorAt)
        }
    }

    @Test
    fun disabledWorkersAreListedWithTheirReason() {
        registry.disabled("space-syncer", WorkerKind.LOOP, "APPVIEW_SIGNING_KEY is not set")
        registry.handle("jetstream", WorkerKind.STREAM)
        val snapshot = registry.snapshot()
        assertEquals(listOf("jetstream", "space-syncer"), snapshot.map { it.name })
        assertEquals(WorkerState.DISABLED, snapshot[1].state)
        assertEquals("APPVIEW_SIGNING_KEY is not set", snapshot[1].disabledReason)
    }
}
