package io.github.solcott.activitytracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withCompositionLocals
import com.slack.circuit.retained.LocalRetainedStateRegistry
import com.slack.circuit.retained.RetainedStateRegistry
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.settings.ThresholdHistoryEvent
import io.github.solcott.activitytracker.app.settings.ThresholdHistoryPresenter
import io.github.solcott.activitytracker.app.settings.ThresholdHistoryScreen
import io.github.solcott.activitytracker.app.settings.ThresholdHistoryState
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.ThresholdSource
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.isLoading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class ThresholdHistoryPresenterTest {
    private val fixture = SessionTestFixture()
    private val clock =
        object : Clock {
            override fun now() = Instant.parse("2026-10-07T12:00:00Z")
        }

    private val training =
        """{"functionalThresholdPower":260,"maxHeartRate":190,""" +
            """"setAt":{"functionalThresholdPower":"2026-09-15T07:00:00Z","maxHeartRate":"2026-08-01T07:00:00Z"},""" +
            """"sports":[{"sport":"run","thresholdSpeed":3271}],""" +
            """"history":[""" +
            """{"threshold":"functionalThresholdPower","value":250,"setAt":"2026-09-01T07:00:00Z","source":"manual"},""" +
            """{"threshold":"functionalThresholdPower","value":260,"setAt":"2026-09-15T07:00:00Z",""" +
            """"source":"device","device":"Edge 840"},""" +
            """{"threshold":"thresholdSpeed","sport":"run","value":3200,"setAt":"2026-09-02T07:00:00Z","source":"trainerRoad"}]}"""

    private val ftp = Threshold.FUNCTIONAL_THRESHOLD_POWER

    private fun ThresholdHistoryState.section(sport: String?, threshold: String) =
        groups!!.single { it.sport == sport }.thresholds.single { it.threshold == threshold }

    @Test
    fun groupsTheDefaultsThenEachSportWithCharts() = runTest {
        fixture.privateDetails = """{"details":{"training":$training},"visibility":{"training":"self"}}"""
        val repos = fixture.repos()
        presenterTestOf({ ThresholdHistoryPresenter(FakeNavigator(ThresholdHistoryScreen), repos.profiles, repos.units, clock) }) {
            val loaded = awaitUntil { it.groups != null }
            val groups = loaded.groups!!
            assertEquals(listOf(null, Sport.RUN), groups.map { it.sport })
            assertEquals(listOf(true, false), groups.map { it.expanded }, "the defaults start expanded")
            // Max heart rate had no history: it's added as of when it was set.
            assertEquals(listOf(Threshold.MAX_HEART_RATE, ftp), groups[0].thresholds.map { it.threshold })

            val power = loaded.section(null, ftp)
            assertEquals(listOf(260L, 250L), power.rows.map { it.entry.value })
            assertEquals(listOf(true, false), power.rows.map { it.current })
            assertEquals("Edge 840", power.newest.entry.device)
            val chart = assertNotNull(power.chart)
            assertEquals(listOf(250L, 260L), chart.points.map { it.value })
            assertEquals(listOf(0f, 1f), chart.points.map { it.y })
            assertEquals(0f, chart.points.first().x)
            assertTrue(chart.points.last().x < 1f, "the line runs on to today")
            assertEquals(listOf(false, true), chart.points.map { it.current })
            assertEquals(250L, power.change?.from)
            assertEquals(9, power.change?.since?.month)

            val maxHeartRate = loaded.section(null, Threshold.MAX_HEART_RATE)
            assertNull(maxHeartRate.chart, "one value has nothing to chart")
            assertNull(maxHeartRate.change)
            assertNull(maxHeartRate.newest.entry.source)

            // The run's pace is now 3271, which no entry has.
            val pace = loaded.section(Sport.RUN, Threshold.THRESHOLD_SPEED)
            assertEquals(listOf(false), pace.rows.map { it.current })
            assertEquals(ThresholdSource.TRAINER_ROAD, pace.newest.entry.source)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun groupsAndLongHistoriesExpandAndCollapse() = runTest {
        val ftps = (1..5).joinToString(",") { """{"threshold":"$ftp","value":${200 + it},"setAt":"2026-0$it-01T07:00:00Z"}""" }
        fixture.privateDetails =
            """{"details":{"training":{"functionalThresholdPower":205,"sports":[{"sport":"ride","maxHeartRate":190}],""" +
                """"history":[$ftps]}},"visibility":{"training":"self"}}"""
        val repos = fixture.repos()
        presenterTestOf({ ThresholdHistoryPresenter(FakeNavigator(ThresholdHistoryScreen), repos.profiles, repos.units, clock) }) {
            val loaded = awaitUntil { it.groups != null }
            val power = loaded.section(null, ftp)
            assertEquals(3, power.shownRows.size)
            assertTrue(power.canShowAll)
            loaded.eventSink(ThresholdHistoryEvent.ToggleShowAll(ftp, null))
            assertEquals(5, awaitUntil { it.section(null, ftp).showingAll }.section(null, ftp).shownRows.size)

            loaded.eventSink(ThresholdHistoryEvent.ToggleGroup(Sport.RIDE))
            assertTrue(awaitUntil { s -> s.groups!!.all { it.expanded } }.anyExpanded)
            loaded.eventSink(ThresholdHistoryEvent.ToggleAll)
            assertFalse(awaitUntil { s -> s.groups!!.none { it.expanded } }.anyExpanded)
            loaded.eventSink(ThresholdHistoryEvent.ToggleAll)
            awaitUntil { s -> s.groups!!.all { it.expanded } }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun removingTheCurrentValuePutsBackThePreviousOne() = runTest {
        fixture.privateDetails = """{"details":{"training":$training},"visibility":{"training":"self"}}"""
        val repos = fixture.repos()
        presenterTestOf({ ThresholdHistoryPresenter(FakeNavigator(ThresholdHistoryScreen), repos.profiles, repos.units, clock) }) {
            val loaded = awaitUntil { it.groups != null }
            val current = loaded.section(null, ftp).newest.entry
            loaded.eventSink(ThresholdHistoryEvent.Remove(current))
            loaded.eventSink(ThresholdHistoryEvent.Remove(current))
            val after = awaitUntil { it.removing == null && it.section(null, ftp).rows.size == 1 }
            assertEquals(listOf(true), after.section(null, ftp).rows.map { it.current })
            cancelAndIgnoreRemainingEvents()
        }
        val sent = fixture.privateDetailsPuts.single().getValue("details").jsonObject.getValue("training").jsonObject
        assertEquals(250L, sent["functionalThresholdPower"]?.jsonPrimitive?.long)
        assertEquals("2026-09-01T07:00:00Z", sent.getValue("setAt").jsonObject["functionalThresholdPower"]?.jsonPrimitive?.content)
        assertEquals(3, sent.getValue("history").jsonArray.size, "two FTPs less one, the pace, and max heart rate added")
    }

    @Test
    fun aFailedLoadSaysWhyAndRetryLoadsTheHistory() = runTest {
        fixture.privateDetails = """{"details":{"training":$training},"visibility":{"training":"self"}}"""
        fixture.offline = true
        val repos = fixture.repos()
        presenterTestOf({ ThresholdHistoryPresenter(FakeNavigator(ThresholdHistoryScreen), repos.profiles, repos.units, clock) }) {
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals(DataError.Network, failed.content.errorOrNull)
            assertNull(failed.groups)
            assertNull(failed.error, "a load failure isn't a removal's")

            fixture.offline = false
            failed.eventSink(ThresholdHistoryEvent.Retry)
            assertEquals(listOf(null, Sport.RUN), awaitUntil { it.groups != null }.groups?.map { it.sport })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedRemovalIsTheActionsErrorAndKeepsTheGroups() = runTest {
        fixture.privateDetails = """{"details":{"training":$training},"visibility":{"training":"self"}}"""
        val repos = fixture.repos()
        presenterTestOf({ ThresholdHistoryPresenter(FakeNavigator(ThresholdHistoryScreen), repos.profiles, repos.units, clock) }) {
            val loaded = awaitUntil { it.groups != null && !it.content.isLoading }
            fixture.offline = true
            loaded.eventSink(ThresholdHistoryEvent.Remove(loaded.section(null, ftp).newest.entry))
            val failed = awaitUntil { it.error != null && it.removing == null }
            assertEquals(UiError.Kind.SaveZones, failed.error?.kind)
            assertNull(failed.content.errorOrNull)
            assertEquals(2, failed.section(null, ftp).rows.size, "nothing removed")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun comingBackShowsTheHistoryAtOnce() = runTest {
        fixture.privateDetails = """{"details":{"training":$training},"visibility":{"training":"self"}}"""
        val repos = fixture.repos()
        val registry = RetainedStateRegistry()
        var shown by mutableStateOf(true)
        presenterTestOf({
            withCompositionLocals(LocalRetainedStateRegistry provides registry) {
                if (shown) ThresholdHistoryPresenter(FakeNavigator(ThresholdHistoryScreen), repos.profiles, repos.units, clock)
                else ThresholdHistoryState()
            }
        }) {
            awaitUntil { it.groups != null && !it.content.isLoading }
            registry.saveAll()
            shown = false
            awaitUntil { it.groups == null }
            shown = true
            assertNotNull(awaitItem().groups, "no loading state in between")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
