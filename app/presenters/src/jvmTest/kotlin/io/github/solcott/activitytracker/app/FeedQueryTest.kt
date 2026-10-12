package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.feed.toActivityFilter
import io.github.solcott.activitytracker.app.feedfilter.DateRange
import io.github.solcott.activitytracker.app.feedfilter.FeedFilter
import io.github.solcott.activitytracker.data.feed.ActivityFilter
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class FeedQueryTest {
    private val denver = TimeZone.of("America/Denver")
    private val today = LocalDate(2026, 10, 11)

    @Test
    fun noFilterIsUnfiltered() {
        assertEquals(ActivityFilter.None, FeedFilter.Default.toActivityFilter(today, denver))
    }

    @Test
    fun rangesResolveToLocalDays() {
        fun since(range: DateRange) = FeedFilter(range = range).toActivityFilter(today, denver)
        assertEquals(
            ActivityFilter(since = Instant.parse("2026-10-05T06:00:00Z")),
            since(DateRange.LastDays(7)),
            "today and the six days before",
        )
        assertEquals(ActivityFilter(since = Instant.parse("2026-01-01T07:00:00Z")), since(DateRange.ThisYear))
        assertEquals(
            ActivityFilter(since = Instant.parse("2026-03-01T07:00:00Z"), until = Instant.parse("2026-04-01T06:00:00Z")),
            since(DateRange.Custom(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31))),
            "the last day is included, across the DST change",
        )
    }

    @Test
    fun typesPassThrough() {
        val types = persistentSetOf(ActivityType("ride"), ActivityType("run", "trail"))
        assertEquals(ActivityFilter(types), FeedFilter(types = types).toActivityFilter(today, denver))
    }
}
