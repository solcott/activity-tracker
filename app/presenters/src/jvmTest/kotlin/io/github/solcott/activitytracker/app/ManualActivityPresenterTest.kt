package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.record.ManualActivityEvent
import io.github.solcott.activitytracker.app.record.ManualActivityPresenter
import io.github.solcott.activitytracker.app.record.ManualActivityScreen
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class ManualActivityPresenterTest {
    private val fixture = SessionTestFixture()

    @Test
    fun imperialEntryIsStoredInSiUnits() = runTest {
        val repos = fixture.repos(defaultUnits = UnitSystem.Imperial)
        val navigator = FakeNavigator(ManualActivityScreen)
        presenterTestOf({ ManualActivityPresenter(navigator, repos.activities, repos.units, Clock.System) }) {
            val initial = awaitItem()
            assertEquals(UnitSystem.Imperial, initial.units)

            initial.eventSink(ManualActivityEvent.DistanceChanged("3.1"))
            awaitItem().eventSink(ManualActivityEvent.DurationChanged("30"))
            awaitItem().eventSink(ManualActivityEvent.TemperatureChanged("50"))
            awaitItem().eventSink(ManualActivityEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }

        val record = fixture.putRecords.single()
        assertEquals(4989L, record.getValue("distance").jsonPrimitive.long) // 3.1 mi
        assertEquals(100L, record.getValue("avgTemperature").jsonPrimitive.long) // 50 °F = 10.0 °C
    }

    @Test
    fun switchingUnitsChangesHowEntriesAreRead() = runTest {
        val repos = fixture.repos(defaultUnits = UnitSystem.Imperial)
        val navigator = FakeNavigator(ManualActivityScreen)
        presenterTestOf({ ManualActivityPresenter(navigator, repos.activities, repos.units, Clock.System) }) {
            awaitItem().eventSink(ManualActivityEvent.UnitsChanged(UnitSystem.Metric))
            val metric = awaitItem()
            assertEquals(UnitSystem.Metric, metric.units)
            metric.eventSink(ManualActivityEvent.DistanceChanged("5"))
            awaitItem().eventSink(ManualActivityEvent.DurationChanged("25"))
            awaitItem().eventSink(ManualActivityEvent.TemperatureChanged("-5.5"))
            awaitItem().eventSink(ManualActivityEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }

        val record = fixture.putRecords.single()
        assertEquals(5000L, record.getValue("distance").jsonPrimitive.long)
        assertEquals(-55L, record.getValue("avgTemperature").jsonPrimitive.long)
    }

    @Test
    fun acceptsACommaAsTheDecimalSeparator() = runTest {
        val repos = fixture.repos(defaultUnits = UnitSystem.Metric)
        val navigator = FakeNavigator(ManualActivityScreen)
        presenterTestOf({ ManualActivityPresenter(navigator, repos.activities, repos.units, Clock.System) }) {
            awaitItem().eventSink(ManualActivityEvent.DistanceChanged("5,2"))
            awaitItem().eventSink(ManualActivityEvent.DurationChanged("30,5"))
            val filled = awaitItem()
            assertTrue(filled.valid)
            filled.eventSink(ManualActivityEvent.TemperatureChanged("-2,5"))
            awaitItem().eventSink(ManualActivityEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }

        val record = fixture.putRecords.single()
        assertEquals(5200L, record.getValue("distance").jsonPrimitive.long)
        assertEquals(1830L, record.getValue("movingTime").jsonPrimitive.long) // 30.5 min
        assertEquals(-25L, record.getValue("avgTemperature").jsonPrimitive.long)
    }
}
