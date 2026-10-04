package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.location.ActivityRecorder
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.activitytracker.location.RecordingState
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@CircuitInject(RecordScreen::class, SessionScope::class)
@Composable
fun RecordPresenter(
    navigator: Navigator,
    recorder: ActivityRecorder,
    tracker: LocationTracker,
    activities: ActivityRepository,
    clock: Clock,
): RecordState {
    val recording by recorder.state.collectAsState()
    var sport by rememberSaveable { mutableStateOf(Sport.RUN) }
    var title by rememberSaveable { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(tracker.hasPermission()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var now by remember { mutableStateOf(clock.now()) }
    val scope = rememberCoroutineScope()

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
        sport = sport,
        title = title,
        hasPermission = hasPermission,
        saving = saving,
        error = error,
    ) { event ->
        when (event) {
            is RecordEvent.SportChanged -> {
                sport = event.sport
            }

            is RecordEvent.TitleChanged -> {
                title = event.title
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
                saving = true
                scope.launch {
                    runSuspendCatching {
                        activities.publish(
                            ActivityDraft.fromRecording(
                                result,
                                sport,
                                title.ifBlank { null },
                                null,
                            )
                        )
                        navigator.pop()
                    }
                        .onFailure { e -> error = UiError.of(UiError.Kind.SaveActivity, e) }
                        .also { saving = false }
                }
            }

            RecordEvent.Back -> {
                navigator.pop()
            }
        }
    }
}
