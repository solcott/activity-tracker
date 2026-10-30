package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals

class AreaUnderlayTest {
    // A layer from 0 to 100 px across and 0 to 100 px down, 10 px per second, values 0 to 10 over its height.
    private val frame =
        AreaFrame(
            Rect(0f, 0f, 100f, 100f),
            start = 0f,
            direction = 1,
            minX = 0.0,
            xStep = 1.0,
            xSpacing = 10.0,
            startPadding = 0.0,
            scroll = 0.0,
        )

    private fun outline(points: List<SeriesPoint>, fromTop: Boolean = false, step: Boolean = false, frame: AreaFrame = this.frame) =
        areaOutline(points, frame, yRange = 0.0..10.0, fromTop = fromTop, step = step)

    @Test
    fun runsFromTheBaselineAlongThePointsAndBack() {
        val points = listOf(SeriesPoint(0.0, 0.0), SeriesPoint(5.0, 5.0), SeriesPoint(10.0, 10.0))
        assertEquals(listOf(Offset(0f, 100f), Offset(0f, 100f), Offset(50f, 50f), Offset(100f, 0f), Offset(100f, 100f)), outline(points))
    }

    @Test
    fun valuesOutsideTheRangeStayInTheLayer() {
        val points = listOf(SeriesPoint(0.0, -5.0), SeriesPoint(10.0, 20.0))
        assertEquals(listOf(Offset(0f, 100f), Offset(0f, 100f), Offset(100f, 0f), Offset(100f, 100f)), outline(points))
    }

    @Test
    fun aStepHoldsEachValueUntilTheNextPoint() {
        val points = listOf(SeriesPoint(0.0, 2.0), SeriesPoint(5.0, 8.0))
        assertEquals(
            listOf(Offset(0f, 100f), Offset(0f, 80f), Offset(50f, 80f), Offset(50f, 20f), Offset(50f, 100f)),
            outline(points, step = true),
        )
    }

    @Test
    fun aGapDropsToTheBaselineAndRunsAlongIt() {
        val points = listOf(SeriesPoint(0.0, 5.0), SeriesPoint(2.0, 5.0), SeriesPoint(8.0, 5.0, gapBefore = true), SeriesPoint(10.0, 5.0))
        assertEquals(
            listOf(
                Offset(0f, 100f),
                Offset(0f, 50f),
                Offset(20f, 50f),
                Offset(20f, 100f),
                Offset(80f, 100f),
                Offset(80f, 50f),
                Offset(100f, 50f),
                Offset(100f, 100f),
            ),
            outline(points),
        )
    }

    @Test
    fun aFillFromTheTopUsesTheTopEdge() {
        // An inverted chart plots values below zero, so its range runs from the deepest point up to 0.
        val points = listOf(SeriesPoint(0.0, 0.0), SeriesPoint(5.0, -10.0, gapBefore = true))
        val drawn = areaOutline(points, frame, yRange = -10.0..0.0, fromTop = true, step = false)
        assertEquals(listOf(Offset(0f, 0f), Offset(0f, 0f), Offset(0f, 0f), Offset(50f, 0f), Offset(50f, 100f), Offset(50f, 0f)), drawn)
    }

    @Test
    fun rightToLeftRunsFromTheRightEdge() {
        val rtl = frame.copy(start = 100f, direction = -1, startPadding = 5.0)
        val points = listOf(SeriesPoint(0.0, 0.0), SeriesPoint(5.0, 10.0))
        assertEquals(listOf(Offset(95f, 100f), Offset(95f, 100f), Offset(45f, 0f), Offset(45f, 100f)), outline(points, frame = rtl))
    }
}
