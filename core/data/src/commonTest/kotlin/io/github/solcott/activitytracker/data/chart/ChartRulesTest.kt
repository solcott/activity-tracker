package io.github.solcott.activitytracker.data.chart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartAxisTest {
    @Test
    fun aRoundStepWithAtMostFourGaps() {
        assertEquals(ChartAxis(1_400.0, 1_600.0, 50.0), ChartAxis.around(1_410.0, 1_590.0))
        assertEquals(listOf(1_400.0, 1_450.0, 1_500.0, 1_550.0, 1_600.0), ChartAxis(1_400.0, 1_600.0, 50.0).ticks)
    }

    @Test
    fun valuesFromZeroKeepAFloorAtZero() {
        assertEquals(0.0, ChartAxis.of(listOf(2.0, 180.0)).min)
    }

    @Test
    fun anInvertedAxisIsBelowZero() {
        val axis = ChartAxis.of(listOf(0.0, 30.0), inverted = true)
        assertTrue(axis.max == 0.0)
        assertEquals(-40.0, axis.min)
    }

    @Test
    fun timeValuesStepOnAClock() {
        assertEquals(120.0, ChartAxis.of(listOf(300.0, 480.0), timeValues = true).step)
    }
}

class BandStopTest {
    @Test
    fun aStopWhereverTheBandChangesHalfwayBetweenPoints() {
        val at = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        assertEquals(
            listOf(BandStop(0f, 0), BandStop(0.375f, 2), BandStop(0.875f, 4)),
            BandStop.runs(at, listOf(0, 0, 2, 2, 4), minPoints = 1),
        )
    }

    @Test
    fun aGradeHoveringAtABandsEdgeDoesntFlicker() {
        val at = List(12) { it / 11f }
        // Alternating 0 and 1 points stay 0; the steady run of 3s later is kept.
        val bands = listOf(0, 1, 0, 1, 0, 1, 0, 3, 3, 3, 3, 3)
        assertEquals(listOf(BandStop(0f, 0), BandStop((at[6] + at[7]) / 2, 3)), BandStop.runs(at, bands))
    }

    @Test
    fun aShortRunJoinsTheOneBefore() {
        val at = List(8) { it / 7f }
        assertEquals(listOf(BandStop(0f, 2)), BandStop.runs(at, listOf(2, 2, 2, 0, 0, 2, 2, 2)))
    }

    @Test
    fun oneBandIsOneStop() {
        assertEquals(listOf(BandStop(0f, 1)), BandStop.runs(listOf(0f, 0.5f, 1f), listOf(1, 1, 1)))
        assertEquals(emptyList(), BandStop.runs(emptyList(), emptyList()))
    }
}
