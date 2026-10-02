package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.launch

/** Log an activity without GPS (also handy for testing on web and simulators). */
@CircuitSerializable(SessionScope::class) data object ManualActivityScreen : Screen

data class ManualActivityState(
    val sport: String,
    val title: String,
    val distanceKm: String,
    val durationMinutes: String,
    val saving: Boolean,
    val error: String?,
    val eventSink: (ManualActivityEvent) -> Unit,
) : CircuitUiState {
    val valid: Boolean
        get() = (durationMinutes.toDoubleOrNull() ?: 0.0) > 0
}

sealed interface ManualActivityEvent {
    data class SportChanged(val value: String) : ManualActivityEvent

    data class TitleChanged(val value: String) : ManualActivityEvent

    data class DistanceChanged(val value: String) : ManualActivityEvent

    data class DurationChanged(val value: String) : ManualActivityEvent

    data object Save : ManualActivityEvent

    data object Back : ManualActivityEvent
}

@CircuitInject(ManualActivityScreen::class, SessionScope::class)
@Composable
fun ManualActivityPresenter(
    navigator: Navigator,
    activities: ActivityRepository,
    clock: Clock,
): ManualActivityState {
    var sport by rememberSaveable { mutableStateOf(Sport.RUN) }
    var title by rememberSaveable { mutableStateOf("") }
    var distance by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    return ManualActivityState(sport, title, distance, duration, saving, error) { event ->
        when (event) {
            is ManualActivityEvent.SportChanged -> {
                sport = event.value
            }

            is ManualActivityEvent.TitleChanged -> {
                title = event.value
            }

            is ManualActivityEvent.DistanceChanged -> {
                distance = event.value
            }

            is ManualActivityEvent.DurationChanged -> {
                duration = event.value
            }

            ManualActivityEvent.Back -> {
                navigator.pop()
            }

            ManualActivityEvent.Save -> {
                val minutes = duration.toDoubleOrNull() ?: return@ManualActivityState
                saving = true
                scope.launch {
                    try {
                        val elapsed = (minutes * 60).toLong()
                        activities.publish(
                            ActivityDraft(
                                sport = sport,
                                title = title.ifBlank { null },
                                description = null,
                                startedAt = clock.now() - minutes.minutes,
                                elapsed = minutes.minutes,
                                movingSeconds = elapsed,
                                distanceMeters = distance.toDoubleOrNull()?.let { it * 1000 },
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@CircuitInject(ManualActivityScreen::class, SessionScope::class)
@Composable
fun ManualActivityUi(state: ManualActivityState, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Add activity") },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(ManualActivityEvent.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SportPicker(state.sport) { state.eventSink(ManualActivityEvent.SportChanged(it)) }
            OutlinedTextField(
                state.title,
                { state.eventSink(ManualActivityEvent.TitleChanged(it)) },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                state.distanceKm,
                { state.eventSink(ManualActivityEvent.DistanceChanged(it)) },
                label = { Text("Distance (km)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                state.durationMinutes,
                { state.eventSink(ManualActivityEvent.DurationChanged(it)) },
                label = { Text("Duration (minutes)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { state.eventSink(ManualActivityEvent.Save) },
                enabled = state.valid && !state.saving,
            ) {
                Text(if (state.saving) "Saving…" else "Save")
            }
        }
    }
}
