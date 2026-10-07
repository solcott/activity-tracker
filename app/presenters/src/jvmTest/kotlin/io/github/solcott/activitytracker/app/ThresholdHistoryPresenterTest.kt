package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.settings.ThresholdHistoryEvent
import io.github.solcott.activitytracker.app.settings.ThresholdHistoryPresenter
import io.github.solcott.activitytracker.app.settings.ThresholdHistoryScreen
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.ThresholdSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class ThresholdHistoryPresenterTest {
    private val fixture = SessionTestFixture()

    private val training =
        """{"functionalThresholdPower":260,"maxHeartRate":190,""" +
            """"setAt":{"functionalThresholdPower":"2026-09-15T07:00:00Z","maxHeartRate":"2026-08-01T07:00:00Z"},""" +
            """"sports":[{"sport":"run","thresholdSpeed":3271}],""" +
            """"history":[""" +
            """{"threshold":"functionalThresholdPower","value":250,"setAt":"2026-09-01T07:00:00Z","source":"manual"},""" +
            """{"threshold":"functionalThresholdPower","value":260,"setAt":"2026-09-15T07:00:00Z",""" +
            """"source":"device","device":"Edge 840"},""" +
            """{"threshold":"thresholdSpeed","sport":"run","value":3200,"setAt":"2026-09-02T07:00:00Z","source":"trainerRoad"}]}"""

    @Test
    fun showsEachThresholdsHistoryNewestFirst() = runTest {
        fixture.privateDetails = """{"details":{"training":$training},"visibility":{"training":"self"}}"""
        val repos = fixture.repos()
        presenterTestOf({ ThresholdHistoryPresenter(FakeNavigator(ThresholdHistoryScreen), repos.profiles, repos.units) }) {
            val sections = awaitUntil { it.sections != null }.sections!!
            // Max heart rate had no history: it's added as of when it was set. The defaults come before the run's.
            assertEquals(
                listOf(
                    Threshold.MAX_HEART_RATE to null,
                    Threshold.FUNCTIONAL_THRESHOLD_POWER to null,
                    Threshold.THRESHOLD_SPEED to Sport.RUN,
                ),
                sections.map { it.threshold to it.sport },
            )
            val ftp = sections[1].rows
            assertEquals(listOf(260L, 250L), ftp.map { it.entry.value })
            assertEquals(listOf(true, false), ftp.map { it.current })
            assertEquals("Edge 840", ftp.first().entry.device)
            assertNull(sections[0].rows.single().entry.source)
            // The run's pace is now 3271, which no entry has.
            assertEquals(listOf(false), sections[2].rows.map { it.current })
            assertEquals(ThresholdSource.TRAINER_ROAD, sections[2].rows.single().entry.source)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun removingTheCurrentValuePutsBackThePreviousOne() = runTest {
        fixture.privateDetails = """{"details":{"training":$training},"visibility":{"training":"self"}}"""
        val repos = fixture.repos()
        presenterTestOf({ ThresholdHistoryPresenter(FakeNavigator(ThresholdHistoryScreen), repos.profiles, repos.units) }) {
            val loaded = awaitUntil { it.sections != null }
            val current = loaded.sections!!.first { it.threshold == Threshold.FUNCTIONAL_THRESHOLD_POWER }.rows.first().entry
            loaded.eventSink(ThresholdHistoryEvent.Remove(current))
            loaded.eventSink(ThresholdHistoryEvent.Remove(current))
            val after = awaitUntil {
                it.removing == null && it.sections!!.first { s -> s.threshold == Threshold.FUNCTIONAL_THRESHOLD_POWER }.rows.size == 1
            }
            assertEquals(
                listOf(true),
                after.sections!!.first { it.threshold == Threshold.FUNCTIONAL_THRESHOLD_POWER }.rows.map { it.current },
            )
            cancelAndIgnoreRemainingEvents()
        }
        val sent = fixture.privateDetailsPuts.single().getValue("details").jsonObject.getValue("training").jsonObject
        assertEquals(250L, sent["functionalThresholdPower"]?.jsonPrimitive?.long)
        assertEquals(
            "2026-09-01T07:00:00Z",
            sent.getValue("setAt").jsonObject["functionalThresholdPower"]?.jsonPrimitive?.content,
        )
        assertEquals(3, sent.getValue("history").jsonArray.size, "two FTPs less one, the pace, and max heart rate added")
    }
}
