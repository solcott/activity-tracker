package io.github.solcott.activitytracker.data.profile

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CalendarDateTest {
    @Test
    fun matchesJavaTimeForFourDigitYears() {
        for (day in listOf(-719_162L, -1L, 0L, 59L, 11_016L, 20_000L, 2_932_896L)) {
            val expected = LocalDate.ofEpochDay(day).toString()
            assertEquals(expected, CalendarDate.fromEpochDay(day))
            assertEquals(day, CalendarDate.toEpochDay(expected))
        }
    }

    @Test
    fun rejectsDatesThatDontExist() {
        assertNull(CalendarDate.toEpochDay("2023-02-29"))
        assertNull(CalendarDate.toEpochDay("1990-13-01"))
        assertNull(CalendarDate.toEpochDay("1990-4-1"))
        assertEquals(LocalDate.parse("2024-02-29").toEpochDay(), CalendarDate.toEpochDay("2024-02-29"))
    }

    @Test
    fun ageCountsWholeYears() {
        val now = Instant.parse("2026-10-04T12:00:00Z")
        assertEquals(36, CalendarDate.age("1990-10-04", now))
        assertEquals(35, CalendarDate.age("1990-10-05", now))
        assertNull(CalendarDate.age("2027-01-01", now))
        assertEquals("1990-04-01", CalendarDate.fromPickerMillis(CalendarDate.toPickerMillis("1990-04-01")!!))
    }
}
