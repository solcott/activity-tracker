package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.rememberNewActivityVisibility
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityDefaults
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.location.ActivityRecorder
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.activitytracker.location.RecordingState
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

@CircuitInject(RecordScreen::class, SessionScope::class)
@Composable
fun RecordPresenter(
    navigator: Navigator,
    recorder: ActivityRecorder,
    tracker: LocationTracker,
    activities: ActivityRepository,
    recentTypes: RecentActivityTypes,
    defaults: ActivityDefaults,
    clock: Clock,
): RecordState {
    val recording by recorder.state.collectAsState()
    var type by rememberSaveable(stateSaver = ActivityTypeSaver) { mutableStateOf(ActivityType(Sport.RUN)) }
    val title = rememberTextFieldState()
    var hasPermission by remember { mutableStateOf(tracker.hasPermission()) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var now by remember { mutableStateOf(clock.now()) }
    val flights = rememberSingleFlight()
    val saving = flights.isRunning(RecordEvent.Finish)
    // GPS recording: indoor types are for manual entry and import.
    val quickTypes = quickActivityTypes(recentTypes, outdoorOnly = true)
    val visibility = rememberNewActivityVisibility(defaults)

    LaunchedEffect(recording.status) {
        while (recording.status == RecordingState.Status.Recording) {
            now = clock.now()
            delay(1_000.milliseconds)
        }
    }

    return RecordState(
        status = recording.status,
        elapsed = recording.elapsed(now),
        distanceMeters = recording.distanceMeters,
        movingSeconds = recording.movingSeconds,
        elevationGainMeters = recording.elevationGainMeters,
        type = type,
        quickTypes = quickTypes,
        title = title,
        hasPermission = hasPermission,
        saving = saving,
        error = error,
        visibility = visibility.fields,
    ) { event ->
        when (event) {
            is RecordEvent.VisibilityChanged -> {
                visibility.activityChanged(event.visibility)
            }

            is RecordEvent.ChartsVisibilityChanged -> {
                visibility.chartsChanged(event.visibility)
            }

            is RecordEvent.TypeChanged -> {
                type = event.type
            }

            is RecordEvent.PermissionResult -> {
                hasPermission = event.granted
                if (event.granted) recorder.start()
            }

            RecordEvent.Start -> {
                recorder.start()
            }

            RecordEvent.Pause -> {
                recorder.pause()
            }

            RecordEvent.Resume -> {
                recorder.resume()
            }

            RecordEvent.Discard -> {
                recorder.stop()
                navigator.pop()
            }

            RecordEvent.Finish -> {
                val result = recorder.stop() ?: return@RecordState
                flights.launch(RecordEvent.Finish) {
                    runSuspendCatching {
                        activities.publish(
                            ActivityDraft.fromRecording(
                                    result,
                                    type,
                                    title.text.toString().ifBlank { null },
                                    null,
                                )
                                .withVisibility(visibility.current)
                        )
                        navigator.pop()
                    }
                        .onFailure { e -> error = uiErrorOf(UiError.Kind.SaveActivity, e) }
                }
            }

            RecordEvent.Back -> {
                navigator.pop()
            }
        }
    }
}
