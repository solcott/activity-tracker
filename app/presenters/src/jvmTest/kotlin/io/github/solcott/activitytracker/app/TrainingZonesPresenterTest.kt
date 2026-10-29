package io.github.solcott.activitytracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withCompositionLocals
import com.slack.circuit.retained.LocalRetainedStateRegistry
import com.slack.circuit.retained.RetainedStateRegistry
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.settings.SportZonesScreen
import io.github.solcott.activitytracker.app.settings.TrainingZonesEvent
import io.github.solcott.activitytracker.app.settings.TrainingZonesPresenter
import io.github.solcott.activitytracker.app.settings.TrainingZonesScreen
import io.github.solcott.activitytracker.app.settings.TrainingZonesState
import io.github.solcott.activitytracker.app.settings.ZonesScreen
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.data.training.ZoneKind
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.hasLoaded
import io.github.solcott.uistate.isLoading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class TrainingZonesPresenterTest {
    private val fixture = SessionTestFixture()

    @Test
    fun showsTheDefaultsAndEachSportAsTheyApply() = runTest {
        fixture.privateDetails =
            """{"details":{"training":{"thresholdHeartRate":165,"functionalThresholdPower":231,""" +
                """"sports":[{"sport":"run","thresholdSpeed":3271,"thresholdHeartRate":172}]}},"visibility":{"training":"mutuals"}}"""
        val repos = fixture.repos(defaultUnits = UnitSystem.Imperial)
        presenterTestOf({ TrainingZonesPresenter(FakeNavigator(TrainingZonesScreen), repos.profiles, repos.units) }) {
            val state = awaitUntil { it.zones != null }
            assertEquals(Visibility.MUTUALS, state.visibility)
            val loaded = state.zones!!
            val defaults = loaded.defaults
            assertNull(defaults.sport)
            assertEquals(setOf(ZoneKind.HeartRate, ZoneKind.Power), defaults.kinds)
            assertEquals(165, defaults.training.thresholdHeartRate)
            assertEquals(5, defaults.heartRateZones?.size)

            val run = loaded.sports.single()
            assertEquals(Sport.RUN, run.sport)
            assertEquals(PaceUnit.PerMile, run.paceUnit)
            assertEquals(172, run.training.thresholdHeartRate)
            assertEquals(231, run.training.functionalThresholdPower, "the run inherits the default FTP")
            assertNull(run.override?.functionalThresholdPower)
            assertEquals(7, run.speedZones?.size)

            assertFalse(Sport.RUN in loaded.addableSports)
            assertEquals(Sport.RIDE, loaded.addableSports.first())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun editOpensTheDefaultsOrASportsEditor() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(TrainingZonesScreen)
        presenterTestOf({ TrainingZonesPresenter(navigator, repos.profiles, repos.units) }) {
            val loaded = awaitUntil { it.zones != null }
            assertEquals(emptyList(), loaded.zones?.sports.orEmpty())
            loaded.eventSink(TrainingZonesEvent.EditDefaults)
            assertEquals(ZonesScreen, navigator.awaitNextScreen())
            loaded.eventSink(TrainingZonesEvent.EditSport(Sport.SWIM))
            assertEquals(SportZonesScreen(Sport.SWIM), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun pickingAVisibilitySavesItAndKeepsTheZones() = runTest {
        fixture.privateDetails =
            """{"details":{"weight":61500,"training":{"functionalThresholdPower":231,""" +
                """"sports":[{"sport":"swim","thresholdSpeed":1115}]}},""" +
                """"visibility":{"weight":"mutuals","training":"self"}}"""
        val repos = fixture.repos()
        presenterTestOf({ TrainingZonesPresenter(FakeNavigator(TrainingZonesScreen), repos.profiles, repos.units) }) {
            val loaded = awaitUntil { it.zones != null }
            assertTrue(loaded.zones?.hasZones == true)
            val save = CompletableDeferred<Unit>().also { fixture.gate = it }
            loaded.eventSink(TrainingZonesEvent.VisibilityChanged(Visibility.MUTUALS))
            assertEquals(Visibility.MUTUALS, awaitUntil { it.savingVisibility }.visibility, "the pick shows while it saves")
            save.complete(Unit)
            assertEquals(Visibility.MUTUALS, awaitUntil { !it.savingVisibility }.visibility)
            cancelAndIgnoreRemainingEvents()
        }
        val sent = fixture.privateDetailsPuts.single()
        val details = sent.getValue("details").jsonObject
        assertEquals(61_500L, details["weight"]?.jsonPrimitive?.long)
        val training = details.getValue("training").jsonObject
        assertEquals(231L, training["functionalThresholdPower"]?.jsonPrimitive?.long)
        assertEquals("swim", training.getValue("sports").jsonArray.single().jsonObject["sport"]?.jsonPrimitive?.content)
        assertEquals(Visibility.MUTUALS, sent.getValue("visibility").jsonPrimitive.content)
    }

    @Test
    fun noVisibilityToSetUntilAZoneIsSaved() = runTest {
        val repos = fixture.repos()
        presenterTestOf({ TrainingZonesPresenter(FakeNavigator(TrainingZonesScreen), repos.profiles, repos.units) }) {
            assertFalse(awaitUntil { it.zones != null }.zones?.hasZones == true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedLoadSaysWhyAndRetryLoadsTheZones() = runTest {
        fixture.offline = true
        val repos = fixture.repos()
        presenterTestOf({ TrainingZonesPresenter(FakeNavigator(TrainingZonesScreen), repos.profiles, repos.units) }) {
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertEquals(DataError.Network, failed.content.errorOrNull)
            assertNull(failed.error, "a load failure isn't the save's")

            fixture.offline = false
            failed.eventSink(TrainingZonesEvent.Retry)
            assertTrue(awaitUntil { it.zones != null && it.content.errorOrNull == null }.content.hasLoaded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSavedVisibilityShowsAtOnceAndSurvivesAReload() = runTest {
        fixture.privateDetails = """{"details":{"training":{"functionalThresholdPower":231}},"visibility":{"training":"self"}}"""
        val repos = fixture.repos()
        presenterTestOf({ TrainingZonesPresenter(FakeNavigator(TrainingZonesScreen), repos.profiles, repos.units) }) {
            awaitUntil { it.zones != null && !it.content.isLoading }.eventSink(TrainingZonesEvent.VisibilityChanged(Visibility.FOLLOWERS))
            val saved = awaitUntil { !it.savingVisibility && it.visibility == Visibility.FOLLOWERS }
            val reload = CompletableDeferred<Unit>().also { fixture.gate = it }
            saved.eventSink(TrainingZonesEvent.Retry)
            awaitUntil { it.content.isLoading }
            reload.complete(Unit)
            val reloaded = awaitUntil { !it.content.isLoading }
            assertEquals(Visibility.FOLLOWERS, reloaded.visibility, "read back from the space it was saved to")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedSaveIsTheActionsErrorAndKeepsTheZones() = runTest {
        fixture.privateDetails = """{"details":{"training":{"functionalThresholdPower":231}},"visibility":{"training":"self"}}"""
        val repos = fixture.repos()
        presenterTestOf({ TrainingZonesPresenter(FakeNavigator(TrainingZonesScreen), repos.profiles, repos.units) }) {
            val loaded = awaitUntil { it.zones != null && !it.content.isLoading }
            fixture.offline = true
            loaded.eventSink(TrainingZonesEvent.VisibilityChanged(Visibility.FOLLOWERS))
            val failed = awaitUntil { it.error != null && !it.savingVisibility }
            assertEquals(UiError.Kind.SaveZones, failed.error?.kind)
            assertEquals(DataError.Network, failed.error?.reason)
            assertNull(failed.content.errorOrNull)
            assertEquals(Visibility.SELF, failed.visibility)
            assertEquals(231, failed.zones?.defaults?.training?.functionalThresholdPower)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun comingBackShowsTheZonesAtOnce() = runTest {
        val repos = fixture.repos()
        val registry = RetainedStateRegistry()
        var shown by mutableStateOf(true)
        presenterTestOf({
            withCompositionLocals(LocalRetainedStateRegistry provides registry) {
                // A stand-in for the editor it opened while it's on the back stack.
                if (shown) TrainingZonesPresenter(FakeNavigator(TrainingZonesScreen), repos.profiles, repos.units) else TrainingZonesState()
            }
        }) {
            awaitUntil { it.zones != null && !it.content.isLoading }
            registry.saveAll()
            shown = false
            awaitUntil { it.zones == null }
            fixture.offline = true
            shown = true
            assertNotNull(awaitItem().zones, "no loading state in between")
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertNotNull(failed.zones)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private val TrainingZonesState.zones
    get() = content.data
