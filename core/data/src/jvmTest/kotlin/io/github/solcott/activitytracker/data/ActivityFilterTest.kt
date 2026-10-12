package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.feed.ActivityFilter
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Instant

class ActivityFilterTest {
    private val since = Instant.parse("2026-03-01T07:00:00Z")
    private val until = Instant.parse("2026-04-01T06:00:00Z")

    @Test
    fun unfilteredKeepsTheExistingKeys() {
        assertEquals("timeline", ActivityFilter.None.cacheKey("timeline"))
        assertEquals("author:did:plc:alice", ActivityFilter().cacheKey("author:did:plc:alice"))
    }

    @Test
    fun equivalentFiltersShareAKey() {
        val a = ActivityFilter(linkedSetOf(ActivityType("run", "trail"), ActivityType("ride")), since, until)
        val b = ActivityFilter(linkedSetOf(ActivityType("ride"), ActivityType("run", "trail")), since, until)
        assertEquals(listOf("ride", "run:trail"), a.typeTokens())
        assertEquals(a.cacheKey("timeline"), b.cacheKey("timeline"))
        assertEquals("timeline|type=ride,run:trail|since=2026-03-01T07:00:00Z|until=2026-04-01T06:00:00Z", a.cacheKey("timeline"))
    }

    @Test
    fun distinctFiltersGetDistinctKeys() {
        val keys =
            listOf(
                    ActivityFilter.None,
                    ActivityFilter(setOf(ActivityType("ride"))),
                    ActivityFilter(setOf(ActivityType("ride", "gravel"))),
                    ActivityFilter(since = since),
                    ActivityFilter(until = since),
                )
                .map { it.cacheKey("timeline") }
        assertEquals(keys.size, keys.toSet().size)
        assertNotEquals(ActivityFilter(since = since).cacheKey("timeline"), ActivityFilter(since = since).cacheKey("author:x"))
    }
}
