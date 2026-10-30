package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.record.GearEvent
import io.github.solcott.activitytracker.app.record.RecordEvent
import io.github.solcott.activitytracker.app.record.RecordPresenter
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.app.record.RecordState
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.ActivityRecorder
import io.github.solcott.activitytracker.location.FakeLocationTracker
import io.github.solcott.activitytracker.location.InMemoryRecordingJournal
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.activitytracker.location.RecordingJournal
import io.github.solcott.activitytracker.location.RecordingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
        val recorder = ActivityRecorder(tracker, Clock.System, dispatcher = StandardTestDispatcher(testScheduler))
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
                repos.profiles,
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
        val recorder = ActivityRecorder(tracker, Clock.System, dispatcher = StandardTestDispatcher(testScheduler))
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
                repos.profiles,
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
        val recorder = ActivityRecorder(tracker, Clock.System, dispatcher = StandardTestDispatcher(testScheduler))
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
                repos.profiles,
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

    private val route =
        (0..30).map {
            LocationSample(0.0, it * 0.0001, null, speed = null, accuracy = 5.0, time = Instant.parse("2026-10-24T07:00:00Z") + it.seconds)
        }

    private fun TestScope.recorder(journal: RecordingJournal, samples: List<LocationSample> = route) =
        ActivityRecorder(FakeLocationTracker(samples), Clock.System, journal, dispatcher = StandardTestDispatcher(testScheduler))

    private fun presenter(
        recorder: ActivityRecorder,
        repos: SessionTestFixture.Repos,
        navigator: FakeNavigator = FakeNavigator(RecordScreen),
    ): @Composable () -> RecordState = {
        RecordPresenter(
            navigator,
            recorder,
            tracker,
            repos.activities,
            repos.typeChoices,
            repos.gearChoices,
            repos.defaults,
            Clock.System,
            repos.profiles,
        )
    }

    @Test
    fun aRefusedFinishKeepsTheRecording() = runTest {
        // A session signed in before the space: scope can't write the recording's track, so publishing is refused up front.
        fixture.scope = "atproto transition:generic"
        val repos = fixture.repos()
        val journal = InMemoryRecordingJournal()
        val recorder = recorder(journal)
        presenterTestOf(presenter(recorder, repos)) {
            awaitItem().eventSink(RecordEvent.Start)
            awaitUntil { it.distanceMeters > 0 }.eventSink(RecordEvent.Finish)

            val refused = awaitUntil { it.error != null && it.status == RecordingState.Status.Paused }
            assertEquals(RecordingState.Status.Paused, refused.status, "still on the recording screen, to try again or discard")
            assertTrue(refused.distanceMeters > 300, "with everything it recorded")
            assertEquals(31, journal.saved.single().samples.size, "and still saved on the device")

            refused.eventSink(RecordEvent.Discard)
            awaitUntil { it.status == RecordingState.Status.Idle }
            assertTrue(journal.saved.isEmpty(), "discarding leaves nothing on the device")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFinishedRecordingLeavesTheDevice() = runTest {
        val repos = fixture.repos()
        val journal = InMemoryRecordingJournal()
        val navigator = FakeNavigator(RecordScreen)
        presenterTestOf(presenter(recorder(journal), repos, navigator)) {
            awaitItem().eventSink(RecordEvent.Start)
            awaitUntil { it.distanceMeters > 0 }.eventSink(RecordEvent.Finish)

            navigator.awaitPop()
            assertTrue(journal.saved.isEmpty(), "queued for publishing, so removed from the journal")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRecoveredRecordingIsOfferedBack() = runTest {
        val repos = fixture.repos()
        val journal = InMemoryRecordingJournal()
        val before = recorder(journal)
        before.start(fixture.did.value)
        testScheduler.advanceUntilIdle()
        // The app is killed: a new recorder over the same journal.
        val recorder = recorder(journal, samples = emptyList())
        recorder.recover(fixture.did.value)
        presenterTestOf(presenter(recorder, repos)) {
            val offered = awaitUntil { it.recovered }
            assertEquals(RecordingState.Status.Paused, offered.status, "to resume, finish or discard")
            assertTrue(offered.distanceMeters > 300)
            offered.eventSink(RecordEvent.Resume)
            assertFalse(awaitUntil { it.status == RecordingState.Status.Recording }.recovered)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
