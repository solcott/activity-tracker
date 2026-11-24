package io.github.solcott.activitytracker.app.interactions

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.DayText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone

/** When a comment was written, beside its author (feed/comments): how long ago earlier today, its day and time before that. */
class CommentPostedTest {
    private val zone = TimeZone.of("America/Denver")

    // 3:00 PM in Denver.
    private val now = Instant.parse("2026-06-01T21:00:00Z")

    private fun posted(ago: Duration) = CommentsTexts.posted(now - ago, now, zone)

    private fun plural(name: String, count: Int) = AppText.Plural(name, count, persistentListOf(AppText.written("$count")))

    @Test fun underAMinuteIsJustNow() = assertEquals(AppText.named("posted_just_now"), posted(40.seconds))

    @Test fun aheadOfTheClockIsJustNow() = assertEquals(AppText.named("posted_just_now"), posted((-2).minutes))

    @Test fun minutesEarlierToday() = assertEquals(plural("posted_minutes_ago", 5), posted(5.minutes + 30.seconds))

    @Test fun hoursEarlierToday() = assertEquals(plural("posted_hours_ago", 3), posted(3.hours + 59.minutes))

    @Test fun theFirstMinuteOfTodayIsStillHoursAgo() = assertEquals(plural("posted_hours_ago", 14), posted(14.hours + 59.minutes))

    @Test
    fun yesterdayUnder24HoursAgoReadsAsADay() {
        // 9:00 PM yesterday in Denver: 18 hours ago, but another day.
        val at = now - 18.hours
        assertEquals(DayText.dayAndTime(at, now, zone), CommentsTexts.posted(at, now, zone))
    }

    @Test
    fun olderReadsAsItsDateAndTime() {
        val at = now - 72.hours
        assertEquals(DayText.dayAndTime(at, now, zone), CommentsTexts.posted(at, now, zone))
    }
}
