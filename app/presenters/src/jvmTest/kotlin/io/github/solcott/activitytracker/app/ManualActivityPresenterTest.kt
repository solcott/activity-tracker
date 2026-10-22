package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.record.ActivityTypeEvent
import io.github.solcott.activitytracker.app.record.ManualActivityEvent
import io.github.solcott.activitytracker.app.record.ManualActivityPresenter
import io.github.solcott.activitytracker.app.record.ManualActivityScreen
import io.github.solcott.activitytracker.app.visibility.VisibilityEvent
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
            ManualActivityPresenter(navigator, repos.activities, repos.units, repos.typeChoices, repos.defaults, Clock.System)
        }) {
            val initial = awaitItem()
            assertEquals(UnitSystem.Imperial, initial.units)

            initial.distance.setTextAndPlaceCursorAtEnd("3.1")
            initial.durationMinutes.setTextAndPlaceCursorAtEnd("30")
            initial.temperature.setTextAndPlaceCursorAtEnd("50")
            initial.eventSink(ManualActivityEvent.Save)
            navigator.awaitPop()
            val opened = navigator.awaitNextScreen() as ActivityDetailScreen
            assertEquals(fixture.putRkeys.single(), opened.uri.substringAfterLast('/'), "the saved activity opens")
            assertEquals(true, opened.suggestOnOpen, "asking whether to combine it")
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
            ManualActivityPresenter(navigator, repos.activities, repos.units, repos.typeChoices, repos.defaults, Clock.System)
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
            ManualActivityPresenter(navigator, repos.activities, repos.units, repos.typeChoices, repos.defaults, Clock.System)
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
            ManualActivityPresenter(navigator, repos.activities, repos.units, repos.typeChoices, repos.defaults, Clock.System)
        }) {
            val initial = awaitUntil { it.type.quickTypes.isNotEmpty() }
            assertEquals(SportCatalog.common, initial.type.quickTypes, "nothing tracked yet")
            assertEquals(ActivityType(Sport.RUN), initial.type.selected)

            initial.type.eventSink(ActivityTypeEvent.Select(gravel))
            val chosen = awaitUntil { it.type.selected == gravel }
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
                repos.typeChoices,
                repos.defaults,
                Clock.System,
            )
        }) {
            val next = awaitUntil { it.type.quickTypes.firstOrNull() == gravel }
            assertEquals(listOf(gravel) + SportCatalog.common, next.type.quickTypes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theDefaultVisibilityCanBeOverriddenForOneActivity() = runTest {
        fixture.activityDefaults = """{"visibility":"followers","chartsVisibility":"followers"}"""
        val repos = fixture.repos()
        val navigator = FakeNavigator(ManualActivityScreen)
        presenterTestOf({
            ManualActivityPresenter(navigator, repos.activities, repos.units, repos.typeChoices, repos.defaults, Clock.System)
        }) {
            val defaulted = awaitUntil { it.visibility.activity == Visibility.FOLLOWERS }
            defaulted.visibility.eventSink(VisibilityEvent.ActivityChanged(Visibility.PUBLIC))
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
            ManualActivityPresenter(navigator, repos.activities, repos.units, repos.typeChoices, repos.defaults, Clock.System)
        }) {
            awaitUntil { it.visibility.activity == Visibility.FOLLOWERS }
                .visibility
                .eventSink(VisibilityEvent.ActivityChanged(Visibility.PUBLIC))
            var state = awaitUntil { it.visibility.activity == Visibility.PUBLIC }
            assertEquals(Visibility.FOLLOWERS, state.visibility.tierOf(DetailCategory.START_TIME), "widening leaves categories")
            state.visibility.eventSink(VisibilityEvent.CategoryChanged(DetailCategory.START_TIME, Visibility.PUBLIC))
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
            ManualActivityPresenter(navigator, repos.activities, repos.units, repos.typeChoices, repos.defaults, Clock.System)
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

    @Test
    fun offersStartTimeAndPaceOnlyOnceADistanceIsEntered() = runTest {
        fixture.activityDefaults = """{"visibility":"public","details":{"dive":"self"}}"""
        val repos = fixture.repos()
        val navigator = FakeNavigator(ManualActivityScreen)
        presenterTestOf({
            ManualActivityPresenter(navigator, repos.activities, repos.units, repos.typeChoices, repos.defaults, Clock.System)
        }) {
            var state = awaitUntil { it.visibility.tierOf(DetailCategory.START_TIME) != null }
            assertEquals(listOf(DetailCategory.START_TIME), state.visibility.categories.map { it.category }, "no distance, no pace")
            // A pace needs a time as well as a distance.
            state.durationMinutes.setTextAndPlaceCursorAtEnd("30")

            state.distance.setTextAndPlaceCursorAtEnd("5")
            state = awaitUntil { it.visibility.tierOf(DetailCategory.SPEED) != null }
            assertEquals(listOf(DetailCategory.START_TIME, DetailCategory.SPEED), state.visibility.categories.map { it.category })
            state.visibility.eventSink(VisibilityEvent.CategoryChanged(DetailCategory.SPEED, Visibility.SELF))
            state = awaitUntil { it.visibility.tierOf(DetailCategory.SPEED) == Visibility.SELF }

            state.distance.clearText()
            state = awaitUntil { it.visibility.tierOf(DetailCategory.SPEED) == null }
            state.visibility.eventSink(VisibilityEvent.CategoryChanged(DetailCategory.SPEED, Visibility.PUBLIC))
            state.distance.setTextAndPlaceCursorAtEnd("5")
            state = awaitUntil { it.visibility.tierOf(DetailCategory.SPEED) != null }
            assertEquals(Visibility.SELF, state.visibility.tierOf(DetailCategory.SPEED), "kept while hidden; changes to it ignored")

            state.distance.clearText()
            state = awaitUntil { it.visibility.tierOf(DetailCategory.SPEED) == null }
            state.eventSink(ManualActivityEvent.Save)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, fixture.putRecords.size)
        assertTrue(fixture.spacePuts.isEmpty(), "an Only-me dive default with no dive data publishes nothing there")
    }
}
