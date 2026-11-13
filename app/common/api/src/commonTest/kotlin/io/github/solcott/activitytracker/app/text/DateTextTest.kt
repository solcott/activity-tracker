package io.github.solcott.activitytracker.app.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** How a calendar date is written out (dev/shared-display-text). */
class DateTextTest {
    @Test
    fun aDateIsItsShortMonthDayAndYear() {
        assertEquals(
            AppText.named("zones_history_date", AppText.named("month_short_1"), AppText.written("5"), AppText.written("2026")),
            DateText.date(2026, 1, 5),
        )
    }

    @Test
    fun anIsoDateIsWrittenOut() {
        assertEquals(DateText.date(2026, 10, 26), DateText.isoDate("2026-10-26"))
    }

    @Test
    fun aCalendarDayIsWrittenOut() {
        assertEquals(DateText.date(2026, 7, 1), DateText.of(LocalDate(2026, 7, 1)))
        assertEquals(DateText.date(2026, 12, 31), DateText.of(LocalDate(2026, 12, 31)))
        assertEquals(DateText.isoDate("1990-04-12"), DateText.of(LocalDate(1990, 4, 12)))
    }

    @Test
    fun aMomentIsItsDateAtItsTimeOnItsOwnClock() {
        val at = Instant.parse("2024-05-12T05:02:00Z").epochSeconds
        assertEquals(AppText.named("page_start_value", DateText.date(2024, 5, 12), AppText.Time(at, 120)), DateText.at(at, 120))
    }

    @Test
    fun aMomentsDateIsItsOffsets() {
        // 23:30 UTC is 01:30 the next day at UTC+2, and 18:30 the same day at UTC-5.
        val at = Instant.parse("2024-05-12T23:30:00Z").epochSeconds
        assertEquals(AppText.named("page_start_value", DateText.date(2024, 5, 13), AppText.Time(at, 120)), DateText.at(at, 120))
        assertEquals(AppText.named("page_start_value", DateText.date(2024, 5, 12), AppText.Time(at, -300)), DateText.at(at, -300))
    }

    @Test
    fun aWallClockIsItsDateAtItsOwnHour() {
        // A zoneless 17:30 reads 17:30 whatever the device's zone: a UTC moment shown at offset 0.
        val at = Instant.parse("2024-10-16T17:30:57Z").epochSeconds
        assertEquals(
            AppText.named("page_start_value", DateText.date(2024, 10, 16), AppText.Time(at, 0)),
            DateText.of(LocalDateTime(2024, 10, 16, 17, 30, 57)),
        )
    }

    @Test
    fun aShortDateIsItsMonthAndDay() {
        assertEquals(
            AppText.named("zones_history_date_short", AppText.named("month_short_3"), AppText.written("2")),
            DateText.monthDay(3, 2),
        )
    }

    @Test
    fun textThatIsNoDateReadsAsItself() {
        assertEquals(AppText.written("soon"), DateText.isoDate("soon"))
        assertEquals(AppText.written("2026-13-01"), DateText.isoDate("2026-13-01"))
    }

    @Test
    fun aYearIsNeverGrouped() {
        val year = (DateText.date(12026, 3, 1) as AppText.Named).args[2]
        assertEquals(AppText.written("12026"), year)
    }
}
