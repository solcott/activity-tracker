package io.github.solcott.activitytracker.app.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.persistentListOf

/** A rough length of time, rounded to its largest unit (dev/shared-display-text). */
class DurationTextTest {
    private fun plural(name: String, quantity: Int) = AppText.Plural(name, quantity, persistentListOf(AppText.written("$quantity")))

    private fun reads(expected: AppText, duration: Duration) = assertEquals(expected, DurationText.approximate(duration))

    @Test fun minutesAreWholeUnderAnHour() = reads(plural("duration_minutes", 40), 40.minutes + 30.seconds)

    @Test fun neverUnderAMinute() = reads(plural("duration_minutes", 1), 10.seconds)

    @Test fun anHourIsInHours() = reads(plural("duration_hours", 1), 1.hours)

    @Test fun hoursRound() = reads(plural("duration_hours", 3), 2.hours + 40.minutes)

    @Test fun aDayAndABitIsADay() = reads(plural("duration_days", 1), 26.hours)

    @Test fun daysRound() = reads(plural("duration_days", 2), 1.days + 13.hours)

    @Test
    fun aLongEstimateGroupsItsDigits() =
        assertEquals(AppText.Plural("duration_days", 1200, persistentListOf(AppText.written("1,200"))), DurationText.approximate(1200.days))
}
