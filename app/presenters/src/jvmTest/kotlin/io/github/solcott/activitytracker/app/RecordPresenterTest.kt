package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.record.GearEvent
import io.github.solcott.activitytracker.app.record.RecordEvent
import io.github.solcott.activitytracker.app.record.RecordPresenter
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.ActivityRecorder
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.activitytracker.location.RecordingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

class RecordPresenterTest {
    private val fixture = SessionTestFixture()

    private val tracker =
        object : LocationTracker {
            override fun hasPermission() = true

            override fun updates(): Flow<LocationSample> = emptyFlow()
        }

    @Test
    fun finishingOpensTheSavedActivityAskingWhetherToCombineIt() = runTest {
        val repos = fixture.repos()
        val recorder = ActivityRecorder(tracker, Clock.System, StandardTestDispatcher(testScheduler))
        val navigator = FakeNavigator(RecordScreen)
        presenterTestOf({
            RecordPresenter(
                navigator,
                recorder,
                tracker,
                repos.activities,
                repos.typeChoices,
                repos.gearChoices,
                repos.defaults,
                Clock.System,
            )
        }) {
            awaitItem().eventSink(RecordEvent.Start)
            awaitUntil { it.status == RecordingState.Status.Recording }.eventSink(RecordEvent.Finish)

            navigator.awaitPop()
            val opened = navigator.awaitNextScreen() as ActivityDetailScreen
            assertEquals(fixture.putRkeys.first(), opened.uri.substringAfterLast('/'), "the saved activity opens")
            assertEquals(true, opened.suggestOnOpen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun offersWhatARecordingHolds() = runTest {
        val repos = fixture.repos()
        val recorder = ActivityRecorder(tracker, Clock.System, StandardTestDispatcher(testScheduler))
        presenterTestOf({
            RecordPresenter(
                FakeNavigator(RecordScreen),
                recorder,
                tracker,
                repos.activities,
                repos.typeChoices,
                repos.gearChoices,
                repos.defaults,
                Clock.System,
            )
        }) {
            assertEquals(
                listOf(DetailCategory.START_TIME, DetailCategory.SPEED, DetailCategory.MAP),
                awaitItem().visibility.categories.map { it.category },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRecordingOffersGearOncePicked() = runTest {
        fixture.gear.getOrPut("repo") { mutableMapOf() }["3bike"] =
            AtJson.encodeToJsonElement(
                GearItemRecord.serializer(),
                GearItemRecord("Tarmac", GearType.BIKE, Instant.parse("2026-10-20T12:00:00Z")),
            ) as JsonObject
        val repos = fixture.repos()
        val recorder = ActivityRecorder(tracker, Clock.System, StandardTestDispatcher(testScheduler))
        presenterTestOf({
            RecordPresenter(
                FakeNavigator(RecordScreen),
                recorder,
                tracker,
                repos.activities,
                repos.typeChoices,
                repos.gearChoices,
                repos.defaults,
                Clock.System,
            )
        }) {
            val loaded = awaitUntil { it.gear.options.isNotEmpty() }
            assertFalse(DetailCategory.GEAR in loaded.visibility.categories.map { it.category })
            loaded.gear.eventSink(GearEvent.Toggle(loaded.gear.options.single().uri))
            val picked = awaitUntil { it.gear.selected.isNotEmpty() }
            assertEquals(DetailCategory.GEAR, picked.visibility.categories.last().category)
            assertEquals(Visibility.SELF, picked.visibility.categories.last().visibility, "Gear never set starts at Only me")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
