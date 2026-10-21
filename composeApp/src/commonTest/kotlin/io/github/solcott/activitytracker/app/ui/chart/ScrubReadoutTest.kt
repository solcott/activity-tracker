package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class ScrubReadoutTest {
    // A layer from 0 to 100 px across and 0 to 100 px down, 10 px per second, values 0 to 10 over its height.
    private val bounds = Rect(0f, 0f, 100f, 100f)
    private val frame =
        AreaFrame(bounds, start = 0f, direction = 1, minX = 0.0, xStep = 1.0, xSpacing = 10.0, startPadding = 0.0, scroll = 0.0)
    private val points =
        listOf(SeriesPoint(0.0, 1.0), SeriesPoint(4.0, 5.0), SeriesPoint(8.0, 9.0, gapBefore = true), SeriesPoint(10.0, 2.0))

    @Test
    fun theNearestPointIsTheClosestByTime() {
        assertEquals(1, nearestIndex(points, 5.0))
        assertEquals(2, nearestIndex(points, 7.0))
        assertEquals(1, nearestIndex(points, 4.0))
    }

    @Test
    fun aTieGoesToTheEarlierPoint() {
        assertEquals(0, nearestIndex(points, 2.0))
    }

    @Test
    fun beforeTheStartOrAfterTheEndIsTheEndPoint() {
        assertEquals(0, nearestIndex(points, -3.0))
        assertEquals(3, nearestIndex(points, 12.0))
    }

    @Test
    fun aGapDoesNotChangeWhichPointIsNearest() {
        // The point after a gap is still read out when the pointer is closest to it.
        assertEquals(2, nearestIndex(points, 7.9))
    }

    @Test
    fun thePointerMapsBackToItsTime() {
        assertEquals(5.0, frame.valueAt(frame.x(5.0)), 1e-9)
        val rtl = frame.copy(start = 100f, direction = -1, startPadding = 4.0)
        assertEquals(96f, rtl.x(0.0))
        assertEquals(3.0, rtl.valueAt(rtl.x(3.0)), 1e-9)
    }

    @Test
    fun dotsGoOnTheLineAndOnOtherLinesAtTheSameTime() {
        val others = listOf(listOf(SeriesPoint(4.0, 2.0)), listOf(SeriesPoint(3.0, 7.0), SeriesPoint(5.0, 7.0)))
        assertEquals(listOf(Offset(40f, 50f), Offset(40f, 80f)), readoutDots(points[1], others, frame, 0.0..10.0))
    }

    @Test
    fun dotsStayInTheLayer() {
        assertEquals(listOf(Offset(40f, 0f)), readoutDots(SeriesPoint(4.0, 15.0), emptyList(), frame, 0.0..10.0))
    }

    @Test
    fun rightToLeftDotsRunFromTheRight() {
        val rtl = frame.copy(start = 100f, direction = -1)
        assertEquals(listOf(Offset(60f, 50f)), readoutDots(points[1], emptyList(), rtl, 0.0..10.0))
    }

    @Test
    fun theLabelSitsCentredAboveTheTopDot() {
        assertEquals(Offset(30f, 30f), labelTopLeft(listOf(Offset(50f, 60f), Offset(50f, 80f)), Size(40f, 30f), bounds))
    }

    @Test
    fun theLabelFlipsBelowWhenItWouldCrossTheTop() {
        assertEquals(Offset(30f, 20f), labelTopLeft(listOf(Offset(50f, 20f)), Size(40f, 30f), bounds))
    }

    @Test
    fun theLabelStaysInsideAtEitherEdge() {
        assertEquals(Offset(0f, 30f), labelTopLeft(listOf(Offset(5f, 60f)), Size(40f, 30f), bounds))
        assertEquals(Offset(60f, 30f), labelTopLeft(listOf(Offset(95f, 60f)), Size(40f, 30f), bounds))
    }
}
