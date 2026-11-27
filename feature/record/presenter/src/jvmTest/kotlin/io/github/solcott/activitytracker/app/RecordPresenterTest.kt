package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.record.GearEvent
import io.github.solcott.activitytracker.app.record.RecordAction
import io.github.solcott.activitytracker.app.record.RecordEvent
import io.github.solcott.activitytracker.app.record.RecordPresenter
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.app.record.RecordState
import io.github.solcott.activitytracker.app.trim.TrimScreen
import io.github.solcott.activitytracker.data.activity.trim.KeptSpan
import io.github.solcott.activitytracker.data.activity.trim.TrimDrafts
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
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
import kotlinx.serialization.json.jsonPrimitive

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
        presenterTestOf(
            {
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
                    TrimDrafts(),
                )
            },
        ) {
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
        presenterTestOf(
            {
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
                    TrimDrafts(),
                )
            },
        ) {
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
        presenterTestOf(
            {
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
                    TrimDrafts(),
                )
            },
        ) {
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
            TrimDrafts(),
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
    fun aFinishedRecordingSavesItsDescription() = runTest {
        val record = finishWithDescription(" Dusty climbs, then a tailwind home. ")
        assertEquals("Dusty climbs, then a tailwind home.", record.getValue("description").jsonPrimitive.content)
    }

    @Test
    fun aBlankDescriptionSavesNone() = runTest {
        val record = finishWithDescription("   ")
        assertFalse("description" in record, "a blank description leaves the field out")
    }

    @Test
    fun aTitlePastItsLimitHoldsFinishBack() = runTest {
        val repos = fixture.repos()
        presenterTestOf(presenter(recorder(InMemoryRecordingJournal()), repos)) {
            val initial = awaitItem()
            initial.eventSink(RecordEvent.Start)
            awaitUntil { it.distanceMeters > 0 }.eventSink(RecordEvent.Pause)
            initial.title.setTextAndPlaceCursorAtEnd("x".repeat(101))
            val tooLong = awaitUntil { it.status == RecordingState.Status.Paused && !it.textsFit }
            assertFalse(tooLong.enabled(RecordAction.Finish))
            assertTrue(tooLong.enabled(RecordAction.Resume), "only Finish waits")
            tooLong.eventSink(RecordEvent.Finish)
            initial.title.setTextAndPlaceCursorAtEnd("Morning loop")
            assertTrue(awaitUntil { it.textsFit }.enabled(RecordAction.Finish))
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(fixture.putRecords.isEmpty(), "Finish while too long published nothing")
    }

    /** A clock the test moves: the recording's elapsed time comes from it. */
    private class StepClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    @Test
    fun aPausedRecordingIsOfferedForTrimmingAndPublishesTrimmed() = runTest {
        val repos = fixture.repos()
        val drafts = TrimDrafts()
        // Ninety seconds of riding: a suggestion keeps at least a minute.
        val ride =
            (0..90).map {
                LocationSample(
                    0.0,
                    it * 0.0001,
                    null,
                    speed = null,
                    accuracy = 5.0,
                    time = Instant.parse("2026-10-24T07:00:00Z") + it.seconds,
                )
            }
        val clock = StepClock(ride.first().time)
        val recorder =
            ActivityRecorder(
                FakeLocationTracker(ride),
                clock,
                InMemoryRecordingJournal(),
                dispatcher = StandardTestDispatcher(testScheduler),
            )
        val navigator = FakeNavigator(RecordScreen)
        presenterTestOf(
            {
                RecordPresenter(
                    navigator,
                    recorder,
                    tracker,
                    repos.activities,
                    repos.typeChoices,
                    repos.gearChoices,
                    repos.defaults,
                    clock,
                    repos.profiles,
                    drafts,
                )
            },
        ) {
            awaitItem().eventSink(RecordEvent.Start)
            // The ride, then minutes standing before the athlete pauses at ten minutes.
            val riding = awaitUntil { it.distanceMeters >= 990.0 && it.status == RecordingState.Status.Recording }
            clock.now = ride.first().time + 600.seconds
            riding.eventSink(RecordEvent.Pause)
            val offered = awaitUntil { it.trim != null }
            assertFalse(offered.trim!!.applied)
            assertTrue(offered.trim!!.suggestion?.end != null, "the standing at the end is suggested")

            offered.eventSink(RecordEvent.Trim)
            assertEquals(TrimScreen(draftId = "recording"), navigator.awaitNextScreen())
            // What the trim screen's Done does.
            drafts.apply("recording", KeptSpan(0, 300))
            awaitUntil { it.trim?.applied == true }.eventSink(RecordEvent.Finish)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val record = fixture.putRecords.first { it["elapsedTime"] != null }
        assertEquals(300, record.getValue("elapsedTime").jsonPrimitive.content.toInt())
    }

    /** Records [route], finishes with [description] typed, and returns the activity record put. */
    private suspend fun TestScope.finishWithDescription(description: String): JsonObject {
        val repos = fixture.repos()
        val navigator = FakeNavigator(RecordScreen)
        presenterTestOf(presenter(recorder(InMemoryRecordingJournal()), repos, navigator)) {
            val initial = awaitItem()
            initial.description.setTextAndPlaceCursorAtEnd(description)
            initial.eventSink(RecordEvent.Start)
            awaitUntil { it.distanceMeters > 0 }.eventSink(RecordEvent.Finish)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        return fixture.putRecords.first { it["\$type"]?.jsonPrimitive?.content == Nsid.FEED_ACTIVITY }
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
