package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.detail.ActivityCharts
import io.github.solcott.activitytracker.app.detail.ActivityPage
import io.github.solcott.activitytracker.app.detail.AvgMaxMetric
import io.github.solcott.activitytracker.app.detail.ChartSample
import io.github.solcott.activitytracker.app.detail.DetailSection
import io.github.solcott.activitytracker.app.detail.FigureKey
import io.github.solcott.activitytracker.app.detail.FigureValue
import io.github.solcott.activitytracker.app.detail.LineSeries
import io.github.solcott.activitytracker.app.detail.PaceKind
import io.github.solcott.activitytracker.app.detail.SetRow
import io.github.solcott.activitytracker.app.detail.StatsGroupKind
import io.github.solcott.activitytracker.app.detail.StreamKind
import io.github.solcott.activitytracker.app.detail.TrainingLoadStats
import io.github.solcott.activitytracker.app.detail.ZoneTime
import io.github.solcott.activitytracker.data.training.ZoneName
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivitySensor
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

class ActivityPageTest {
    // Hero figures by sport.

    @Test
    fun `a mountain bike ride with power`() {
        val page = ActivityPage.build(mtb, rideCharts())
        assertEquals(
            listOf(
                FigureKey.Distance,
                FigureKey.MovingTime,
                FigureKey.ElevationGain,
                FigureKey.AvgSpeed,
                FigureKey.AvgPower,
                FigureKey.Calories,
            ),
            page.hero.map { it.key },
        )
    }

    @Test
    fun `a ride whose power the viewer may not see leaves no gap`() {
        val page = ActivityPage.build(mtb.copy(avgPower = null, maxPower = null, normalizedPower = null, work = null), null)
        assertEquals(
            listOf(FigureKey.Distance, FigureKey.MovingTime, FigureKey.ElevationGain, FigureKey.AvgSpeed, FigureKey.Calories),
            page.hero.map { it.key },
        )
    }

    @Test
    fun `a run`() {
        val run = record(Sport.RUN).copy(distance = 10_000, movingTime = 3000, elevationGain = 80, avgHeartRate = 150, calories = 700)
        assertEquals(
            listOf(
                FigureKey.Distance,
                FigureKey.MovingTime,
                FigureKey.AvgPace,
                FigureKey.ElevationGain,
                FigureKey.AvgHeartRate,
                FigureKey.Calories,
            ),
            ActivityPage.build(run, null).hero.map { it.key },
        )
    }

    @Test
    fun `a swim paces per 100`() {
        val swim = record(Sport.SWIM).copy(distance = 2000, movingTime = 2400, avgHeartRate = 130, calories = 500)
        val hero = ActivityPage.build(swim, null).hero
        assertEquals(
            listOf(FigureKey.Distance, FigureKey.MovingTime, FigureKey.AvgPace, FigureKey.AvgHeartRate, FigureKey.Calories),
            hero.map { it.key },
        )
        assertEquals(true, (hero[2].value as FigureValue.Pace).swim)
    }

    @Test
    fun `a dive`() {
        val dive = record(Sport.DIVE).copy(dive = ActivityDive(maxDepth = 2450, avgDepth = 1200, minTemperature = 245))
        assertEquals(
            listOf(FigureKey.MaxDepth, FigureKey.DiveTime, FigureKey.WaterTemp, FigureKey.AvgDepth),
            ActivityPage.build(dive, null).hero.map { it.key },
        )
    }

    @Test
    fun `a ski day`() {
        val ski = record(Sport.SKI).copy(elevationLoss = 3200, movingTime = 9000, maxSpeed = 21_000)
        val charts = charts().copy(skiRuns = 12)
        assertEquals(
            listOf(FigureKey.Runs, FigureKey.Descent, FigureKey.MovingTime, FigureKey.MaxSpeed),
            ActivityPage.build(ski, charts).hero.map { it.key },
        )
    }

    @Test
    fun `a strength workout with sets shows no distance`() {
        val strength = record(Sport.STRENGTH).copy(distance = 0, avgHeartRate = 110, calories = 300)
        val sets =
            persistentListOf(
                SetRow(1, false, "squat", "squat", 10, 60_000, 40),
                SetRow(null, true, null, null, null, null, 90),
                SetRow(2, false, "squat", "squat", 8, 60_000, 35),
            )
        val page = ActivityPage.build(strength, charts().copy(sets = sets))
        assertEquals(listOf(FigureKey.Time, FigureKey.AvgHeartRate, FigureKey.Calories, FigureKey.Sets), page.hero.map { it.key })
        assertEquals(FigureValue.Count(2), page.hero.last().value)
    }

    @Test
    fun `another sport takes the ride's order without power`() {
        val row = record(Sport.ROW).copy(distance = 5000, movingTime = 1500, elevationGain = 0, avgPower = 180, calories = 350)
        val hero = ActivityPage.build(row, null).hero
        assertEquals(
            // Its own rate (rowing's is a speed in the catalog), and its power left out.
            listOf(FigureKey.Distance, FigureKey.MovingTime, FigureKey.ElevationGain, FigureKey.AvgSpeed, FigureKey.Calories),
            hero.map { it.key },
        )
    }

    // Cards, training load, stats groups and avg/max rows.

    @Test
    fun `a ride the viewer may see in full`() {
        val page = ActivityPage.build(mtb, rideCharts())
        assertEquals(
            listOf(DetailSection.HeartRate, DetailSection.Power, DetailSection.Speed, DetailSection.Cadence, DetailSection.Elevation),
            page.cards.map { it.section },
        )
        assertTrue(page.cards.all { it.series.isNotEmpty() })
        val power = page.cards.first { it.section == DetailSection.Power }
        assertEquals(listOf(FigureKey.Avg, FigureKey.NormalizedPower, FigureKey.Max), power.headline.map { it.key })
        assertEquals(FigureKey.Work, power.rows.first().key)
        assertTrue(power.zones != null)
        assertEquals(listOf(FigureKey.Ftp, FigureKey.TrainingStressScore), page.trainingLoad.map { it.key })
        assertEquals(
            listOf(AvgMaxMetric.HeartRate, AvgMaxMetric.Power, AvgMaxMetric.Speed, AvgMaxMetric.Cadence),
            page.avgMax.map { it.metric },
        )
        assertTrue(page.cards.all { it.mark == null })
        // Every hero figure is in the stats list too.
        val listed = page.stats.flatMap { it.rows }.map { it.key }
        assertTrue(page.hero.all { it.key in listed })
        val counts = page.stats.first { it.kind == StatsGroupKind.Counts }.rows.map { it.key }
        assertEquals(listOf(FigureKey.Shifts, FigureKey.Jumps), counts)
        assertEquals(FigureKey.Descent, page.stats.first { it.kind == StatsGroupKind.Elevation }.rows.last().key)
        val device = page.stats.first { it.kind == StatsGroupKind.Device }.rows
        assertEquals(listOf(FigureKey.Device, FigureKey.Sensor), device.map { it.key })
    }

    @Test
    fun `a viewer without heart rate or zones`() {
        val served = mtb.copy(avgHeartRate = null, maxHeartRate = null)
        val charts = rideCharts(heartRate = false).copy(trainingLoad = null, powerZones = null)
        val page = ActivityPage.build(served, charts)
        assertTrue(page.cards.none { it.section == DetailSection.HeartRate })
        assertTrue(page.trainingLoad.isEmpty())
        assertTrue(page.stats.none { it.kind == StatsGroupKind.HeartRate })
        assertTrue(page.avgMax.none { it.metric == AvgMaxMetric.HeartRate })
        assertNull(page.cards.first { it.section == DetailSection.Power }.zones)
    }

    @Test
    fun `the owner sees a narrower category marked on its card only`() {
        val marks = mapOf(DetailCategory.HEART_RATE to Visibility.SELF)
        val page = ActivityPage.build(mtb, rideCharts(), marks)
        assertEquals(Visibility.SELF, page.cards.first { it.section == DetailSection.HeartRate }.mark)
        assertTrue(page.cards.filter { it.section != DetailSection.HeartRate }.all { it.mark == null })
    }

    @Test
    fun `the owner sees a narrower start time and map where they show`() {
        val marks = mapOf(DetailCategory.START_TIME to Visibility.FOLLOWERS, DetailCategory.MAP to Visibility.SELF)
        val page = ActivityPage.build(mtb.copy(polyline = "abc"), null, marks)
        val start = page.stats.first { it.kind == StatsGroupKind.Timing }.rows.first()
        assertEquals(FigureKey.StartTime, start.key)
        assertEquals(Visibility.FOLLOWERS, start.mark)
        assertEquals(Visibility.SELF, page.mapMark)
    }

    @Test
    fun `groups without figures are left out`() {
        // No elevation gain recorded, so no Elevation group: unlike distance, it isn't shown as a dash.
        val walk = record(Sport.WALK).copy(distance = 3000, movingTime = 2400)
        assertEquals(
            listOf(StatsGroupKind.Timing, StatsGroupKind.DistanceSpeed),
            ActivityPage.build(walk, null).stats.map { it.kind },
        )
    }

    @Test
    fun `a manual run without streams has a hero and stats but no cards`() {
        val run = record(Sport.RUN).copy(distance = 5000, movingTime = 1500)
        val page = ActivityPage.build(run, null)
        assertTrue(page.cards.isEmpty())
        assertTrue(page.hero.isNotEmpty())
        assertTrue(page.stats.isNotEmpty())
        assertTrue(page.avgMax.none { it.metric == AvgMaxMetric.HeartRate })
    }

    @Test
    fun `a dive's computer summary is its own group`() {
        val dive =
            record(Sport.DIVE)
                .copy(dive = ActivityDive(maxDepth = 2450, bottomTime = 2400, surfaceInterval = 5400, startCns = 2, endCns = 9))
        val rows = ActivityPage.build(dive, null).stats.first { it.kind == StatsGroupKind.Dive }.rows.map { it.key }
        assertEquals(listOf(FigureKey.MaxDepth, FigureKey.BottomTime, FigureKey.SurfaceInterval, FigureKey.Cns), rows)
    }

    @Test
    fun `a run's speed card gives its pace`() {
        val run = record(Sport.RUN).copy(distance = 10_000, movingTime = 3000, avgSpeed = 3330, maxSpeed = 4500)
        val card = ActivityPage.build(run, charts().copy(lines = persistentListOf(line(StreamKind.Speed, 3300.0)))).cards.single()
        assertEquals(PaceKind.Distance, card.pace)
        assertEquals(listOf(FigureKey.Avg, FigureKey.Best), card.headline.map { it.key })
        assertEquals(listOf(FigureValue.Pace(3330, false), FigureValue.Pace(4500, false)), card.headline.map { it.value })
    }

    @Test
    fun `a swim's speed card paces per 100 and a ride's stays a speed`() {
        val swim = record(Sport.SWIM).copy(distance = 1000, movingTime = 1200)
        val speed = charts().copy(lines = persistentListOf(line(StreamKind.Speed, 800.0)))
        assertEquals(PaceKind.Swim, ActivityPage.build(swim, speed).cards.single().pace)
        assertNull(ActivityPage.build(mtb, rideCharts()).cards.single { it.section == DetailSection.Speed }.pace)
    }

    @Test
    fun `a pool swim lists no elevation`() {
        val pool = record(Sport.SWIM).copy(subSport = "pool", distance = 1000, movingTime = 1200)
        assertTrue(ActivityPage.build(pool, null).stats.none { it.kind == StatsGroupKind.Elevation })
        assertTrue(ActivityPage.build(pool.copy(elevationGain = 0), null).stats.none { it.kind == StatsGroupKind.Elevation })
    }

    @Test
    fun `a ride without an elevation gain shows none, and one of 0 shows it`() {
        val flat = mtb.copy(elevationGain = null, elevationLoss = null)
        assertTrue(ActivityPage.build(flat, null).hero.none { it.key == FigureKey.ElevationGain })
        assertTrue(ActivityPage.build(flat, null).stats.none { it.kind == StatsGroupKind.Elevation })
        assertEquals(
            FigureValue.Elevation(0.0),
            ActivityPage.build(flat.copy(elevationGain = 0), null).hero.single { it.key == FigureKey.ElevationGain }.value,
        )
    }

    @Test
    fun `a card's chart marks the average its headline gives`() {
        // The stream's samples average 805 mm/s; the swim stores 800, as the headline shows.
        val swim = record(Sport.SWIM).copy(distance = 1000, movingTime = 1250, avgSpeed = 800)
        val page = ActivityPage.build(swim, charts().copy(lines = persistentListOf(line(StreamKind.Speed, 800.0))))
        val series = page.cards.single().series.single()
        assertEquals(800.0, series.average)
        assertEquals(FigureValue.Pace(800, true), page.cards.single().headline.first().value)
        // Nothing stored for the maximum, so the stream's stays.
        assertEquals(810.0, series.max)
        // The page's own lines keep the stream's figures.
        assertEquals(805.0, page.charts?.lines?.single()?.average)
    }

    @Test
    fun `a card without a stored figure keeps the stream's`() {
        val ride = record(Sport.RIDE).copy(avgTemperature = 200)
        val lines = persistentListOf(line(StreamKind.HeartRate, 140.0), line(StreamKind.Temperature, 180.0))
        val cards = ActivityPage.build(ride, charts().copy(lines = lines)).cards
        assertEquals(145.0, cards.single { it.section == DetailSection.HeartRate }.series.single().average)
        val temperature = cards.single { it.section == DetailSection.Temperature }.series.single()
        assertEquals(200.0, temperature.average)
        assertEquals(190.0, temperature.max)
    }

    private fun record(sport: String) = ActivityRecord(sport, START, 3600, START)

    private val mtb =
        record(Sport.RIDE)
            .copy(
                subSport = "mountain",
                distance = 30_000,
                movingTime = 7200,
                elevationGain = 900,
                elevationLoss = 880,
                avgSpeed = 4170,
                maxSpeed = 15_000,
                avgHeartRate = 145,
                maxHeartRate = 180,
                avgPower = 210,
                maxPower = 700,
                normalizedPower = 240,
                work = 1500,
                avgCadence = 75,
                maxCadence = 110,
                calories = 1500,
                rearShifts = 300,
                frontShifts = 0,
                jumps = 12,
                device = "Edge 1050",
                sensors = listOf(ActivitySensor(kind = "heartRate", manufacturer = "Garmin", model = "HRM 200")),
            )

    private fun charts() =
        ActivityCharts(
            visibility = Visibility.PUBLIC,
            durationSeconds = 7200,
            lines = persistentListOf(),
            heartRateZones = null,
            powerZones = null,
            normalizedPower = null,
            powerCurve = persistentListOf(),
            gears = persistentListOf(),
        )

    private fun rideCharts(heartRate: Boolean = true) =
        charts()
            .copy(
                lines =
                    listOfNotNull(
                            line(StreamKind.HeartRate, 140.0).takeIf { heartRate },
                            line(StreamKind.Power, 200.0),
                            line(StreamKind.Speed, 4000.0),
                            line(StreamKind.Cadence, 70.0),
                            line(StreamKind.Elevation, 500.0),
                        )
                        .toImmutableList(),
                heartRateZones = zones.takeIf { heartRate },
                powerZones = zones,
                normalizedPower = 240,
                trainingLoad = TrainingLoadStats(ftp = 250, intensityFactor = null, trainingStressScore = 120.0, null, null),
            )

    private fun line(kind: StreamKind, value: Double) =
        LineSeries(kind, persistentListOf(ChartSample(0, value), ChartSample(60, value + 10)), value + 5, value + 10)

    private val zones = persistentListOf(ZoneTime("1", ZoneName.entries.first(), 0, null, 3600))

    private companion object {
        val START: Instant = Instant.parse("2026-05-02T14:30:00Z")
    }
}
