package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.settings.SportZonesScreen
import io.github.solcott.activitytracker.app.settings.TrainingZonesEvent
import io.github.solcott.activitytracker.app.settings.TrainingZonesPresenter
import io.github.solcott.activitytracker.app.settings.TrainingZonesScreen
import io.github.solcott.activitytracker.app.settings.ZonesScreen
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.data.training.ZoneKind
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
            val loaded = awaitUntil { it.defaults != null }
            assertEquals(Visibility.MUTUALS, loaded.visibility)
            val defaults = loaded.defaults!!
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
            val loaded = awaitUntil { it.defaults != null }
            assertEquals(emptyList(), loaded.sports)
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
            val loaded = awaitUntil { it.defaults != null }
            assertTrue(loaded.hasZones)
            loaded.eventSink(TrainingZonesEvent.VisibilityChanged(Visibility.MUTUALS))
            assertEquals(Visibility.MUTUALS, awaitUntil { it.savingVisibility }.visibility, "the pick shows while it saves")
            assertEquals(Visibility.MUTUALS, awaitUntil { !it.savingVisibility }.visibility)
            cancelAndIgnoreRemainingEvents()
        }
        val sent = fixture.privateDetailsPuts.single()
        val details = sent.getValue("details").jsonObject
        assertEquals(61_500L, details["weight"]?.jsonPrimitive?.long)
        val training = details.getValue("training").jsonObject
        assertEquals(231L, training["functionalThresholdPower"]?.jsonPrimitive?.long)
        assertEquals("swim", training.getValue("sports").jsonArray.single().jsonObject["sport"]?.jsonPrimitive?.content)
        assertEquals(Visibility.MUTUALS, sent.getValue("visibility").jsonObject["training"]?.jsonPrimitive?.content)
    }

    @Test
    fun noVisibilityToSetUntilAZoneIsSaved() = runTest {
        val repos = fixture.repos()
        presenterTestOf({ TrainingZonesPresenter(FakeNavigator(TrainingZonesScreen), repos.profiles, repos.units) }) {
            assertFalse(awaitUntil { it.defaults != null }.hasZones)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
