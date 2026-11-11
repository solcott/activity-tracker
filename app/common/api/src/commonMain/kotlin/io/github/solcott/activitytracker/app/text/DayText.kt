package io.github.solcott.activitytracker.app.text

import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * How a day reads beside the current one (dev/shared-display-text): "Today", "Yesterday" or the date written out, and something lasting
 * until a time today or tomorrow. Which day is today depends on the clock and zone each UI reads (a preview or test fixes both), so each
 * function takes them; a time of day inside is an [AppText.Time], which each UI formats on the device's clock.
 */
object DayText {
    /** [date] beside [today]: "Today", "Yesterday", or written out ("October 26, 2026"). */
    fun day(date: LocalDate, today: LocalDate): AppText =
        when (date) {
            today -> AppText.named("start_today")
            today.minus(DatePeriod(days = 1)) -> AppText.named("start_yesterday")
            else ->
                AppText.named(
                    "page_start_date",
                    AppText.named("month_long_${date.month.ordinal + 1}"),
                    // A day and a year are labels, not quantities: never grouped (ui/number-format).
                    AppText.written(date.day.toString()),
                    AppText.written(date.year.toString()),
                )
        }

    /** The moment [at] as its day beside [now]'s in [zone], at its time of day: "Today at 7:05 AM". */
    fun dayAndTime(at: Instant, now: Instant, zone: TimeZone): AppText =
        AppText.named("page_start_value", day(at.toLocalDateTime(zone).date, now.toLocalDateTime(zone).date), AppText.Time(at.epochSeconds))

    /**
     * Something lasting until [until]: the string [today] ("… until 3:40 PM") or, when it ends after [now]'s day in [zone], [tomorrow] ("…
     * until tomorrow at 3:40 PM"), each given the time of day.
     */
    fun until(until: Instant, now: Instant, zone: TimeZone, today: String, tomorrow: String): AppText {
        val later = until.toLocalDateTime(zone).date > now.toLocalDateTime(zone).date
        return AppText.named(if (later) tomorrow else today, AppText.Time(until.epochSeconds))
    }
}
