package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.chart.ChartDot
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.ThresholdEntry
import io.github.solcott.activitytracker.lexicon.app.ThresholdSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

class ThresholdHistoryTextsTest {
    private val since = HistoryDate(2026, 3, 2)

    private fun section(
        threshold: String,
        values: List<Long>,
        chart: ThresholdChart? = null,
        change: ThresholdTrend? = null,
        unit: PaceUnit = PaceUnit.PerKilometer,
    ) =
        ThresholdHistorySection(
            threshold = threshold,
            sport = null,
            paceUnit = unit,
            rows =
                values
                    .mapIndexed { i, v -> ThresholdHistoryRow(ThresholdEntry(threshold, v), HistoryDate(2026, 5, 1 + i), i == 0) }
                    .toImmutableList(),
            showingAll = false,
            chart = chart,
            undatedCount = 0,
            change = change,
        )

    private val powerChart =
        ThresholdChart(
            persistentListOf(ChartPoint(0f, 0f, 230, current = false), ChartPoint(0.8f, 1f, 250, current = true)),
            start = since,
            lowest = 230,
            highest = 250,
        )

    @Test
    fun powerRoseSinceItsFirstDate() {
        val power = section(Threshold.FUNCTIONAL_THRESHOLD_POWER, listOf(250, 230), powerChart, ThresholdTrend(230, 250, since))
        assertEquals(
            AppText.named(
                "zones_history_change_up",
                AppText.named("zones_summary_value_watts", AppText.written("20")),
                AppText.named("zones_history_date_short", AppText.named("month_short_3"), AppText.written("2")),
            ),
            ThresholdHistoryTexts.change(power),
        )
        assertTrue(ThresholdHistoryTexts.changed(power))
    }

    @Test
    fun aFasterPaceIsSecondsPerUnit() {
        // 3.0 to 3.2 m/s is 536 to 503 seconds a mile.
        val pace = section(Threshold.THRESHOLD_SPEED, listOf(3_200, 3_000), change = ThresholdTrend(3_000, 3_200, since), unit = PaceUnit.PerMile)
        val change = assertNotNull(ThresholdHistoryTexts.change(pace)) as AppText.Named
        assertEquals("zones_history_change_faster", change.name)
        assertEquals(AppText.written("33"), change.args[0])
    }

    @Test
    fun aSingleValueSaysSoAndHasNoChart() {
        val only = section(Threshold.MAX_HEART_RATE, listOf(192))
        assertEquals(AppText.named("zones_history_only_value"), ThresholdHistoryTexts.change(only))
        assertFalse(ThresholdHistoryTexts.changed(only))
        assertNull(ThresholdHistoryTexts.chart(only, detailed = true))
    }

    @Test
    fun theDetailedChartStepsOnToTodayWithADotOnEachValue() {
        val power = section(Threshold.FUNCTIONAL_THRESHOLD_POWER, listOf(250, 230), powerChart, ThresholdTrend(230, 250, since))
        val chart = assertNotNull(ThresholdHistoryTexts.chart(power, detailed = true))
        assertTrue(chart.steps)
        // The last value holds until today, at the right edge, with no dot of its own.
        assertEquals(listOf(0.0, 0.8f.toDouble(), 1.0), chart.points.map { it.x })
        assertEquals(chart.points[1].y, chart.points[2].y)
        assertEquals(
            listOf(ChartDot(0, false, AppText.written("230")), ChartDot(1, true, AppText.written("250"))),
            chart.dots,
        )
        assertEquals(listOf(AppText.written("230"), AppText.written("250")), chart.yTicks.map { it.label })
        assertTrue(chart.axis.max > 1.0 && chart.axis.min < 0.0)
    }

    @Test
    fun aSparklineHasNoDotsOrTicks() {
        val power = section(Threshold.FUNCTIONAL_THRESHOLD_POWER, listOf(250, 230), powerChart)
        val chart = assertNotNull(ThresholdHistoryTexts.chart(power, detailed = false))
        assertTrue(chart.dots.isEmpty())
        assertTrue(chart.yTicks.isEmpty())
    }

    @Test
    fun aDeviceSourceNamesTheDevice() {
        val row = ThresholdHistoryRow(ThresholdEntry(Threshold.MAX_HEART_RATE, 190, source = ThresholdSource.DEVICE, device = "Edge 840"), null, true)
        assertEquals(AppText.named("threshold_source_device_named", AppText.written("Edge 840")), ThresholdHistoryTexts.source(row))
        assertEquals(AppText.named("zones_history_date_unknown"), ThresholdHistoryTexts.date(row))
    }
}
