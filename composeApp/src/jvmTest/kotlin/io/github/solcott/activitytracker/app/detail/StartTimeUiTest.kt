package io.github.solcott.activitytracker.app.detail

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.fixtures.fakeActivity
import io.github.solcott.activitytracker.app.fixtures.fakeActivityRecord
import io.github.solcott.activitytracker.app.ui.ActivityCard
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/** An activity's start reads on its own clock, as Today, Yesterday or a date (feed/activity-start-time). */
@OptIn(ExperimentalTestApi::class)
class StartTimeUiTest {
    /** Who's looking, and when: the preview's clock, zone and clock style. */
    private data class Viewer(
        val at: Instant = Instant.parse("2026-10-28T20:00:00Z"),
        val zone: TimeZone = TimeZone.UTC,
        val use24Hour: Boolean = false,
    )

    private fun start(iso: String, offset: Int?, dateOnly: Boolean = false) =
        FigureValue.StartTime(Instant.parse(iso).epochSeconds, offset, dateOnly)

    private fun assertReads(expected: String, value: FigureValue.StartTime, viewer: Viewer = Viewer()) = runComposeUiTest {
        setContent { AppPreview(now = viewer.at, timeZone = viewer.zone, use24Hour = viewer.use24Hour) { Text(startTime(value)) } }
        onNodeWithText(expected).assertExists()
    }

    @Test fun today() = assertReads("Today at 7:12 AM", start("2026-10-28T07:12:00Z", offset = 0))

    @Test fun yesterday() = assertReads("Yesterday at 6:30 PM", start("2026-10-27T18:30:00Z", offset = 0))

    @Test fun anOlderDateIsWrittenOut() = assertReads("October 26, 2026 at 2:00 PM", start("2026-10-26T14:00:00Z", offset = 0))

    @Test fun aTwentyFourHourClock() = assertReads("Today at 14:05", start("2026-10-28T14:05:00Z", offset = 0), Viewer(use24Hour = true))

    @Test
    fun theActivitysClockNotTheViewers() =
        assertReads("Today at 9:00 AM", start("2026-10-28T07:00:00Z", offset = 120), Viewer(zone = TimeZone.of("Etc/GMT+5")))

    @Test
    fun todayIsTheDateWhereItHappened() {
        // 11:30 PM at UTC-7 on the 27th; midnight there is 07:00 UTC on the 28th.
        val value = start("2026-10-28T06:30:00Z", offset = -420)
        assertReads("Today at 11:30 PM", value, Viewer(at = Instant.parse("2026-10-28T06:59:00Z")))
        assertReads("Yesterday at 11:30 PM", value, Viewer(at = Instant.parse("2026-10-28T07:00:00Z")))
    }

    @Test
    fun noOffsetReadsInTheViewersZone() =
        assertReads("Today at 9:10 AM", start("2026-10-28T15:10:00Z", offset = null), Viewer(zone = TimeZone.of("Etc/GMT+6")))

    @Test fun aHiddenStartShowsTheDayOnly() = assertReads("Today", start("2026-10-28T00:00:00Z", offset = 0, dateOnly = true))

    @Test fun anOlderHiddenStartShowsTheDate() = assertReads("October 26, 2026", start("2026-10-26T00:00:00Z", offset = 0, dateOnly = true))

    @Test
    fun aHiddenStartWithNoOffsetKeepsItsDay() =
        assertReads(
            "October 26, 2026",
            start("2026-10-26T00:00:00Z", offset = null, dateOnly = true),
            Viewer(zone = TimeZone.of("Etc/GMT+6")),
        )

    @Test
    fun aCardShowsTheStart() = runComposeUiTest {
        // The sample ride started 1:00 AM MDT on May 31, the day before the preview's June 1.
        setContent {
            AppPreview(use24Hour = false) {
                ActivityCard(fakeActivity(), onClick = {}, onAuthorClick = null, onLike = null, onOpenMedia = {})
            }
        }
        onNodeWithText("Yesterday at 1:00 AM").assertExists()
    }

    @Test
    fun aCardWithAHiddenStartShowsTheDayOnly() = runComposeUiTest {
        // Local midnight at MDT, as the record holds it when the start is narrower than the activity.
        val record = fakeActivityRecord(startedAt = Instant.parse("2026-05-31T06:00:00Z"))
        setContent {
            AppPreview { ActivityCard(fakeActivity(record = record), onClick = {}, onAuthorClick = null, onLike = null, onOpenMedia = {}) }
        }
        onNodeWithText("Yesterday").assertExists()
    }
}
