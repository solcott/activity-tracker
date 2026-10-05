package io.github.solcott.activitytracker.data.profile

import kotlin.time.Instant

/**
 * `YYYY-MM-DD` calendar dates, as profile birthdates store them, without a date-time library. An epoch day counts days from 1970-01-01, the
 * unit Material's date picker uses (as UTC midnight millis).
 */
object CalendarDate {
    private val pattern = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    private const val MILLIS_PER_DAY = 86_400_000L
    private const val SECONDS_PER_DAY = 86_400L

    // Howard Hinnant's days_from_civil / civil_from_days, on a calendar of 400-year eras starting in March.
    private const val DAYS_PER_ERA = 146_097L
    private const val YEARS_PER_ERA = 400L
    private const val EPOCH_OFFSET = 719_468L
    private const val MONTHS_BEFORE_MARCH = 2

    /** Days since 1970-01-01, or null when [date] isn't a real `YYYY-MM-DD` date. */
    fun toEpochDay(date: String): Long? {
        val (y, m, d) = pattern.matchEntire(date)?.destructured ?: return null
        val day = daysFromCivil(y.toLong(), m.toInt(), d.toInt())
        // Round-tripping rejects month 13, February 30th and the like.
        return day.takeIf { fromEpochDay(it) == date }
    }

    fun fromEpochDay(epochDay: Long): String {
        val (y, m, d) = civilFromDays(epochDay)
        return "${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
    }

    fun fromPickerMillis(utcMillis: Long): String = fromEpochDay(utcMillis.floorDiv(MILLIS_PER_DAY))

    fun toPickerMillis(date: String): Long? = toEpochDay(date)?.let { it * MILLIS_PER_DAY }

    /** Today's date in UTC. */
    fun today(now: Instant): String = fromEpochDay(now.epochSeconds.floorDiv(SECONDS_PER_DAY))

    /** Whole years from [birthDate] to [now] (in UTC); null for an invalid or future date. */
    fun age(birthDate: String, now: Instant): Int? {
        toEpochDay(birthDate) ?: return null
        val today = today(now)
        if (today < birthDate) return null
        val (by, bmd) = birthDate.split('-', limit = 2)
        val (ty, tmd) = today.split('-', limit = 2)
        return ty.toInt() - by.toInt() - if (tmd < bmd) 1 else 0
    }

    private fun daysFromCivil(year: Long, month: Int, day: Int): Long {
        val y = if (month <= MONTHS_BEFORE_MARCH) year - 1 else year
        val era = y.floorDiv(YEARS_PER_ERA)
        val yearOfEra = y - era * YEARS_PER_ERA
        val dayOfYear = (153 * (if (month > MONTHS_BEFORE_MARCH) month - 3 else month + 9) + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * DAYS_PER_ERA + dayOfEra - EPOCH_OFFSET
    }

    private fun civilFromDays(epochDay: Long): Triple<Long, Int, Int> {
        val z = epochDay + EPOCH_OFFSET
        val era = z.floorDiv(DAYS_PER_ERA)
        val dayOfEra = z - era * DAYS_PER_ERA
        val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
        val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        val mp = (5 * dayOfYear + 2) / 153
        val day = (dayOfYear - (153 * mp + 2) / 5 + 1).toInt()
        val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val year = yearOfEra + era * YEARS_PER_ERA + if (month <= MONTHS_BEFORE_MARCH) 1 else 0
        return Triple(year, month, day)
    }
}
