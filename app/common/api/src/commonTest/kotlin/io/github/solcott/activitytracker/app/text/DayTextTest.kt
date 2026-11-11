package io.github.solcott.activitytracker.app.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** A day beside the current one, and something lasting until a time today or tomorrow (dev/shared-display-text). */
class DayTextTest {
    private val today = LocalDate(2026, 6, 1)
    private val now = Instant.parse("2026-06-01T12:00:00Z")

    @Test fun today() = assertEquals(AppText.named("start_today"), DayText.day(today, today))

    @Test fun yesterday() = assertEquals(AppText.named("start_yesterday"), DayText.day(LocalDate(2026, 5, 31), today))

    @Test
    fun anEarlierDayIsWrittenOutWithItsYearUngrouped() =
        assertEquals(
            AppText.named("page_start_date", AppText.named("month_long_10"), AppText.written("26"), AppText.written("2026")),
            DayText.day(LocalDate(2026, 10, 26), today),
        )

    @Test
    fun aDayAndTimeIsReadInTheZoneGiven() {
        val at = Instant.parse("2026-06-01T07:00:00Z")
        assertEquals(
            AppText.named("page_start_value", AppText.named("start_today"), AppText.Time(at.epochSeconds)),
            DayText.dayAndTime(at, now, TimeZone.UTC),
        )
        // In Honolulu that moment is the evening of May 31, and now is already June 1 there.
        assertEquals(
            AppText.named("page_start_value", AppText.named("start_yesterday"), AppText.Time(at.epochSeconds)),
            DayText.dayAndTime(at, now, TimeZone.of("Pacific/Honolulu")),
        )
    }

    @Test
    fun untilLaterToday() {
        val until = now + 3.hours
        assertEquals(AppText.named("today", AppText.Time(until.epochSeconds)), DayText.until(until, now, TimeZone.UTC, "today", "tomorrow"))
    }

    @Test
    fun untilPastMidnight() {
        val until = now + 13.hours
        assertEquals(
            AppText.named("tomorrow", AppText.Time(until.epochSeconds)),
            DayText.until(until, now, TimeZone.UTC, "today", "tomorrow"),
        )
    }
}
