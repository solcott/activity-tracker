package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.chart.BandStop
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.toImmutableList

class SegmentProfileChartsTest {
    private fun profile(distanceMeters: Long, heights: List<Double>, grades: List<Double>) =
        SegmentProfile(heights.toImmutableList(), distanceMeters, grades.toImmutableList())

    @Test
    fun distanceLabelsAreRoundSteps() {
        assertEquals(listOf(0.0, 0.5, 1.0, 1.5), distanceLabels(1.6))
        assertEquals(listOf(0.0, 0.2, 0.4, 0.6, 0.8), distanceLabels(0.99).map { (it * 10).roundToInt() / 10.0 })
        assertEquals(listOf(0.0, 10.0, 20.0, 30.0, 40.0, 50.0), distanceLabels(50.0))
        assertEquals(listOf(0.0, 0.25, 0.5, 0.75, 1.0), distanceLabels(1.2))
    }

    @Test
    fun aChartInTheViewersUnits() {
        val chart = profile(1_000, listOf(100.0, 110.0, 130.0), listOf(2.0, 3.0, 4.0)).chart(UnitSystem.Imperial)
        assertEquals(0.62, (chart.xMax * 100).roundToInt() / 100.0)
        assertEquals(328.0, chart.points.first().y.roundToInt().toDouble())
        assertEquals(AppText.named("unit_distance_mi", AppText.written("0.2")), chart.xTicks[1].label)
        assertEquals(AppText.named("unit_elevation_ft", AppText.written("300")), chart.yTicks.first().label)
    }

    @Test
    fun eachPointReadsOutItsDistanceElevationAndGrade() {
        val chart = profile(1_000, listOf(100.0, 110.0, 130.0), listOf(2.0, 3.0, 4.0)).chart(UnitSystem.Metric)
        assertEquals(
            AppText.named(
                "segment_profile_readout",
                AppText.named("unit_distance_km", AppText.written("0.5")),
                AppText.named("unit_elevation_m", AppText.written("110")),
                AppText.named("unit_percent", AppText.written("3.0")),
            ),
            chart.readout(0.45),
        )
    }

    @Test
    fun tintedByGradeBandRuns() {
        val chart = profile(1_000, List(6) { it * 10.0 }, listOf(1.0, 1.0, 1.0, 12.0, 12.0, 12.0)).chart(UnitSystem.Metric)
        assertEquals(listOf(BandStop(0f, 0), BandStop(0.5f, 3)), chart.bands)
    }
}
