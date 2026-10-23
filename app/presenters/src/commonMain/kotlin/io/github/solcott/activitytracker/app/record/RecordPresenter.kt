package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.rememberNewActivityVisibility
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityDefaults
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
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
    typeChoices: ActivityTypeChoices,
    gearChoices: GearChoices,
    defaults: ActivityDefaults,
    clock: Clock,
): RecordState {
    val recording by recorder.state.collectAsState()
    val title = rememberTextFieldState()
    var hasPermission by remember { mutableStateOf(tracker.hasPermission()) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var now by remember { mutableStateOf(clock.now()) }
    val flights = rememberSingleFlight()
    val saving = flights.isRunning(RecordEvent.Finish)
    // GPS recording: indoor types are for manual entry and import.
    val type = typeChoices.produce(ActivityType(Sport.RUN), outdoorOnly = true)
    val gear = gearChoices.produce(type.selected)
    // Offered before there's any data: what a GPS recording holds, and Gear once there's some.
    val visibility = rememberNewActivityVisibility(defaults, gear.offering(ActivityDraft.RECORDED_CATEGORIES))

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
        type = type.field,
        title = title,
        hasPermission = hasPermission,
        saving = saving,
        error = error,
        visibility = visibility.fields,
        gear = gear.field,
    ) { event ->
        when (event) {
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
                        val saved =
                            activities.publish(
                                ActivityDraft.fromRecording(
                                        result,
                                        type.selected,
                                        title.text.toString().ifBlank { null },
                                        null,
                                    )
                                    .copy(gear = gear.selected)
                                    .withVisibility(visibility.current)
                            )
                        // The saved activity opens, asking whether to combine it with another of the athlete's.
                        navigator.pop()
                        navigator.goTo(ActivityDetailScreen(saved.value, suggestOnOpen = true))
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
