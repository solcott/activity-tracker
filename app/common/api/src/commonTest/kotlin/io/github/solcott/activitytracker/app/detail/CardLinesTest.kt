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
    fun aPaceThatNeverMovesHasNoChart() {
        val still = LineSeries(StreamKind.Speed, samples(0.0, 0.0), average = 0.0, max = 0.0)
        assertNull(CardLines.chart(still, 600, PaceKind.Distance, UnitSystem.Metric))
    }

    @Test
    fun aStandstillAverageCapsStopsAtTheSlowestMovingPace() {
        // 3 m/s is 5:33 /km; the stop is held at 1.5 times that, not drawn at an infinite pace.
        val speed = LineSeries(StreamKind.Speed, samples(3_000.0, 0.0), average = 0.0, max = 3_000.0)
        val chart = CardLines.chart(speed, 600, PaceKind.Distance, UnitSystem.Metric)!!
        assertEquals(chart.points[0].y * 1.5, chart.points[1].y, 1e-6)
        assertTrue(chart.axis.min > -10_000, "${chart.axis.min}")
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
    fun aZoneReadsItsShareAndOneSentence() {
        val zones = listOf(ZoneTime("2", ZoneName.Aerobic, 120, 145, 2_100), ZoneTime("5", ZoneName.Aerobic, 175, null, 9))
        val rows = ZoneRows.of(zones, DetailSection.HeartRate)
        assertEquals(named("unit_percent", written("100")), rows[0].percent)
        assertEquals(named("chart_zone_percent_under"), rows[1].percent)
        assertEquals(named("chart_zone_title", named("chart_zone_label", written("2")), rows[0].name), rows[0].title)
        assertEquals(named("chart_zone_spoken", rows[0].title, rows[0].range, rows[0].time, rows[0].percent), rows[0].spoken)
    }

    @Test
    fun aZoneShareRoundsAndNoneReadsZero() {
        assertEquals(named("unit_percent", written("58")), ZoneRows.percent(0.584))
        assertEquals(named("unit_percent", written("0")), ZoneRows.percent(0.0))
        assertEquals(named("unit_percent", written("1")), ZoneRows.percent(0.005))
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

    @Test
    fun anEffortLineHasTheElevationBehindIt() {
        val elevation = LineSeries(StreamKind.Elevation, samples(100.0, 160.0, 120.0), average = null, max = 160.0)
        val hr = LineSeries(StreamKind.HeartRate, samples(120.0, 140.0, 160.0), average = 140.0, max = 160.0)
        val card = MetricCard(DetailSection.HeartRate, persistentListOf(), series = persistentListOf(hr))
        val chart = CardLines.of(card, charts(hr, elevation), UnitSystem.Metric).single().chart
        assertEquals(3, chart.backdrop.size)
        assertEquals(chart.axis.min, chart.backdrop[0].y)
        assertEquals(
            named("chart_marker_elevation", written("1:00"), named("unit_bpm", written("140")), named("unit_elevation_m", written("160"))),
            chart.readouts[1],
        )
        // Elevation itself draws none behind it.
        val own = MetricCard(DetailSection.Elevation, persistentListOf(), series = persistentListOf(elevation))
        assertTrue(CardLines.of(own, charts(hr, elevation), UnitSystem.Metric).single().chart.backdrop.isEmpty())
    }

    @Test
    fun aPairDrawsItsRightLegAsASecondLine() {
        val right = LineSeries(StreamKind.PedalSmoothness, samples(20.0, 22.0), average = 21.0, max = 22.0)
        val left = LineSeries(StreamKind.PedalSmoothness, samples(18.0, 19.0), average = 18.5, max = 19.0, right = right)
        val card = MetricCard(DetailSection.CyclingDynamics, persistentListOf(), series = persistentListOf(left))
        val line = CardLines.of(card, charts(left), UnitSystem.Metric).single()
        assertEquals(listOf(20.0, 22.0), line.chart.others.single().points.map { it.y })
        assertTrue(line.chart.axis.max >= 22.0)
        assertTrue(line.detail != null)
    }

    @Test
    fun everyTankIsOnOneChart() {
        val one = LineSeries(StreamKind.TankPressure, samples(200.0, 150.0), average = null, max = 200.0)
        val two = LineSeries(StreamKind.TankPressure, samples(210.0, 190.0), average = null, max = 210.0)
        val card = MetricCard(DetailSection.TankPressure, persistentListOf(), series = persistentListOf(one, two))
        val line = CardLines.of(card, charts(), UnitSystem.Metric).single()
        assertEquals(1, line.chart.otherCount)
        assertNull(line.title)
        assertEquals(
            named("text_dot_pair", named("unit_pressure_bar", written("200")), named("unit_pressure_bar", written("210"))),
            line.detail,
        )
    }

    @Test
    fun aDiveMarksItsSwitchesAndWarningsAndDrawsItsCeiling() {
        val depth = LineSeries(StreamKind.Depth, samples(0.0, 20.0, 30.0, 5.0), average = 15.0, max = 30.0)
        val card = MetricCard(DetailSection.Depth, persistentListOf(), series = persistentListOf(depth))
        val dive =
            charts(depth)
                .copy(
                    gasSwitches = persistentListOf(GasSwitchPoint(120, 50, 0)),
                    alerts = persistentListOf(DiveAlertPoint(150, "ascent_rate")),
                    ceiling = persistentListOf(ChartSample(60, 3.0), ChartSample(120, 6.0)),
                )
        val chart = CardLines.of(card, dive, UnitSystem.Metric).single().chart
        assertTrue(chart.fillAbove)
        assertEquals(listOf(120.0, 150.0), chart.marks.map { it.x })
        assertEquals(listOf(false, true), chart.marks.map { it.warning })
        assertEquals(-6.0, chart.others.single().points[1].y)
    }

    @Test
    fun assistModeIsInSteps() {
        val mode = LineSeries(StreamKind.EbikeAssistMode, samples(1.0, 3.0, 2.0), average = null, max = 3.0)
        val card = MetricCard(DetailSection.EBike, persistentListOf(), series = persistentListOf(mode))
        assertTrue(CardLines.of(card, charts(mode), UnitSystem.Metric).single().chart.steps)
    }

    @Test
    fun thePowerCurveFollowsThePowerLine() {
        val power = LineSeries(StreamKind.Power, samples(200.0, 250.0), average = 225.0, max = 250.0)
        val curve = persistentListOf(CurvePoint(5, 600), CurvePoint(30, 400), CurvePoint(60, 350), CurvePoint(1200, 260))
        val card = MetricCard(DetailSection.Power, persistentListOf(), series = persistentListOf(power), powerCurve = curve)
        val lines = CardLines.of(card, charts(power), UnitSystem.Metric)
        val curveChart = lines[1]
        assertEquals(named("chart_power_curve"), curveChart.title)
        assertEquals(listOf(0.0, 2.0, 3.0), curveChart.chart.xTicks.map { it.value })
        assertEquals(
            listOf(
                named("unit_duration_seconds", written("5")),
                named("unit_duration_minutes", written("1")),
                named("unit_duration_minutes", written("20")),
            ),
            curveChart.chart.xTicks.map { it.label },
        )
    }

    @Test
    fun gearsHoldToTheEndInSteps() {
        val gears = persistentListOf(GearPoint(0, 2, 5), GearPoint(120, 2, 3))
        val card = MetricCard(DetailSection.Gears, persistentListOf(), gears = gears)
        val chart = CardLines.of(card, charts(), UnitSystem.Metric).single().chart
        assertTrue(chart.steps)
        assertEquals(listOf(0.0, 120.0, 600.0), chart.points.map { it.x })
        assertEquals(listOf(5.0, 3.0, 3.0), chart.points.map { it.y })
    }
}
