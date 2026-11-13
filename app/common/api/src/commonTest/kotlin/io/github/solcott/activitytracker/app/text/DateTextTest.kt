package io.github.solcott.activitytracker.app.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

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
