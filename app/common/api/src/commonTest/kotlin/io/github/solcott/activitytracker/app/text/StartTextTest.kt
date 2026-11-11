package io.github.solcott.activitytracker.app.text

import io.github.solcott.activitytracker.app.detail.FigureValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/** An activity's start on its own clock (feed/activity-start-time, dev/shared-display-text). */
class StartTextTest {
    private fun start(iso: String, offset: Int?, dateOnly: Boolean = false) =
        FigureValue.StartTime(Instant.parse(iso).epochSeconds, offset, dateOnly)

    private fun at(iso: String) = Instant.parse(iso).epochSeconds

    @Test
    fun aStartInAnotherZoneReadsOnItsOwnClock() {
        // 9:00 AM at UTC-7, seen from UTC at 18:00 the same day.
        val value = start("2026-06-01T16:00:00Z", offset = -420)
        assertEquals(
            AppText.named("page_start_value", AppText.named("start_today"), AppText.Time(at("2026-06-01T16:00:00Z"), -420)),
            StartText.of(value, Instant.parse("2026-06-01T18:00:00Z"), TimeZone.UTC),
        )
    }

    @Test
    fun todayIsTheActivitysDayNotTheViewers() {
        // 11:30 PM at UTC-7 is 06:30 UTC the next day: yesterday at 07:00 UTC there, already today in UTC.
        val value = start("2026-06-02T06:30:00Z", offset = -420)
        val seen = StartText.of(value, Instant.parse("2026-06-03T06:59:00Z"), TimeZone.UTC)
        assertEquals(
            AppText.named("page_start_value", AppText.named("start_yesterday"), AppText.Time(at("2026-06-02T06:30:00Z"), -420)),
            seen,
        )
    }

    @Test
    fun withoutAnOffsetItIsTheViewersZone() {
        val value = start("2026-06-01T07:00:00Z", offset = null)
        assertEquals(
            AppText.named("page_start_value", AppText.named("start_today"), AppText.Time(at("2026-06-01T07:00:00Z"))),
            StartText.of(value, Instant.parse("2026-06-01T12:00:00Z"), TimeZone.of("America/Denver")),
        )
    }

    @Test
    fun aDateOnlyStartIsItsDayAlone() {
        // Cut at UTC midnight, so read in UTC whatever the viewer's zone.
        val value = start("2026-06-01T00:00:00Z", offset = null, dateOnly = true)
        assertEquals(
            AppText.named("start_today"),
            StartText.of(value, Instant.parse("2026-06-01T12:00:00Z"), TimeZone.of("America/Denver")),
        )
    }
}
