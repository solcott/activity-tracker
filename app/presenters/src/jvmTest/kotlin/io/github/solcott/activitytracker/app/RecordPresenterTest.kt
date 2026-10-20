package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.record.RecordEvent
import io.github.solcott.activitytracker.app.record.RecordPresenter
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.location.ActivityRecorder
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.activitytracker.location.RecordingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

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
            RecordPresenter(navigator, recorder, tracker, repos.activities, repos.typeChoices, repos.defaults, Clock.System)
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
}
