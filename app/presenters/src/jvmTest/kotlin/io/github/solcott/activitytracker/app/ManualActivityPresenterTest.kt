package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.record.ManualActivityEvent
import io.github.solcott.activitytracker.app.record.ManualActivityPresenter
import io.github.solcott.activitytracker.app.record.ManualActivityScreen
import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.Visibility
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
        presenterTestOf({
            ManualActivityPresenter(navigator, repos.activities, repos.units, repos.recentTypes, repos.defaults, Clock.System)
        }) {
            val initial = awaitItem()
            assertEquals(UnitSystem.Imperial, initial.units)

            initial.distance.setTextAndPlaceCursorAtEnd("3.1")
            initial.durationMinutes.setTextAndPlaceCursorAtEnd("30")
            initial.temperature.setTextAndPlaceCursorAtEnd("50")
            initial.eventSink(ManualActivityEvent.Save)
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
        presenterTestOf({
            ManualActivityPresenter(navigator, repos.activities, repos.units, repos.recentTypes, repos.defaults, Clock.System)
        }) {
            awaitItem().eventSink(ManualActivityEvent.UnitsChanged(UnitSystem.Metric))
            val metric = awaitItem()
            assertEquals(UnitSystem.Metric, metric.units)
            metric.distance.setTextAndPlaceCursorAtEnd("5")
            metric.durationMinutes.setTextAndPlaceCursorAtEnd("25")
            metric.temperature.setTextAndPlaceCursorAtEnd("-5.5")
            metric.eventSink(ManualActivityEvent.Save)
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
        presenterTestOf({
            ManualActivityPresenter(navigator, repos.activities, repos.units, repos.recentTypes, repos.defaults, Clock.System)
        }) {
            val form = awaitItem()
            form.distance.setTextAndPlaceCursorAtEnd("5,2")
            form.durationMinutes.setTextAndPlaceCursorAtEnd("30,5")
            assertTrue(form.valid)
            form.temperature.setTextAndPlaceCursorAtEnd("-2,5")
            form.eventSink(ManualActivityEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }

        val record = fixture.putRecords.single()
        assertEquals(5200L, record.getValue("distance").jsonPrimitive.long)
        assertEquals(1830L, record.getValue("movingTime").jsonPrimitive.long) // 30.5 min
        assertEquals(-25L, record.getValue("avgTemperature").jsonPrimitive.long)
    }

    @Test
    fun savesTheChosenTypeAndOffersItFirstNextTime() = runTest {
        val repos = fixture.repos(defaultUnits = UnitSystem.Metric)
        val navigator = FakeNavigator(ManualActivityScreen)
        val gravel = ActivityType(Sport.RIDE, SubSport.GRAVEL)
        presenterTestOf({
            ManualActivityPresenter(navigator, repos.activities, repos.units, repos.recentTypes, repos.defaults, Clock.System)
        }) {
            val initial = awaitUntil { it.quickTypes.isNotEmpty() }
            assertEquals(SportCatalog.common, initial.quickTypes, "nothing tracked yet")
            assertEquals(ActivityType(Sport.RUN), initial.type)

            initial.eventSink(ManualActivityEvent.TypeChanged(gravel))
            val chosen = awaitUntil { it.type == gravel }
            chosen.durationMinutes.setTextAndPlaceCursorAtEnd("90")
            chosen.eventSink(ManualActivityEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }

        val record = fixture.putRecords.single()
        assertEquals(Sport.RIDE, record.getValue("sport").jsonPrimitive.content)
        assertEquals(SubSport.GRAVEL, record.getValue("subSport").jsonPrimitive.content)

        presenterTestOf({
            ManualActivityPresenter(
                FakeNavigator(ManualActivityScreen),
                repos.activities,
                repos.units,
                repos.recentTypes,
                repos.defaults,
                Clock.System,
            )
        }) {
            val next = awaitUntil { it.quickTypes.firstOrNull() == gravel }
            assertEquals(listOf(gravel) + SportCatalog.common, next.quickTypes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theDefaultVisibilityCanBeOverriddenForOneActivity() = runTest {
        fixture.activityDefaults = """{"visibility":"followers","chartsVisibility":"followers"}"""
        val repos = fixture.repos()
        val navigator = FakeNavigator(ManualActivityScreen)
        presenterTestOf({
            ManualActivityPresenter(navigator, repos.activities, repos.units, repos.recentTypes, repos.defaults, Clock.System)
        }) {
            val defaulted = awaitUntil { it.visibility.activity == Visibility.FOLLOWERS }
            defaulted.eventSink(ManualActivityEvent.VisibilityChanged(Visibility.PUBLIC))
            val public = awaitUntil { it.visibility.activity == Visibility.PUBLIC }
            public.durationMinutes.setTextAndPlaceCursorAtEnd("30")
            public.eventSink(ManualActivityEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, fixture.putRecords.size, "published to the repo")
        // Widening the activity leaves its categories at the Followers default: its exact start goes to the followers space.
        val (tier, details) = fixture.spacePuts.single()
        assertEquals(Visibility.FOLLOWERS, tier)
        assertEquals(Nsid.FEED_ACTIVITY_DETAILS, details.getValue("\$type").jsonPrimitive.content)
        assertTrue("startedAt" in details)
        assertEquals("""{"visibility":"followers","chartsVisibility":"followers"}""", fixture.activityDefaults, "the default stays")
    }

    @Test
    fun everyCategoryCanBeMadePublicWithTheActivity() = runTest {
        fixture.activityDefaults = """{"visibility":"followers"}"""
        val repos = fixture.repos()
        val navigator = FakeNavigator(ManualActivityScreen)
        presenterTestOf({
            ManualActivityPresenter(navigator, repos.activities, repos.units, repos.recentTypes, repos.defaults, Clock.System)
        }) {
            awaitUntil { it.visibility.activity == Visibility.FOLLOWERS }
                .eventSink(ManualActivityEvent.VisibilityChanged(Visibility.PUBLIC))
            var state = awaitUntil { it.visibility.activity == Visibility.PUBLIC }
            assertEquals(Visibility.FOLLOWERS, state.visibility.tierOf(DetailCategory.START_TIME), "widening leaves categories")
            state.eventSink(ManualActivityEvent.CategoryVisibilityChanged(DetailCategory.START_TIME, Visibility.PUBLIC))
            state = awaitUntil { it.visibility.tierOf(DetailCategory.START_TIME) == Visibility.PUBLIC }
            state.durationMinutes.setTextAndPlaceCursorAtEnd("30")
            state.eventSink(ManualActivityEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, fixture.putRecords.size)
        assertTrue(fixture.spacePuts.isEmpty(), "nothing narrower than the activity has data")
    }

    @Test
    fun aFollowersDefaultPublishesToTheFollowersSpace() = runTest {
        fixture.activityDefaults = """{"visibility":"followers"}"""
        val repos = fixture.repos()
        val navigator = FakeNavigator(ManualActivityScreen)
        presenterTestOf({
            ManualActivityPresenter(navigator, repos.activities, repos.units, repos.recentTypes, repos.defaults, Clock.System)
        }) {
            val defaulted = awaitUntil { it.visibility.activity == Visibility.FOLLOWERS }
            defaulted.durationMinutes.setTextAndPlaceCursorAtEnd("30")
            defaulted.eventSink(ManualActivityEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(fixture.putRecords.isEmpty(), "never in the repo")
        assertEquals(listOf(Visibility.FOLLOWERS), fixture.spacePuts.map { it.first })
    }
}
