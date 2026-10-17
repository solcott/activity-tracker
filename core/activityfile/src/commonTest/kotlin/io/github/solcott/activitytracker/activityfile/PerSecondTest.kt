package io.github.solcott.activitytracker.activityfile

import kotlin.test.Test
import kotlin.test.assertEquals

class PerSecondTest {
    @Test
    fun steadyThirtySecondRecordingCountsEverySecond() {
        // 8:00 logged every 30 s: 16 intervals of 30 s, plus the last sample's own second.
        val samples = (0..480 step 30).map { it.toDouble() to 150 }
        assertEquals(481, PerSecond.hold(samples).size)
    }

    @Test
    fun aLongGapInOneSecondRecordingCountsOnce() {
        // 0..59 s, a 10-minute auto-pause, then 660..719 s.
        val samples = ((0..59) + (660..719)).map { it.toDouble() to 200 }
        assertEquals(120, PerSecond.hold(samples).size)
    }

    @Test
    fun aLongGapInSparseRecordingCountsOnce() {
        // Every 30 s for 5:00, a 20-minute pause, then every 30 s for 5:00 more.
        val samples = ((0..300 step 30) + (1500..1800 step 30)).map { it.toDouble() to 140 }
        assertEquals(300 + 1 + 300 + 1, PerSecond.hold(samples).size)
    }

    @Test
    fun smartRecordingCountsItsDuration() {
        // Gaps cycling 1 to 8 s.
        val times = (0 until 40).runningFold(0) { at, i -> at + i % 8 + 1 }
        val samples = times.map { it.toDouble() to 180 }
        assertEquals(times.last() + 1, PerSecond.hold(samples).size)
    }

    @Test
    fun oneSecondRecordingHoldsShortDropoutsOnly() {
        // A 4 s dropout holds; a 6 s one counts once.
        val samples = listOf(0, 1, 2, 6, 7, 13, 14).map { it.toDouble() to 100 }
        assertEquals(1 + 1 + 4 + 1 + 1 + 1 + 1, PerSecond.hold(samples).size)
    }

    @Test
    fun oneLongGapIsAPauseNotTheRecordingInterval() {
        val samples = listOf(0.0 to 100, 600.0 to 300)
        assertEquals(listOf(100, 300), PerSecond.hold(samples))
    }
}
