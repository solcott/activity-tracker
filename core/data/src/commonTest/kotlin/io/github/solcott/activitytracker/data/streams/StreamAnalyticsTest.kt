package io.github.solcott.activitytracker.data.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamAnalyticsTest {
    @Test
    fun timeInZonesHoldsSamplesAcrossShortGapsOnly() {
        // 0..9 s at 150 W every second, then one sample at 20 s (a pause), then 260 W held 2 s.
        val time = (0..9).toList() + listOf(20, 21, 23)
        val watts = List(10) { 150 } + listOf(100, 260, 260)
        assertEquals(listOf(1, 10, 3), StreamAnalytics.timeInZones(time, watts, lows = listOf(0, 140, 250)))
    }

    @Test
    fun timeInZonesCountsSteadyThirtySecondRecordingInFull() {
        // An 8:00 run with heart rate logged every 30 s, all in zone 3.
        val time = (0..480 step 30).toList()
        val heartRate = List(time.size) { 155 }
        assertEquals(listOf(0, 0, 481, 0), StreamAnalytics.timeInZones(time, heartRate, lows = listOf(0, 120, 150, 170)))
    }

    @Test
    fun aMissingSampleHoldsThePreviousValue() {
        assertEquals(listOf(0, 3), StreamAnalytics.timeInZones(listOf(0, 1, 2), listOf(160, null, 165), lows = listOf(0, 150)))
    }

    @Test
    fun clockSecondsMatchPerSecondForUnbrokenRecording() {
        val time = (0..59).toList()
        val watts = time.map { 100 + it }
        assertEquals(StreamAnalytics.perSecond(time, watts), StreamAnalytics.clockSeconds(time, watts))
    }

    @Test
    fun clockSecondsCountAPauseItsRealLengthAtZero() {
        // 0..59 s at 200 W, a 10-minute auto-pause, then 660..719 s at 200 W.
        val time = (0..59).toList() + (660..719).toList()
        val seconds = StreamAnalytics.clockSeconds(time, time.map { 200 })
        assertEquals(720, seconds.size)
        assertEquals(600, seconds.count { it == 0 })
        assertEquals(List(599) { 0 }, seconds.subList(60, 659))
    }

    @Test
    fun clockSecondsHoldSmartRecordingWithoutZeros() {
        // Gaps cycling 1 to 8 s, as smart recording logs.
        val time = generateSequence(0 to 0) { (at, i) -> at + (i % 8 + 1) to i + 1 }.map { it.first }.takeWhile { it <= 600 }.toList()
        val seconds = StreamAnalytics.clockSeconds(time, time.map { 180 })
        assertEquals(time.last() + 1, seconds.size)
        assertTrue(seconds.all { it == 180 })
    }

    @Test
    fun clockSecondsSkipMissingSamples() {
        assertEquals(listOf(160, 160, 165), StreamAnalytics.clockSeconds(listOf(0, 1, 2), listOf(160, null, 165)))
    }

    @Test
    fun meanMaxFindsTheBestWindowForEachDurationItFits() {
        val seconds = listOf(100, 100, 400, 300, 100, 100)
        assertEquals(listOf(1 to 400, 5 to 200), StreamAnalytics.meanMax(seconds, durations = listOf(1, 5, 10)))
    }

    @Test
    fun gradeIsTheClimbOverACenteredWindow() {
        // 10 m apart, climbing 1 m each: 10%.
        val distance = (0..10).map { it * 1_000 }
        val altitude = (0..10).map { it * 100 }
        val grade = StreamAnalytics.grade(distance, altitude)
        assertEquals(10.0, grade[5]!!, absoluteTolerance = 0.01)
        assertNull(StreamAnalytics.grade(listOf(0, null), listOf(0, 0))[1])
    }

    @Test
    fun averagedKeepsSpikesInTheMeanAtTheMiddleOfTheRun() {
        val values = listOf(0.0, 600.0, 0.0, 0.0, null, null, null, null, null)
        val points = StreamAnalytics.averaged(time = (0..8).toList(), values = values, max = 3)
        // Each run sits at the mean time of its present samples, not its start, so the line lines up with the time axis.
        assertEquals(listOf(ChartPoint(1, 200.0), ChartPoint(3, 0.0)), points)
    }

    @Test
    fun averagedBreaksAtALongGapAndKeepsNoPointInsideIt() {
        // One sample a second for 10 minutes, a 10-minute auto-pause, then 10 minutes more.
        val time = (0 until 600).toList() + (1200 until 1800).toList()
        val points = StreamAnalytics.averaged(time, time.map { 150.0 }, max = 50)
        assertEquals(listOf(points.indexOfFirst { it.seconds >= 1200 }), points.indices.filter { points[it].gapBefore })
        assertTrue(points.none { it.seconds in 600 until 1200 }, "no point inside the gap")
    }

    @Test
    fun averagedDoesNotLetARunStraddleAGap() {
        // A 3-minute gap two samples into what would be a 10-sample run: thinned naively, its point would sit in the middle of the gap.
        val time = (0 until 102).toList() + (282 until 380).toList()
        val points = StreamAnalytics.averaged(time, time.map { 1.0 }, max = 20)
        assertTrue(points.none { it.seconds in 102 until 282 }, "no point inside the gap: $points")
        assertEquals(1, points.count { it.gapBefore })
    }

    @Test
    fun averagedJoinsShortStopsAndSmartRecording() {
        // A 90 s stop at one sample a second, then smart recording 1–8 s apart.
        val smart = generateSequence(400) { it + (it % 8) + 1 }.takeWhile { it < 4000 }.toList()
        val time = (0 until 300).toList() + (390 until 400).toList() + smart
        assertTrue(StreamAnalytics.averaged(time, time.map { 1.0 }).none { it.gapBefore })
    }

    @Test
    fun averagedJoinsSparseSteadyRecording() {
        // Heart rate every 60 s for an hour.
        val time = (0..3600 step 60).toList()
        assertTrue(StreamAnalytics.averaged(time, time.map { 140.0 }).none { it.gapBefore })
    }

    @Test
    fun aDroppedOutSensorIsAGapThoughTimeRunsOn() {
        // Samples every second for 15 minutes, heart rate missing for minutes 5 to 10; the first point after is the mean of 600..602.
        val time = (0 until 900).toList()
        val heartRate = time.map { if (it in 300 until 600) null else 140.0 }
        val points = StreamAnalytics.averaged(time, heartRate)
        assertEquals(listOf(601), points.filter { it.gapBefore }.map { it.seconds })
        assertTrue(StreamAnalytics.averaged(time, time.map { 200.0 }).none { it.gapBefore })
    }

    @Test
    fun gapLimitIsTwoMinutesOrFourUsualIntervals() {
        assertEquals(120, StreamAnalytics.gapLimit((0..100).toList()))
        assertEquals(240, StreamAnalytics.gapLimit((0..3600 step 60).toList()))
        assertEquals(120, StreamAnalytics.gapLimit(emptyList()))
    }

    @Test
    fun everyNthKeepsTheLast() {
        assertEquals(listOf(0, 3, 6, 9, 10), StreamAnalytics.everyNth((0..10).toList(), max = 4))
    }
}
