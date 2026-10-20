package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class CombineCueTest {
    private val start = Instant.parse("2026-10-10T09:00:00Z")

    private fun record(after: Duration, length: Duration, sport: String = Sport.RIDE) =
        ActivityRecord(sport, start + after, length.inWholeSeconds, start)

    /** About 50 m north of the trailhead per 0.00045°. */
    private val trailhead = EndPoint(46.0, 7.0, altitudeMeters = 1_200.0)

    private fun cue(earlier: ActivityRecord, earlierEnd: EndPoint?, later: ActivityRecord, laterStart: EndPoint?) =
        CombineCues.of(
            ActivityMatcher.pairing(earlier, later)!!,
            later,
            ActivityEnds(start = laterStart),
            earlier,
            ActivityEnds(end = earlierEnd),
        )

    @Test
    fun aWatchThatDiedIsASwitchOfDevices() {
        val watch = record(Duration.ZERO, 1.hours)
        val phone = record(1.hours + 2.minutes, 1.hours)
        assertEquals(CombineCue.SwitchedDevices, cue(watch, trailhead, phone, trailhead.copy(latitude = 46.00045)))
    }

    @Test
    fun startingWellAboveIsAShuttle() {
        val first = record(Duration.ZERO, 30.minutes)
        val second = record(55.minutes, 30.minutes)
        assertEquals(CombineCue.ShuttleOrLift, cue(first, trailhead, second, EndPoint(46.02, 7.01, altitudeMeters = 1_600.0)))
    }

    @Test
    fun aLongStopAtTheSameSpotIsABreak() {
        val morning = record(Duration.ZERO, 2.hours)
        val afternoon = record(3.hours + 15.minutes, 2.hours)
        assertEquals(CombineCue.Break, cue(morning, trailhead, afternoon, trailhead))
    }

    @Test
    fun anOverlapIsTheSameActivity() {
        val watch = record(Duration.ZERO, 45.minutes, Sport.RUN)
        val phone = record(1.minutes, 45.minutes, Sport.RUN)
        assertEquals(CombineCue.SameActivity, cue(watch, null, phone, null))
    }

    @Test
    fun unknownEndsAreABreak() {
        val watch = record(Duration.ZERO, 1.hours)
        val phone = record(1.hours + 2.minutes, 1.hours)
        assertEquals(CombineCue.Break, cue(watch, null, phone, trailhead))
    }
}
