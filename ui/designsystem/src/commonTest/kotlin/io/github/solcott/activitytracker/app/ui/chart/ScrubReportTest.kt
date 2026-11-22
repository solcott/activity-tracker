package io.github.solcott.activitytracker.app.ui.chart

import kotlin.test.Test
import kotlin.test.assertEquals

/** What a chart tells its page as its readout moves ([reportScrub]): the marked point's x when it changes, and null when it goes. */
class ScrubReportTest {
    private val points = listOf(SeriesPoint(0.0, 1.0), SeriesPoint(10.0, 2.0), SeriesPoint(20.0, 3.0))

    private fun reports(vararg xs: Double): List<Double?> {
        val told = mutableListOf<Double?>()
        var before = Double.NaN
        for (x in xs) {
            reportScrub(before, x, points, told::add)
            before = x
        }
        return told
    }

    @Test
    fun aMoveReportsTheNearestPointOnlyWhenItChanges() {
        assertEquals(listOf(0.0, 10.0, 20.0), reports(1.0, 2.0, 9.0, 11.0, 19.0))
    }

    @Test
    fun leavingReportsNullOnce() {
        assertEquals(listOf(10.0, null), reports(9.0, Double.NaN, Double.NaN))
    }

    @Test
    fun noPointsNoReports() {
        val told = mutableListOf<Double?>()
        reportScrub(Double.NaN, 3.0, emptyList(), told::add)
        assertEquals(emptyList(), told)
    }
}
