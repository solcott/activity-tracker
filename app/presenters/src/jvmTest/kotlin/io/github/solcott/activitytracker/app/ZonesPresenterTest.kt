package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.settings.ZonesEvent
import io.github.solcott.activitytracker.app.settings.ZonesPresenter
import io.github.solcott.activitytracker.app.settings.ZonesScreen
import io.github.solcott.activitytracker.data.training.ZoneModels
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.HeartRateZoneModel
import io.github.solcott.activitytracker.lexicon.app.PowerZoneModel
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Threshold
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class ZonesPresenterTest {
    private val fixture = SessionTestFixture()
    private val now = Instant.parse("2026-10-06T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = this@ZonesPresenterTest.now
        }

    @Test
    fun loadsSavedThresholdsAndShowsTheirZones() = runTest {
        fixture.privateDetails =
            """{"details":{"weight":61500,"training":{"thresholdHeartRate":168,"functionalThresholdPower":231,""" +
                """"heartRateZoneModel":"frielRunning"}},"visibility":{"weight":"mutuals","training":"mutuals"}}"""
        val repos = fixture.repos()
        presenterTestOf({ ZonesPresenter(FakeNavigator(ZonesScreen), repos.profiles, clock) }) {
            val loaded = awaitUntil { it.loaded }
            assertEquals("168", loaded.thresholdHeartRate.text.toString())
            assertEquals("", loaded.maxHeartRate.text.toString())
            assertEquals(ZoneModels.frielRunning, loaded.heartRateModel)
            assertEquals(7, loaded.heartRateZones?.size)
            assertEquals(ZoneModels.defaultPower, loaded.powerModel, "an unpicked model shows the default")
            assertEquals(280, loaded.powerZones?.last()?.low)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedLoadSaysWhyAndRetryFillsTheForm() = runTest {
        fixture.privateDetails = """{"details":{"training":{"thresholdHeartRate":168}},"visibility":{"training":"self"}}"""
        fixture.offline = true
        val repos = fixture.repos()
        presenterTestOf({ ZonesPresenter(FakeNavigator(ZonesScreen), repos.profiles, clock) }) {
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals(DataError.Network, failed.content.errorOrNull)
            assertFalse(failed.loaded)
            assertNull(failed.error, "a load failure isn't the save's")

            fixture.offline = false
            failed.eventSink(ZonesEvent.Retry)
            awaitUntil { it.content.isLoading }
            assertEquals("168", awaitUntil { it.loaded }.thresholdHeartRate.text.toString())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aLaterLoadKeepsWhatWasTyped() = runTest {
        fixture.privateDetails = """{"details":{"training":{"thresholdHeartRate":168}},"visibility":{"training":"self"}}"""
        val repos = fixture.repos()
        presenterTestOf({ ZonesPresenter(FakeNavigator(ZonesScreen), repos.profiles, clock) }) {
            val loaded = awaitUntil { it.loaded }
            val reads = fixture.profileReads
            loaded.thresholdHeartRate.setTextAndPlaceCursorAtEnd("170")
            fixture.privateDetails = """{"details":{"training":{"thresholdHeartRate":150}},"visibility":{"training":"self"}}"""
            loaded.eventSink(ZonesEvent.Retry)
            awaitSettled { fixture.profileReads > reads }

            val after = expectMostRecentItem()
            assertTrue(after.loaded)
            assertEquals("170", after.thresholdHeartRate.text.toString())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun zonesFollowTypingAndTheModel() = runTest {
        val repos = fixture.repos()
        presenterTestOf({ ZonesPresenter(FakeNavigator(ZonesScreen), repos.profiles, clock) }) {
            val loaded = awaitUntil { it.loaded }
            assertNull(loaded.heartRateZones)
            assertNull(loaded.powerZones)

            loaded.maxHeartRate.setTextAndPlaceCursorAtEnd("190")
            loaded.eventSink(ZonesEvent.HeartRateModelChanged(HeartRateZoneModel.PERCENT_MAX))
            val byMax = awaitUntil { it.heartRateZones != null }
            assertEquals(171, byMax.heartRateZones?.last()?.low)

            byMax.eventSink(ZonesEvent.HeartRateModelChanged(HeartRateZoneModel.RESERVE))
            awaitUntil { it.heartRateModel == ZoneModels.heartRateReserve && it.heartRateZones == null }
            byMax.restingHeartRate.setTextAndPlaceCursorAtEnd("55")
            assertEquals(177, awaitUntil { it.heartRateZones != null }.heartRateZones?.last()?.low)

            byMax.maxHeartRate.setTextAndPlaceCursorAtEnd("999")
            assertFalse(awaitUntil { it.heartRateZones == null }.valid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun savingSendsThresholdsAndKeepsOtherDetails() = runTest {
        fixture.privateDetails = """{"details":{"weight":61500},"visibility":{"weight":"mutuals"}}"""
        val repos = fixture.repos()
        val navigator = FakeNavigator(ZonesScreen)
        presenterTestOf({ ZonesPresenter(navigator, repos.profiles, clock) }) {
            val loaded = awaitUntil { it.loaded }
            loaded.functionalThresholdPower.setTextAndPlaceCursorAtEnd("231")
            loaded.thresholdHeartRate.setTextAndPlaceCursorAtEnd("168")
            loaded.thresholdHeartRate.clearText()
            loaded.eventSink(ZonesEvent.PowerModelChanged(PowerZoneModel.COGGAN_7))
            awaitUntil { it.powerModel.token == PowerZoneModel.COGGAN_7 }.eventSink(ZonesEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val sent = fixture.privateDetailsPuts.single()
        val details = sent.getValue("details").jsonObject
        assertNull(details["weight"], "the Only me tier gets only the training")
        assertEquals(
            61_500L,
            fixture.tierDetails[AthleteSpace.MUTUALS]?.get("weight")?.jsonPrimitive?.long,
            "the Mutuals weight is untouched",
        )
        val training = details.getValue("training").jsonObject
        assertEquals(231L, training["functionalThresholdPower"]?.jsonPrimitive?.long)
        assertEquals(PowerZoneModel.COGGAN_7, training["powerZoneModel"]?.jsonPrimitive?.content)
        assertNull(training["thresholdHeartRate"], "a cleared threshold is left out")
        assertEquals(
            Visibility.SELF,
            sent.getValue("visibility").jsonPrimitive.content,
            "new zones start as Only me",
        )
    }

    @Test
    fun failedSaveStaysAndShowsError() = runTest {
        fixture.failWrites = true
        val repos = fixture.repos()
        val navigator = FakeNavigator(ZonesScreen)
        presenterTestOf({ ZonesPresenter(navigator, repos.profiles, clock) }) {
            val loaded = awaitUntil { it.loaded }
            loaded.functionalThresholdPower.setTextAndPlaceCursorAtEnd("231")
            loaded.eventSink(ZonesEvent.Save)
            val failed = awaitUntil { it.error != null && !it.saving }
            assertEquals(UiError.Kind.SaveZones, failed.error?.kind)
            assertEquals("231", failed.functionalThresholdPower.text.toString())
            cancelAndIgnoreRemainingEvents()
        }
        navigator.assertPopIsEmpty()
    }

    @Test
    fun autoUpdateChoicesLoadAndSaveAndOnlyEditedValuesGetANewTime() = runTest {
        val setOn = "2026-09-01T00:00:00Z"
        fixture.privateDetails =
            """{"details":{"training":{"maxHeartRate":190,"functionalThresholdPower":231,"autoUpdate":["maxHeartRate"],""" +
                """"setAt":{"maxHeartRate":"$setOn","functionalThresholdPower":"$setOn"}}},"visibility":{"training":"self"}}"""
        val repos = fixture.repos()
        val navigator = FakeNavigator(ZonesScreen)
        presenterTestOf({ ZonesPresenter(navigator, repos.profiles, clock) }) {
            val loaded = awaitUntil { it.loaded }
            assertEquals(setOf(Threshold.MAX_HEART_RATE), loaded.autoUpdate)
            loaded.eventSink(ZonesEvent.AutoUpdateChanged(Threshold.FUNCTIONAL_THRESHOLD_POWER, true))
            loaded.eventSink(ZonesEvent.AutoUpdateChanged(Threshold.MAX_HEART_RATE, false))
            loaded.functionalThresholdPower.setTextAndPlaceCursorAtEnd("240")
            awaitUntil { it.autoUpdate == setOf(Threshold.FUNCTIONAL_THRESHOLD_POWER) }.eventSink(ZonesEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val training = fixture.privateDetailsPuts.single().getValue("details").jsonObject.getValue("training").jsonObject
        assertEquals(
            listOf(Threshold.FUNCTIONAL_THRESHOLD_POWER),
            training.getValue("autoUpdate").jsonArray.map { it.jsonPrimitive.content },
        )
        val setAt = training.getValue("setAt").jsonObject
        assertEquals(now.toString(), setAt["functionalThresholdPower"]?.jsonPrimitive?.content, "an edited value is set now")
        assertEquals(setOn, setAt["maxHeartRate"]?.jsonPrimitive?.content, "an untouched value keeps its time")
    }

    @Test
    fun savingKeepsSportsSavedElsewhere() = runTest {
        fixture.privateDetails =
            """{"details":{"training":{"thresholdHeartRate":165,"sports":[{"sport":"run","thresholdSpeed":3271}]}},""" +
                """"visibility":{"training":"self"}}"""
        val repos = fixture.repos()
        val navigator = FakeNavigator(ZonesScreen)
        presenterTestOf({ ZonesPresenter(navigator, repos.profiles, clock) }) {
            val loaded = awaitUntil { it.loaded }
            // Swim gets its own thresholds on its screen while this one is open.
            fixture.privateDetails =
                """{"details":{"training":{"thresholdHeartRate":165,"sports":[{"sport":"run","thresholdSpeed":3271},""" +
                    """{"sport":"swim","thresholdSpeed":1115}]}},"visibility":{"training":"self"}}"""
            loaded.thresholdHeartRate.setTextAndPlaceCursorAtEnd("170")
            awaitUntil { it.heartRateZones?.size == 5 && it.thresholdHeartRate.text.toString() == "170" }.eventSink(ZonesEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val training = fixture.privateDetailsPuts.single().getValue("details").jsonObject.getValue("training").jsonObject
        assertEquals(170L, training["thresholdHeartRate"]?.jsonPrimitive?.long)
        assertEquals(
            listOf(Sport.RUN, Sport.SWIM),
            training.getValue("sports").jsonArray.map { it.jsonObject.getValue("sport").jsonPrimitive.content },
        )
    }
}
