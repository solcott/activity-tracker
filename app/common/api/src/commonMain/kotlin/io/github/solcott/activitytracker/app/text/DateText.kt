package io.github.solcott.activitytracker.app.text

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * A calendar date written out (dev/shared-display-text): the short month name, the day and the year ("Jan 5, 2026"), as every date a UI
 * shows reads (a board's entries, a segment's efforts, threshold history, gear's dates, the upload queue, a birth date); no UI writes one
 * as `YYYY-MM-DD`. A day beside today ("Today", "Yesterday") is [DayText]'s.
 */
object DateText {
    /** The date [year]-[month]-[day], [month] from 1 for January. */
    fun date(year: Int, month: Int, day: Int): AppText =
        AppText.named(
            "zones_history_date",
            AppText.named("month_short_$month"),
            // A day and a year are labels, not quantities: never grouped (ui/number-format).
            AppText.written(day.toString()),
            AppText.written(year.toString()),
        )

    /** A day of a year that goes without saying, as a chart's short label: "Mar 2". */
    fun monthDay(month: Int, day: Int): AppText =
        AppText.named("zones_history_date_short", AppText.named("month_short_$month"), AppText.written(day.toString()))

    /** [date] written out. */
    fun of(date: LocalDate): AppText = date(date.year, date.month.ordinal + 1, date.day)

    /**
     * The moment [epochSeconds] on the clock [utcOffsetMinutes] from UTC: its date written out, at its time of day ("May 12, 2024 at 7:02
     * AM"), an [AppText.Time] in that offset, which each UI formats on the device's 12- or 24-hour clock.
     */
    fun at(epochSeconds: Long, utcOffsetMinutes: Int): AppText {
        val clock = UtcOffset(seconds = utcOffsetMinutes * SECONDS_PER_MINUTE).asTimeZone()
        val day = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(clock).date
        return AppText.named("page_start_value", of(day), AppText.Time(epochSeconds, utcOffsetMinutes))
    }

    /** A `YYYY-MM-DD` date (an effort's or a board entry's) written out; the text itself when it isn't one. */
    @Suppress("MagicNumber") // A date's three parts.
    fun isoDate(text: String): AppText {
        val parts = text.split('-').mapNotNull { it.toIntOrNull() }
        return if (parts.size == 3 && parts[1] in 1..12) date(parts[0], parts[1], parts[2]) else AppText.written(text)
    }

    private const val SECONDS_PER_MINUTE = 60
}
