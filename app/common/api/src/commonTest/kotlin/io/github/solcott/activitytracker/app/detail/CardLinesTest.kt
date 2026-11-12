package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.app.text.AppText.Companion.named
import io.github.solcott.activitytracker.app.text.AppText.Companion.written
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.training.ZoneName
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** A metric card's lines, zones and laps as both UIs read them (feed/activity-page-layout, dev/shared-display-text). */
class CardLinesTest {
    private fun samples(vararg values: Double) = values.mapIndexed { i, v -> ChartSample(i * 60, v) }.toImmutableList()

    private fun charts(vararg lines: LineSeries) =
        ActivityCharts(
            visibility = "public",
            durationSeconds = 600,
            lines = lines.toList().toImmutableList(),
            heartRateZones = null,
            powerZones = null,
            normalizedPower = null,
            powerCurve = persistentListOf(),
            gears = persistentListOf(),
        )

    @Test
    fun aLoneLineLeavesItsTitleAndDetailToTheCard() {
        val hr = LineSeries(StreamKind.HeartRate, samples(120.0, 140.0, 160.0), average = 140.0, max = 160.0)
        val card = MetricCard(DetailSection.HeartRate, persistentListOf(), series = persistentListOf(hr))
        val line = CardLines.of(card, charts(hr), UnitSystem.Metric).single()
        assertNull(line.title)
        assertNull(line.detail)
        assertEquals(140.0, line.chart.average)
        assertEquals(named("chart_marker", written("1:00"), named("unit_bpm", written("140"))), line.chart.readouts[1])
    }

    @Test
    fun gradeUnderElevationIsTitled() {
        val elevation = LineSeries(StreamKind.Elevation, samples(100.0, 120.0), average = null, max = 120.0)
        val grade = LineSeries(StreamKind.Grade, samples(2.0, 4.0), average = null, max = 4.0)
        val card = MetricCard(DetailSection.Elevation, persistentListOf(), series = persistentListOf(elevation, grade))
        val lines = CardLines.of(card, charts(elevation, grade), UnitSystem.Metric)
        assertNull(lines[0].title)
        assertEquals(named("chart_grade"), lines[1].title)
        assertEquals(named("chart_max_only", named("unit_percent", written("4.0"))), lines[1].detail)
    }

    @Test
    fun aPaceLineIsInvertedTheFasterAtTheTop() {
        // 4 m/s is 4:10 /km, 3 m/s 5:33 /km.
        val speed = LineSeries(StreamKind.Speed, samples(4_000.0, 3_000.0), average = 3_500.0, max = 4_000.0)
        val chart = CardLines.chart(speed, 600, PaceKind.Distance, UnitSystem.Metric)!!
        assertTrue(chart.points[0].y > chart.points[1].y, "the faster pace plots higher")
        assertTrue(chart.axis.max <= 0)
        chart.yTicks.forEach { assertEquals(written(StatText.duration((-it.value).toLong())), it.label) }
        assertEquals(
            named("chart_description_pace", named("unit_pace_per_km", written("4:46")), named("unit_pace_per_km", written("4:10"))),
            chart.description,
        )
    }

    @Test
    fun timeTicksStepRoundly() {
        assertEquals(listOf(0.0, 600.0, 1200.0, 1800.0, 2400.0), CardLines.timeTicks(2_700).map { it.value })
        assertEquals(written("10:00"), CardLines.timeTicks(2_700)[1].label)
    }

    @Test
    fun zonesReadTheirRangeAndTime() {
        val zones = listOf(ZoneTime("2", ZoneName.Aerobic, 150, 200, 720), ZoneTime("5", ZoneName.Aerobic, 300, null, 240))
        val rows = ZoneRows.of(zones, DetailSection.Power)
        assertEquals(named("unit_watts", named("chart_zone_range", written("150"), written("200"))), rows[0].range)
        assertEquals(written("12:00"), rows[0].time)
        assertEquals(0.75, rows[0].fraction)
        assertEquals(named("unit_watts", named("chart_zone_range_open", written("300"))), rows[1].range)
    }

    @Test
    fun aSkiDaysLapsAreRunsAndLifts() {
        val run =
            LapRow(1, elapsedSeconds = 200, movingSeconds = null, distance = 1.2, speed = 30.0, null, null, null, null, descent = 300.0)
        val lift = LapRow(2, 400, null, 1.0, 10.0, null, null, null, null, intensity = "rest", liftName = "Summit Quad")
        val table = LapTableTexts.of(listOf(run, lift), ski = true, UnitSystem.Metric)
        assertEquals(named("lap_run_number", written("1")), table.rows[0].cells[0])
        assertEquals(written("Summit Quad"), table.rows[1].cells[0])
        assertEquals(named("value_missing"), table.rows[1].cells.last())
        assertTrue(table.wideFirst)
    }
}
