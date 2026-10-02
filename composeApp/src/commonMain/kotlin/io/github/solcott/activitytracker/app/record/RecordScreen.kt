package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.StatsRow
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.location.ActivityRecorder
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.activitytracker.location.RecordingState
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@CircuitSerializable(SessionScope::class) data object RecordScreen : Screen

data class RecordState(
    val status: RecordingState.Status,
    val elapsed: Duration,
    val distanceMeters: Double,
    val movingSeconds: Long,
    val elevationGainMeters: Double,
    val sport: String,
    val title: String,
    val hasPermission: Boolean,
    val saving: Boolean,
    val error: String?,
    val eventSink: (RecordEvent) -> Unit,
) : CircuitUiState

sealed interface RecordEvent {
    data class SportChanged(val sport: String) : RecordEvent

    data class TitleChanged(val title: String) : RecordEvent

    data class PermissionResult(val granted: Boolean) : RecordEvent

    data object Start : RecordEvent

    data object Pause : RecordEvent

    data object Resume : RecordEvent

    data object Finish : RecordEvent

    data object Discard : RecordEvent

    data object Back : RecordEvent
}

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
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(clock.now()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(recording.status) {
        while (recording.status == RecordingState.Status.Recording) {
            now = clock.now()
            delay(1_000)
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
                    try {
                        activities.publish(
                            ActivityDraft.fromRecording(
                                result,
                                sport,
                                title.ifBlank { null },
                                null,
                            )
                        )
                        navigator.pop()
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        saving = false
                    }
                }
            }

            RecordEvent.Back -> {
                navigator.pop()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(RecordScreen::class, SessionScope::class)
@Composable
fun RecordUi(state: RecordState, modifier: Modifier = Modifier) {
    val requestPermission = rememberLocationPermissionRequest { state.eventSink(RecordEvent.PermissionResult(it)) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Record") },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(RecordEvent.Back) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SportPicker(state.sport, enabled = state.status == RecordingState.Status.Idle) { state.eventSink(RecordEvent.SportChanged(it)) }
            Text(Format.duration(state.elapsed), style = MaterialTheme.typography.displayLarge)
            StatsRow(
                "Distance" to Format.distance(state.distanceMeters),
                "Pace" to Format.pace(state.distanceMeters, state.movingSeconds),
                "Elevation" to Format.elevation(state.elevationGainMeters),
            )
            OutlinedTextField(
                value = state.title,
                onValueChange = { state.eventSink(RecordEvent.TitleChanged(it)) },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state.status) {
                    RecordingState.Status.Idle -> {
                        Button(
                            onClick = {
                                if (state.hasPermission) {
                                    state.eventSink(RecordEvent.Start)
                                } else {
                                    requestPermission()
                                }
                            },
                            enabled = !state.saving,
                        ) {
                            Text("Start")
                        }
                    }

                    RecordingState.Status.Recording -> {
                        Button(onClick = { state.eventSink(RecordEvent.Pause) }) { Text("Pause") }
                    }

                    RecordingState.Status.Paused -> {
                        Button(onClick = { state.eventSink(RecordEvent.Resume) }) { Text("Resume") }
                        Button(onClick = { state.eventSink(RecordEvent.Finish) }) { Text("Finish & save") }
                        OutlinedButton(onClick = { state.eventSink(RecordEvent.Discard) }) { Text("Discard") }
                    }
                }
            }
        }
    }
}

@Composable
fun SportPicker(selected: String, enabled: Boolean = true, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Sport.all) { sport ->
            FilterChip(
                selected = sport == selected,
                onClick = { onSelect(sport) },
                enabled = enabled,
                label = { Text(sport.replaceFirstChar { it.uppercase() }) },
            )
        }
    }
}
