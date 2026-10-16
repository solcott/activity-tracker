package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.settings.SettingsEvent
import io.github.solcott.activitytracker.app.settings.SettingsPresenter
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.app.settings.TrainingZonesScreen
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SettingsPresenterTest {
    private val fixture = SessionTestFixture()

    @Test
    fun choosingUnitsSavesThemToTheProfile() = runTest {
        val repos = fixture.repos(defaultUnits = UnitSystem.Metric)
        presenterTestOf({ SettingsPresenter(FakeNavigator(SettingsScreen), repos.units, repos.auth, repos.defaults, repos.privacy) }) {
            val initial = awaitItem()
            assertEquals(UnitSystem.Metric, initial.units)
            assertNull(initial.error)

            initial.eventSink(SettingsEvent.UnitsChanged(UnitSystem.Imperial))
            val saved = awaitUntil { it.units == UnitSystem.Imperial && !it.saving }
            assertNull(saved.error)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("imperial", fixture.putRecords.single()["units"]?.jsonPrimitive?.content)
    }

    @Test
    fun failedSaveRevertsAndShowsError() = runTest {
        fixture.failWrites = true
        val repos = fixture.repos(defaultUnits = UnitSystem.Metric)
        presenterTestOf({ SettingsPresenter(FakeNavigator(SettingsScreen), repos.units, repos.auth, repos.defaults, repos.privacy) }) {
            awaitItem().eventSink(SettingsEvent.UnitsChanged(UnitSystem.Imperial))
            val failed = awaitUntil { it.error != null && !it.saving }
            assertEquals(UiError.Kind.SaveSettings, failed.error?.kind)
            assertEquals(UnitSystem.Metric, failed.units)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun backPops() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(SettingsScreen)
        presenterTestOf({ SettingsPresenter(navigator, repos.units, repos.auth, repos.defaults, repos.privacy) }) {
            awaitItem().eventSink(SettingsEvent.Back)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun zonesOpenTheZonesScreen() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(SettingsScreen)
        presenterTestOf({ SettingsPresenter(navigator, repos.units, repos.auth, repos.defaults, repos.privacy) }) {
            awaitItem().eventSink(SettingsEvent.OpenZones)
            assertEquals(TrainingZonesScreen, navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun defaultVisibilitiesSaveCappedAndReload() = runTest {
        val repos = fixture.repos()
        presenterTestOf({ SettingsPresenter(FakeNavigator(SettingsScreen), repos.units, repos.auth, repos.defaults, repos.privacy) }) {
            val loaded = awaitUntil { it.defaults != null }
            assertEquals(VisibilityFields(), loaded.defaults, "never set: Public")
            loaded.eventSink(SettingsEvent.DefaultChartsVisibilityChanged(Visibility.SELF))
            awaitUntil { it.savingDefaults }
            awaitUntil { !it.savingDefaults }.eventSink(SettingsEvent.DefaultVisibilityChanged(Visibility.MUTUALS))
            awaitUntil { it.savingDefaults }
            val saved = awaitUntil { !it.savingDefaults }
            assertEquals(Visibility.MUTUALS, saved.defaults?.activity)
            assertEquals(Visibility.SELF, saved.defaults?.charts, "narrower charts stay")
            assertEquals(persistentListOf(Visibility.MUTUALS, Visibility.SELF), saved.defaults?.chartsOptions)
            cancelAndIgnoreRemainingEvents()
        }
        val record = AtJson.parseToJsonElement(fixture.activityDefaults!!).jsonObject
        assertEquals(Visibility.MUTUALS, record.getValue("visibility").jsonPrimitive.content)
        assertEquals(Visibility.SELF, record.getValue("chartsVisibility").jsonPrimitive.content)

        // Opening Settings again, in a new session, reads them back.
        val again = fixture.repos()
        presenterTestOf({ SettingsPresenter(FakeNavigator(SettingsScreen), again.units, again.auth, again.defaults, again.privacy) }) {
            assertEquals(VisibilityFields(Visibility.MUTUALS, Visibility.SELF), awaitUntil { it.defaults != null }.defaults)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun wideningTheActivityDefaultKeepsTheChartsAndNarrowingCapsThem() = runTest {
        fixture.activityDefaults = """{"visibility":"followers","chartsVisibility":"followers"}"""
        val repos = fixture.repos()
        presenterTestOf({ SettingsPresenter(FakeNavigator(SettingsScreen), repos.units, repos.auth, repos.defaults, repos.privacy) }) {
            awaitUntil { it.defaults != null }.eventSink(SettingsEvent.DefaultVisibilityChanged(Visibility.SELF))
            awaitUntil { it.savingDefaults }
            val narrowed = awaitUntil { !it.savingDefaults }
            assertEquals(Visibility.SELF, narrowed.defaults?.activity)
            assertEquals(Visibility.SELF, narrowed.defaults?.charts, "charts default capped")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
