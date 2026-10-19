package io.github.solcott.activitytracker.app.ui.chart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChartGapsTest {
    @Test
    fun gapsAreThePointsAfterEachGap() {
        val points =
            listOf(SeriesPoint(0.0, 1.0), SeriesPoint(48.0, 2.0), SeriesPoint(1430.0, 3.0, gapBefore = true), SeriesPoint(1478.0, 4.0))
        assertEquals(listOf(1430.0), gapsIn(points))
        assertEquals(emptyList(), gapsIn(points.map { it.copy(gapBefore = false) }))
    }

    @Test
    fun dataXInvertsWherePointsAreDrawn() {
        // A 4-hour chart 900 px wide, labelled every 1800 s: 112.5 px per label step, drawn from 40 px.
        val xSpacing = 112.5
        val xStep = 1800.0
        fun drawn(x: Double, direction: Int, start: Float) = start + direction * xSpacing * (x / xStep)
        for (x in listOf(0.0, 48.0, 1430.0, 14_400.0)) {
            assertEquals(x, dataX(drawn(x, 1, 40f).toFloat() - 40f, 1, xSpacing, 0.0, xStep), absoluteTolerance = 0.5)
            assertEquals(x, dataX(drawn(x, -1, 940f).toFloat() - 940f, -1, xSpacing, 0.0, xStep), absoluteTolerance = 0.5)
        }
    }

    @Test
    fun aGapMatchesOnlyWithinTolerance() {
        val gaps = listOf(1430.0, 5000.0)
        assertTrue(gaps.containsNear(1430.0, tolerance = 8.0))
        assertTrue(gaps.containsNear(1437.0, tolerance = 8.0))
        assertTrue(gaps.containsNear(4995.0, tolerance = 8.0))
        // The point before the gap, 1382 s from the next, and points between gaps don't match.
        assertFalse(gaps.containsNear(48.0, tolerance = 8.0))
        assertFalse(gaps.containsNear(1478.0, tolerance = 8.0))
        assertFalse(gaps.containsNear(6000.0, tolerance = 8.0))
        assertFalse(emptyList<Double>().containsNear(0.0, tolerance = 8.0))
    }
}
