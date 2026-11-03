package io.github.solcott.activitytracker.appview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class CachesTest {
    @Test
    fun aFullCacheDropsEntriesInsteadOfGrowingOrStartingOver() {
        val cache = boundedCache<Int, String>(maxSize = 100)
        repeat(1_000) { cache.put(it, "v$it") }
        cache.cleanUp()

        assertTrue(cache.estimatedSize() <= 100, "size ${cache.estimatedSize()}")
        assertTrue(cache.estimatedSize() > 0, "it keeps entries rather than clearing")
    }

    @Test
    fun entriesExpireByTheGivenClock() {
        val clock = MutableClock(Instant.fromEpochSeconds(1_700_000_000))
        val cache = boundedCache<String, String>(maxSize = 10, ttl = 10.minutes, clock = clock)
        cache.put("did:plc:alice", "key")

        clock.now += 9.minutes
        assertEquals("key", cache.getIfPresent("did:plc:alice"))
        clock.now += 2.minutes
        assertNull(cache.getIfPresent("did:plc:alice"))
    }
}
