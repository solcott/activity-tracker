package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.settings.SportZonesEvent
import io.github.solcott.activitytracker.app.settings.SportZonesPresenter
import io.github.solcott.activitytracker.app.settings.SportZonesScreen
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.data.training.ZoneKind
import io.github.solcott.activitytracker.data.training.ZoneModels
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.HeartRateZoneModel
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.isLoading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class SportZonesPresenterTest {
    private val fixture = SessionTestFixture()
    private val now = Instant.parse("2026-10-06T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = this@SportZonesPresenterTest.now
        }
    private val setOn = "2026-09-01T00:00:00Z"
    private val saved =
        """{"details":{"weight":61500,"training":{"thresholdHeartRate":165,"maxHeartRate":190,"functionalThresholdPower":231,""" +
            """"sports":[{"sport":"ride","functionalThresholdPower":250},""" +
            """{"sport":"run","thresholdHeartRate":172,"thresholdSpeed":3271,"setAt":{"thresholdSpeed":"$setOn"}}]}},""" +
            """"visibility":{"weight":"mutuals","training":"mutuals"}}"""

    @Test
    fun aFailedLoadSaysWhyAndRetryFillsTheForm() = runTest {
        fixture.privateDetails = saved
        fixture.offline = true
        val repos = fixture.repos()
        presenterTestOf({
            SportZonesPresenter(
                SportZonesScreen(Sport.RIDE),
                FakeNavigator(SportZonesScreen(Sport.RIDE)),
                repos.profiles,
                repos.units,
                clock,
            )
        }) {
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals(DataError.Network, failed.content.errorOrNull)
            assertFalse(failed.loaded)
            assertNull(failed.error, "a load failure isn't the save's")

            fixture.offline = false
            failed.eventSink(SportZonesEvent.Retry)
            awaitUntil { it.content.isLoading }
            assertEquals("250", awaitUntil { it.loaded }.functionalThresholdPower.text.toString())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedSaveKeepsWhatWasTypedAndShowsTheSavesError() = runTest {
        fixture.privateDetails = saved
        fixture.failWrites = true
        val repos = fixture.repos()
        val navigator = FakeNavigator(SportZonesScreen(Sport.RIDE))
        presenterTestOf({ SportZonesPresenter(SportZonesScreen(Sport.RIDE), navigator, repos.profiles, repos.units, clock) }) {
            val loaded = awaitUntil { it.loaded }
            loaded.functionalThresholdPower.setTextAndPlaceCursorAtEnd("260")
            loaded.eventSink(SportZonesEvent.Save)
            val failed = awaitUntil { it.error != null && !it.saving }
            assertEquals(UiError.Kind.SaveZones, failed.error?.kind)
            assertTrue(failed.loaded, "a failed save isn't a failed load")
            assertEquals("260", failed.functionalThresholdPower.text.toString())
            cancelAndIgnoreRemainingEvents()
        }
        navigator.assertPopIsEmpty()
    }

    private fun sportsOf(training: JsonObject) = training["sports"]?.jsonArray.orEmpty().map { it.jsonObject }

    @Test
    fun loadsTheOverrideAndInheritsTheRest() = runTest {
        fixture.privateDetails = saved
        val repos = fixture.repos(defaultUnits = UnitSystem.Imperial)
        presenterTestOf({
            SportZonesPresenter(SportZonesScreen(Sport.RUN), FakeNavigator(SportZonesScreen(Sport.RUN)), repos.profiles, repos.units, clock)
        }) {
            val loaded = awaitUntil { it.loaded }
            assertEquals(setOf(ZoneKind.HeartRate, ZoneKind.Power, ZoneKind.Speed), loaded.kinds)
            assertEquals(PaceUnit.PerMile, loaded.paceUnit)
            assertEquals("8:12", loaded.thresholdPace.text.toString())
            assertEquals("172", loaded.thresholdHeartRate.text.toString())
            assertEquals("", loaded.maxHeartRate.text.toString(), "a value the sport doesn't set stays blank")
            assertEquals(190, loaded.defaults.maxHeartRate)
            assertEquals(ZoneModels.frielRunningPace, loaded.speedModel)
            assertEquals(3634, loaded.speedZones?.last()?.low)
            // Zones use the run's LTHR, and the default FTP since the run sets none.
            assertEquals(182, loaded.heartRateZones?.last()?.low)
            assertEquals(280, loaded.powerZones?.last()?.low)
            assertTrue(loaded.saved)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aNewSportStartsEmptyAndOffersOnlyItsZones() = runTest {
        fixture.privateDetails = saved
        val repos = fixture.repos(defaultUnits = UnitSystem.Metric)
        presenterTestOf({
            SportZonesPresenter(
                SportZonesScreen(Sport.SWIM),
                FakeNavigator(SportZonesScreen(Sport.SWIM)),
                repos.profiles,
                repos.units,
                clock,
            )
        }) {
            val loaded = awaitUntil { it.loaded }
            assertEquals(setOf(ZoneKind.HeartRate, ZoneKind.Speed), loaded.kinds)
            assertEquals(PaceUnit.Per100Meters, loaded.paceUnit)
            assertFalse(loaded.saved)
            assertNull(loaded.speedZones)
            assertEquals(ZoneModels.frielSwimmingPace, loaded.speedModel)
            loaded.thresholdPace.setTextAndPlaceCursorAtEnd("1:30")
            assertEquals(7, awaitUntil { it.speedZones != null }.speedZones?.size)
            loaded.thresholdPace.setTextAndPlaceCursorAtEnd("1:3")
            assertFalse(awaitUntil { it.speedZones == null }.valid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun savingReplacesOnlyThisSportAndMarksEditedValues() = runTest {
        fixture.privateDetails = saved
        val repos = fixture.repos(defaultUnits = UnitSystem.Imperial)
        val navigator = FakeNavigator(SportZonesScreen(Sport.RUN))
        presenterTestOf({ SportZonesPresenter(SportZonesScreen(Sport.RUN), navigator, repos.profiles, repos.units, clock) }) {
            val loaded = awaitUntil { it.loaded }
            loaded.thresholdHeartRate.setTextAndPlaceCursorAtEnd("175")
            loaded.eventSink(SportZonesEvent.HeartRateModelChanged(HeartRateZoneModel.FRIEL_RUNNING))
            awaitUntil { it.heartRateModel == ZoneModels.frielRunning && it.thresholdHeartRate.text.toString() == "175" }
                .eventSink(SportZonesEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val sent = fixture.privateDetailsPuts.single()
        assertEquals(Visibility.MUTUALS, sent.getValue("visibility").jsonPrimitive.content)
        val details = sent.getValue("details").jsonObject
        assertEquals(61_500L, details["weight"]?.jsonPrimitive?.long)
        val training = details.getValue("training").jsonObject
        assertEquals(165L, training["thresholdHeartRate"]?.jsonPrimitive?.long, "the defaults are untouched")
        val (ride, run) = sportsOf(training)
        assertEquals(250L, ride["functionalThresholdPower"]?.jsonPrimitive?.long)
        assertEquals(175L, run["thresholdHeartRate"]?.jsonPrimitive?.long)
        assertEquals(3271L, run["thresholdSpeed"]?.jsonPrimitive?.long)
        assertEquals(HeartRateZoneModel.FRIEL_RUNNING, run["heartRateZoneModel"]?.jsonPrimitive?.content)
        val setAt = run.getValue("setAt").jsonObject
        assertEquals(now.toString(), setAt["thresholdHeartRate"]?.jsonPrimitive?.content)
        assertEquals(setOn, setAt["thresholdSpeed"]?.jsonPrimitive?.content, "an untouched value keeps its time")
    }

    @Test
    fun removingDropsOnlyThisSport() = runTest {
        fixture.privateDetails = saved
        val repos = fixture.repos()
        val navigator = FakeNavigator(SportZonesScreen(Sport.RUN))
        presenterTestOf({ SportZonesPresenter(SportZonesScreen(Sport.RUN), navigator, repos.profiles, repos.units, clock) }) {
            awaitUntil { it.loaded }.eventSink(SportZonesEvent.Remove)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val training = fixture.privateDetailsPuts.single().getValue("details").jsonObject.getValue("training").jsonObject
        assertEquals(listOf(Sport.RIDE), sportsOf(training).map { it.getValue("sport").jsonPrimitive.content })
    }

    @Test
    fun savingTwiceWritesOnce() = runTest {
        fixture.privateDetails = saved
        val repos = fixture.repos()
        val navigator = FakeNavigator(SportZonesScreen(Sport.RUN))
        presenterTestOf({ SportZonesPresenter(SportZonesScreen(Sport.RUN), navigator, repos.profiles, repos.units, clock) }) {
            val loaded = awaitUntil { it.loaded }
            loaded.eventSink(SportZonesEvent.Save)
            loaded.eventSink(SportZonesEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, fixture.privateDetailsPuts.size)
    }
}
