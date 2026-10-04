package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Composable
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
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.launch

@CircuitInject(ManualActivityScreen::class, SessionScope::class)
@Composable
fun ManualActivityPresenter(
    navigator: Navigator,
    activities: ActivityRepository,
    unitsRepository: UnitsRepository,
    clock: Clock,
): ManualActivityState {
    var sport by rememberSaveable { mutableStateOf(Sport.RUN) }
    var title by rememberSaveable { mutableStateOf("") }
    var distance by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf("") }
    var temperature by rememberSaveable { mutableStateOf("") }
    var units by rememberSaveable { mutableStateOf(unitsRepository.units.value) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val scope = rememberCoroutineScope()

    return ManualActivityState(sport, title, distance, duration, temperature, units, saving, error) { event ->
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

            is ManualActivityEvent.TemperatureChanged -> {
                temperature = event.value
            }

            is ManualActivityEvent.UnitsChanged -> {
                units = event.value
            }

            ManualActivityEvent.Back -> {
                navigator.pop()
            }

            ManualActivityEvent.Save -> {
                val minutes = duration.toDoubleOrNull() ?: return@ManualActivityState
                saving = true
                scope.launch {
                    runSuspendCatching {
                        val elapsed = (minutes * 60).toLong()
                        activities.publish(
                            ActivityDraft(
                                sport = sport,
                                title = title.ifBlank { null },
                                description = null,
                                startedAt = clock.now() - minutes.minutes,
                                elapsed = minutes.minutes,
                                movingSeconds = elapsed,
                                distanceMeters = distance.toDoubleOrNull()?.let { UnitConversions.fromDistanceUnit(it, units) },
                                avgTemperatureCelsius =
                                    temperature.toDoubleOrNull()?.let { UnitConversions.fromTemperatureUnit(it, units) },
                            )
                        )
                        navigator.pop()
                    }
                        .onFailure { e -> error = UiError.of(UiError.Kind.SaveActivity, e) }
                        .also { saving = false }
                }
            }
        }
    }
}
