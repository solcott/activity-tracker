package io.github.solcott.activitytracker.data.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StreamAnalyticsTest {
    @Test
    fun timeInZonesHoldsSamplesAcrossShortGapsOnly() {
        // 0..9 s at 150 W every second, then one sample at 20 s (a pause), then 260 W held 2 s.
        val time = (0..9).toList() + listOf(20, 21, 23)
        val watts = List(10) { 150 } + listOf(100, 260, 260)
        assertEquals(listOf(1, 10, 3), StreamAnalytics.timeInZones(time, watts, lows = listOf(0, 140, 250)))
    }

    @Test
    fun aMissingSampleHoldsThePreviousValue() {
        assertEquals(listOf(0, 3), StreamAnalytics.timeInZones(listOf(0, 1, 2), listOf(160, null, 165), lows = listOf(0, 150)))
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
    fun averagedKeepsSpikesInTheMean() {
        val points = StreamAnalytics.averaged(time = (0..5).toList(), values = listOf(0.0, 600.0, 0.0, 0.0, null, null), max = 3)
        assertEquals(listOf(0 to 300.0, 2 to 0.0), points)
    }

    @Test
    fun everyNthKeepsTheLast() {
        assertEquals(listOf(0, 3, 6, 9, 10), StreamAnalytics.everyNth((0..10).toList(), max = 4))
    }
}
