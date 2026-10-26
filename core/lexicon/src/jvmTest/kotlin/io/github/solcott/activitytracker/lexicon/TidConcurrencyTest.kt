package io.github.solcott.activitytracker.lexicon

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Record keys (data/record-keys): unique and increasing however many threads ask at once, and whatever the clock does. */
class TidConcurrencyTest {
    @Test
    fun threadsAskingInTheSameMicrosecondGetDistinctTids() {
        // A clock that stands still, so every thread asks for the same microsecond.
        val still = fixed(Instant.parse("2026-10-26T12:00:00Z"))
        val threads = 8
        val perThread = 20_000
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        val batches =
            List(threads) {
                pool.submit<List<String>> {
                    start.await()
                    List(perThread) { Tid.next(still) }
                }
            }
        start.countDown()
        val tids = batches.map { it.get(1, TimeUnit.MINUTES) }
        pool.shutdown()

        assertEquals(threads * perThread, tids.flatten().toSet().size, "no TID is handed out twice")
        assertTrue(tids.all { it == it.sorted() }, "each thread's TIDs increase")
    }

    @Test
    fun aClockSetBackStillGivesLaterTids() {
        val now = Instant.parse("2026-10-26T12:00:00Z")
        val before = Tid.next(fixed(now))
        val after = Tid.next(fixed(now - 1.hours))
        assertTrue(after > before, "$after sorts after $before")
    }

    private fun fixed(at: Instant) =
        object : Clock {
            override fun now() = at
        }
}
