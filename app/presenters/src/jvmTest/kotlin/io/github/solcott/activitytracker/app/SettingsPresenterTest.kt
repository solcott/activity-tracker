package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.settings.SettingsEvent
import io.github.solcott.activitytracker.app.settings.SettingsPresenter
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.app.settings.SettingsState
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive

class SettingsPresenterTest {
    private val fixture = SessionTestFixture()

    @Test
    fun choosingUnitsSavesThemToTheProfile() = runTest {
        val repos = fixture.repos(defaultUnits = UnitSystem.Metric)
        presenterTestOf({ SettingsPresenter(FakeNavigator(SettingsScreen), repos.units, repos.auth) }) {
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
        presenterTestOf({ SettingsPresenter(FakeNavigator(SettingsScreen), repos.units, repos.auth) }) {
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
        presenterTestOf({ SettingsPresenter(navigator, repos.units, repos.auth) }) {
            awaitItem().eventSink(SettingsEvent.Back)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<SettingsState>.awaitUntil(predicate: (SettingsState) -> Boolean): SettingsState {
        var state = awaitItem()
        while (!predicate(state)) state = awaitItem()
        return state
    }
}
