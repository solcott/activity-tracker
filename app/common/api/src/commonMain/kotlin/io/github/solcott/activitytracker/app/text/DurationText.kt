package io.github.solcott.activitytracker.app.text

import io.github.solcott.activitytracker.data.units.formatWhole
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.collections.immutable.persistentListOf

/**
 * How a length of time reads (dev/shared-display-text). A length of time follows no device setting, unlike a time of day ([AppText.Time]),
 * so its unit, its rounding and its words are all chosen here, for the shared and the native UI. The clock form of a stat ("1:23:45") is
 * [StatText.duration].
 */
object DurationText {
    /**
     * [duration] rounded to its largest unit, for an estimate: "40 min" (whole minutes, at least 1) under an hour, "3 h" under a day, and
     * "2 days" from a day up, each rounded.
     */
    fun approximate(duration: Duration): AppText =
        when {
            duration < 1.hours -> plural("duration_minutes", duration.inWholeMinutes.toInt().coerceAtLeast(1))
            duration < 1.days -> plural("duration_hours", (duration / 1.hours).roundToInt().coerceAtLeast(1))
            else -> plural("duration_days", (duration / 1.days).roundToInt().coerceAtLeast(1))
        }

    private fun plural(name: String, quantity: Int): AppText =
        AppText.Plural(name, quantity, persistentListOf(AppText.written(formatWhole(quantity))))
}
