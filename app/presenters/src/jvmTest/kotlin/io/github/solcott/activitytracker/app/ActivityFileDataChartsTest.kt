package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.detail.ActivityCharts
import io.github.solcott.activitytracker.app.detail.ActivityDetailPresenter
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.app.detail.DetailSection
import io.github.solcott.activitytracker.app.detail.DiveAlertPoint
import io.github.solcott.activitytracker.app.detail.GasKind
import io.github.solcott.activitytracker.app.detail.GasSwitchPoint
import io.github.solcott.activitytracker.app.detail.StreamKind
import io.github.solcott.activitytracker.app.detail.TrainingLoadStats
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.DiveAlertKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** What the detail page charts from the data a FIT or UDDF file kept: dive state, tanks, gas switches, running dynamics, laps, load. */
class ActivityFileDataChartsTest : ActivityDetailTestBase() {
    private suspend fun loaded(units: UnitSystem = UnitSystem.Metric): Pair<ActivityDetailState.Loaded, ActivityCharts> {
        val r = repos(units = units)
        var result: Pair<ActivityDetailState.Loaded, ActivityCharts>? = null
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
            )
        }) {
            var state = awaitLoaded()
            while (state.charts == null) state = awaitLoaded()
            result = state to state.charts!!
            cancelAndIgnoreRemainingEvents()
        }
        return result!!
    }

    private fun streamsOf(data: String, extra: String = "") {
        streams = """{"uri":"$activityUri","visibility":"public","channels":[],"data":{$data}$extra}"""
    }

    @Test
    fun aDecoDiveChartsItsCeilingSwitchesOxygenAndEachTank() = runTest {
        streamsOf(
            """"time":[0,600,1200,1800],"depth":[0,3000,1200,0],"decoStopDepth":[null,600,300,null],""" +
                """"ppO2":[210,840,1300,500],"cns":[1,5,12,14],"gasSwitches":[{"time":0,"oxygen":21,"helium":0},""" +
                """{"time":1200,"oxygen":50,"helium":0}],"tankPressures":[{"pressure":[200000,150000,120000,100000]},""" +
                """{"pressure":[210000,null,205000,200000]}]"""
        )
        val (state, charts) = loaded()

        assertEquals(listOf(GasSwitchPoint(0, 21, 0), GasSwitchPoint(1200, 50, 0)), charts.gasSwitches)
        assertEquals(listOf(GasKind.Air, GasKind.Nitrox), charts.gasSwitches.map { it.kind })
        assertEquals(listOf(600 to 6.0, 1200 to 3.0), charts.ceiling.map { it.seconds to it.value }, "meters, only while in deco")
        assertEquals(1.3, charts.lines.single { it.kind == StreamKind.PpO2 }.max, "bar")
        assertEquals(14.0, charts.lines.single { it.kind == StreamKind.Cns }.max)
        assertEquals(listOf(200.0, 210.0), charts.tankPressures.map { it.max }, "a line per tank, in bar")
        assertEquals(listOf(DetailSection.Depth, DetailSection.Oxygen, DetailSection.TankPressure), state.sections)
    }

    @Test
    fun onlyWarningsAreMarkedOnTheDepthChart() = runTest {
        streamsOf(
            """"time":[0,1500,2400],"depth":[0,3000,0],"diveAlerts":[{"time":2400,"kind":"ascentCritical"},""" +
                """{"time":1500,"kind":"ndlReached"},{"time":1600,"kind":"safetyStopComplete"},{"time":1700,"kind":"someNewAlert"}]"""
        )
        val (_, charts) = loaded()

        assertEquals(
            listOf(DiveAlertPoint(1500, DiveAlertKind.NDL_REACHED), DiveAlertPoint(2400, DiveAlertKind.ASCENT_CRITICAL)),
            charts.alerts,
            "informational alerts and kinds the app doesn't name aren't marked",
        )
    }

    @Test
    fun aDiveWithDepthAndTemperatureOnlyHasNoneOfIt() = runTest {
        streamsOf(""""time":[0,600,1200],"depth":[0,1800,0],"temperature":[240,220,230]""")
        val (state, charts) = loaded()

        assertTrue(charts.ceiling.isEmpty())
        assertTrue(charts.gasSwitches.isEmpty(), "a dive imported before switches were stored has no markers")
        assertTrue(charts.alerts.isEmpty())
        assertTrue(charts.tankPressures.isEmpty())
        assertEquals(listOf(DetailSection.Temperature, DetailSection.Depth), state.sections)
    }

    @Test
    fun aRunsDynamicsRespirationAndARidesBalanceAreCharted() = runTest {
        streamsOf(
            """"time":[0,10,20],"heartRate":[140,150,160],"power":[200,210,220],"leftRightBalance":[52,51,50],""" +
                """"respirationRate":[305,310,320],"verticalOscillation":[85,86,84],"stanceTime":[245,246,244],""" +
                """"stepLength":[1250,1260,1240],"verticalRatio":[683,690,670],"stanceTimeBalance":[4975,5010,4990]"""
        )
        val (state, charts) = loaded()

        assertEquals(32.0, charts.lines.single { it.kind == StreamKind.RespirationRate }.max, "breaths a minute")
        assertEquals(1.26, charts.lines.single { it.kind == StreamKind.StepLength }.max, "meters")
        assertEquals(6.9, charts.lines.single { it.kind == StreamKind.VerticalRatio }.max)
        assertEquals(50.1, charts.lines.single { it.kind == StreamKind.StanceTimeBalance }.max)
        assertEquals(52.0, charts.lines.single { it.kind == StreamKind.LeftRightBalance }.max)
        assertEquals(
            listOf(DetailSection.HeartRate, DetailSection.Power, DetailSection.Respiration, DetailSection.RunningDynamics),
            state.sections,
        )
    }

    @Test
    fun aRunRecordedInTheAppHasNoLapsOrDynamics() = runTest {
        streamsOf(""""time":[0,10,20],"distance":[0,3000,6000],"speed":[3000,3000,3000]""")
        val (state, charts) = loaded()

        assertTrue(charts.laps.isEmpty())
        assertTrue(DetailSection.Laps !in state.sections && DetailSection.RunningDynamics !in state.sections, "${state.sections}")
        assertNull(charts.trainingLoad)
    }

    @Test
    fun lapRowsAreInTheViewersUnits() = runTest {
        streamsOf(
            """"time":[0,300,600],"heartRate":[140,150,160],"laps":[""" +
                """{"start":0,"elapsed":300,"moving":295,"distance":1609,"avgSpeed":5364,"avgHeartRate":150,"ascent":30},""" +
                """{"start":300,"elapsed":300,"distance":3218,"avgPower":210,"avgCadence":88}]"""
        )
        val (state, metric) = loaded()
        assertEquals(DetailSection.Laps, state.sections.last())
        assertEquals(listOf(1, 2), metric.laps.map { it.number })
        assertEquals(1.609, metric.laps.first().distance!!, 1e-9, "kilometers")
        assertEquals(19.31, metric.laps.first().speed!!, 0.01, "km/h")
        assertEquals(30.0, metric.laps.first().ascent!!, 1e-9)

        val imperial = loaded(UnitSystem.Imperial).second.laps
        assertEquals(1.0, imperial.first().distance!!, 0.001, "miles")
        assertEquals(12.0, imperial.first().speed!!, 0.01, "mph")
        assertEquals(98.4, imperial.first().ascent!!, 0.1, "feet")
        assertEquals(210 to 88, imperial.last().avgPower to imperial.last().avgCadence)
    }

    @Test
    fun aLapWithoutSpeedShowsItsDistanceOverTime() = runTest {
        // TCX laps without the LX AvgSpeed extension: moving time when the lap has it, else elapsed.
        streamsOf(
            """"time":[0,300,600],"laps":[{"start":0,"elapsed":300,"distance":1000},""" +
                """{"start":300,"elapsed":320,"moving":300,"distance":1000},{"start":620,"elapsed":60}]"""
        )
        val laps = loaded().second.laps
        assertEquals(12.0, laps[0].speed!!, 0.01, "km/h")
        assertEquals(12.0, laps[1].speed!!, 0.01, "km/h")
        assertNull(laps[2].speed, "no distance")

        assertEquals(7.46, loaded(UnitSystem.Imperial).second.laps.first().speed!!, 0.01, "mph")
    }

    @Test
    fun trainingLoadShowsOnlyWhenTheAppViewServesIt() = runTest {
        // The AppView leaves it out for a viewer its zones record doesn't admit, like the zones themselves.
        streamsOf(""""time":[0,10,20],"power":[200,210,220]""")
        assertNull(loaded().second.trainingLoad)

        streamsOf(
            """"time":[0,10,20],"power":[200,210,220]""",
            ""","trainingLoad":{"ftp":250,"intensityFactor":84,"trainingStressScore":1124,"aerobicTrainingEffect":34}""",
        )
        assertEquals(TrainingLoadStats(250, 0.84, 112.4, 3.4, null), loaded().second.trainingLoad)
    }

    @Test
    fun aRidesCyclingDynamicsAreChartedLeftAndRight() = runTest {
        streamsOf(
            """"time":[0,10,20],"power":[200,210,220],"torqueEffectivenessLeft":[8000,8000,8000],""" +
                """"torqueEffectivenessRight":[7650,7650,7650],"pedalSmoothnessLeft":[2200,2200,2200],""" +
                """"pedalSmoothnessRight":[2050,2050,2050],"pcoLeft":[-2,-2,-2],"pcoRight":[3,3,3]"""
        )
        val (state, charts) = loaded()

        val torque = charts.lines.single { it.kind == StreamKind.TorqueEffectiveness }
        assertEquals(80.0, torque.average, "percent")
        assertEquals(76.5, torque.right?.average, "the right leg on the same chart")
        val smoothness = charts.lines.single { it.kind == StreamKind.PedalSmoothness }
        assertEquals(22.0 to 20.5, smoothness.average to smoothness.right?.average)
        val pco = charts.lines.single { it.kind == StreamKind.PlatformCentreOffset }
        assertEquals(-2.0 to 3.0, pco.average to pco.right?.average, "millimeters")
        assertEquals(listOf(DetailSection.Power, DetailSection.CyclingDynamics), state.sections)
    }

    @Test
    fun physiologyEBikeAndStrydChannelsAreCharted() = runTest {
        streamsOf(
            """"time":[0,10,20],"coreTemperature":[3825,3830,3835],"smO2":[655,650,645],"totalHemoglobin":[1234,1234,1234],""" +
                """"ebikeBattery":[90,89,88],"ebikeAssistMode":[2,2,3],"ebikeAssistLevel":[50,50,50],""" +
                """"formPower":[70,70,70],"legSpringStiffness":[950,950,950]"""
        )
        val (state, charts) = loaded()
        fun line(kind: StreamKind) = charts.lines.single { it.kind == kind }

        assertEquals(383.0, line(StreamKind.CoreTemperature).average, "tenths of a degree, like temperature")
        assertEquals(65.0, line(StreamKind.SmO2).average!!, 1e-9)
        assertEquals(12.34, line(StreamKind.TotalHemoglobin).average!!, 1e-9)
        assertEquals(90.0, line(StreamKind.EbikeBattery).max)
        assertEquals(3.0, line(StreamKind.EbikeAssistMode).max)
        assertEquals(70.0, line(StreamKind.FormPower).average)
        assertEquals(9.5, line(StreamKind.LegSpringStiffness).average)
        assertEquals(listOf(DetailSection.Physiology, DetailSection.RunningDynamics, DetailSection.EBike), state.sections)
    }

    @Test
    fun anAppRecordingHasNoneOfTheSensorCharts() = runTest {
        streamsOf(""""time":[0,10,20],"distance":[0,3000,6000],"speed":[3000,3000,3000]""")
        val (state, charts) = loaded()

        assertTrue(
            charts.lines.none { it.kind in setOf(StreamKind.TorqueEffectiveness, StreamKind.CoreTemperature, StreamKind.EbikeBattery) }
        )
        assertTrue(state.sections.none { it in setOf(DetailSection.CyclingDynamics, DetailSection.Physiology, DetailSection.EBike) })
    }

    @Test
    fun theTrainingLoadCarriesItsHrv() = runTest {
        streamsOf(""""time":[0,10,20],"heartRate":[150,151,152]""", ""","trainingLoad":{"hrvRmssd":42}""")
        assertEquals(42, loaded().second.trainingLoad?.hrvRmssd)
    }

    @Test
    fun gasKindsNameEachMix() {
        assertEquals(GasKind.Air, GasKind.of(21, 0))
        assertEquals(GasKind.Nitrox, GasKind.of(50, 0))
        assertEquals(GasKind.Nitrox, GasKind.of(32, 0))
        assertEquals(GasKind.Oxygen, GasKind.of(100, 0))
        assertEquals(GasKind.Oxygen, GasKind.of(99, 0), "Garmin logs pure oxygen as 99%")
        assertEquals(GasKind.Trimix, GasKind.of(18, 45))
        assertEquals(GasKind.Trimix, GasKind.of(21, 35), "helium makes it trimix, even at 21% oxygen")
    }
}
