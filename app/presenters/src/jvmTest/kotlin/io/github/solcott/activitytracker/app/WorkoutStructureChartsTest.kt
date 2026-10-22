package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailPage
import io.github.solcott.activitytracker.app.detail.ActivityDetailPresenter
import io.github.solcott.activitytracker.app.detail.DetailSection
import io.github.solcott.activitytracker.app.detail.LegRow
import io.github.solcott.activitytracker.app.detail.LengthRow
import io.github.solcott.activitytracker.app.detail.PoolLength
import io.github.solcott.activitytracker.app.detail.SetRow
import io.github.solcott.activitytracker.app.detail.poolLength
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** What the detail page shows of a workout's structure: a swim's lengths and pool, a workout's sets, a race's legs, ski runs. */
class WorkoutStructureChartsTest : ActivityDetailTestBase() {
    /** The page once loaded, waiting for its charts when [withCharts]. */
    private suspend fun loaded(withCharts: Boolean = true): ActivityDetailPage {
        val r = repos()
        var result: ActivityDetailPage? = null
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
                r.suggestions,
                r.blocks,
                r.reports,
                r.segments,
            )
        }) {
            var state = awaitLoaded()
            if (withCharts) while (state.charts == null) state = awaitLoaded()
            result = state
            cancelAndIgnoreRemainingEvents()
        }
        return result!!
    }

    private fun streamsOf(data: String) {
        streams = """{"uri":"$activityUri","visibility":"public","channels":[],"data":{"time":[0,30,60],$data}}"""
    }

    @Test
    fun aViewerWhoMaySeeTheStreamsSeesTheLengthsAndPool() = runTest {
        sport = "swim"
        recordExtra = """"poolLength":2500,"""
        streamsOf(
            """"lengths":[{"start":0,"elapsed":30,"stroke":"freestyle","strokes":15},{"start":30,"elapsed":20,"rest":true},""" +
                """{"start":50,"elapsed":35,"stroke":"breaststroke","strokes":12}]"""
        )
        val state = loaded()
        assertEquals(PoolLength(25, yards = false), state.poolLength)
        val lengths = state.charts!!.lengths
        assertEquals(
            listOf(
                LengthRow(1, "freestyle", 30, 15, false),
                LengthRow(null, null, 20, null, true),
                LengthRow(2, "breaststroke", 35, 12, false),
            ),
            lengths,
        )
        assertEquals(listOf(45, null, 47), lengths.map { it.swolf })
        assertTrue(DetailSection.Lengths in state.sections)
    }

    @Test
    fun aViewerWhoMayNotSeeTheStreamsSeesThePoolOnly() = runTest {
        sport = "swim"
        recordExtra = """"poolLength":2286,"""
        // The AppView serves no streams to a viewer their place doesn't admit.
        streams = null
        val state = loaded(withCharts = false)
        assertEquals(PoolLength(25, yards = true), state.poolLength)
        assertNull(state.charts)
        assertTrue(
            DetailSection.Lengths !in state.sections && DetailSection.Sets !in state.sections && DetailSection.Legs !in state.sections
        )
    }

    @Test
    fun aWorkoutsSetsAreNumberedWithTheirRestsBetween() = runTest {
        sport = "strength"
        streamsOf(
            """"sets":[{"start":0,"elapsed":40,"category":"bench_press","exercise":"barbell_bench_press","reps":8,"weight":60000},""" +
                """{"start":40,"elapsed":90,"rest":true},{"start":130,"elapsed":30,"category":"push_up","exercise":"push_up","reps":20}]"""
        )
        val state = loaded()
        assertEquals(
            listOf(
                SetRow(1, false, "bench_press", "barbell_bench_press", 8, 60_000, 40),
                SetRow(null, true, null, null, null, null, 90),
                SetRow(2, false, "push_up", "push_up", 20, null, 30),
            ),
            state.charts!!.sets,
        )
        assertTrue(DetailSection.Sets in state.sections)
        assertNull(state.poolLength)
    }

    @Test
    fun aTriathlonsLegsShowWithTheirTransitions() = runTest {
        sport = "multisport"
        streamsOf(
            """"legs":[{"start":0,"elapsed":1800,"sport":"swim","distance":1500},""" +
                """{"start":1800,"elapsed":120,"sport":"multisport","transition":true},""" +
                """{"start":1920,"elapsed":3600,"moving":3550,"sport":"ride","subSport":"road","distance":40000}]"""
        )
        val state = loaded()
        assertEquals(
            listOf(
                LegRow("swim", null, false, 1800, 1500),
                LegRow("multisport", null, true, 120, null),
                LegRow("ride", "road", false, 3550, 40_000),
            ),
            state.charts!!.legs,
        )
        assertEquals(DetailSection.Legs, state.sections.first { it == DetailSection.Legs })
    }

    @Test
    fun aSkiDayCountsItsRunsAndMarksLifts() = runTest {
        sport = "ski"
        streamsOf(
            """"laps":[{"start":0,"elapsed":300,"ascent":300,"intensity":"rest"},""" +
                """{"start":300,"elapsed":240,"descent":300,"intensity":"active"},""" +
                """{"start":540,"elapsed":300,"ascent":300,"intensity":"rest"},{"start":840,"elapsed":240,"descent":290,"intensity":"active"}]"""
        )
        val charts = loaded().charts!!
        assertEquals(2, charts.skiRuns)
        assertEquals(listOf(true, false, true, false), charts.laps.map { it.isRest })
        assertEquals(300.0, charts.laps[1].descent!!, 1e-9)
    }

    @Test
    fun aRunWithLapsHasNoRunCount() = runTest {
        streamsOf(""""laps":[{"start":0,"elapsed":30},{"start":30,"elapsed":30}]""")
        val charts = loaded().charts!!
        assertNull(charts.skiRuns)
        assertTrue(charts.laps.none { it.intensity != null })
    }

    @Test
    fun anActivityImportedBeforeShowsNoStructure() = runTest {
        sport = "swim"
        streamsOf(""""heartRate":[120,130,140]""")
        val state = loaded()
        val charts = state.charts!!
        assertTrue(charts.lengths.isEmpty() && charts.sets.isEmpty() && charts.legs.isEmpty())
        assertNull(state.poolLength)
        assertTrue(listOf(DetailSection.Lengths, DetailSection.Sets, DetailSection.Legs).none { it in state.sections })
    }

    @Test
    fun poolLengthsReadInTheirPoolsUnit() {
        assertEquals(PoolLength(25, yards = false), poolLength(2500))
        assertEquals(PoolLength(50, yards = false), poolLength(5000))
        assertEquals(PoolLength(25, yards = true), poolLength(2286))
        assertEquals(PoolLength(33, yards = false), poolLength(3333), "33.33 m is neither: the nearest meter")
        assertNull(poolLength(null))
        assertNull(poolLength(0))
    }
}
