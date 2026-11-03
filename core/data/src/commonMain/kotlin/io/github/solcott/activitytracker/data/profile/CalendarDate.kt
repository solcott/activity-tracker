package io.github.solcott.activitytracker.data.profile

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearsUntil

/**
 * `YYYY-MM-DD` calendar dates, as profile birthdates store them. An epoch day counts days from 1970-01-01, the unit Material's date picker
 * uses (as UTC midnight millis).
 */
object CalendarDate {
    private const val MILLIS_PER_DAY = 86_400_000L

    /** Days since 1970-01-01, or null when [date] isn't a real `YYYY-MM-DD` date. */
    fun toEpochDay(date: String): Long? = parse(date)?.toEpochDays()

    fun fromEpochDay(epochDay: Long): String = LocalDate.fromEpochDays(epochDay).toString()

    fun fromPickerMillis(utcMillis: Long): String = fromEpochDay(utcMillis.floorDiv(MILLIS_PER_DAY))

    fun toPickerMillis(date: String): Long? = toEpochDay(date)?.let { it * MILLIS_PER_DAY }

    /** Whole years from [birthDate] to [now] (in UTC); null for an invalid or future date. */
    fun age(birthDate: String, now: Instant): Int? {
        val birth = parse(birthDate) ?: return null
        val today = utcDate(now)
        return if (birth > today) null else birth.yearsUntil(today)
    }

    private fun parse(date: String): LocalDate? = LocalDate.Formats.ISO.parseOrNull(date)

    private fun utcDate(now: Instant): LocalDate = now.toLocalDateTime(TimeZone.UTC).date
}
