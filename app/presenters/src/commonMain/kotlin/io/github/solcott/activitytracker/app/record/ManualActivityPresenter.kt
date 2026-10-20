package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.data.units.parseDecimal
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.toImmutableSet

@CircuitInject(ManualActivityScreen::class, SessionScope::class)
@Composable
fun ManualActivityPresenter(
    navigator: Navigator,
    activities: ActivityRepository,
    unitsRepository: UnitsRepository,
    typeChoices: ActivityTypeChoices,
    defaults: ActivityDefaults,
    clock: Clock,
): ManualActivityState {
    val type = typeChoices.produce(ActivityType(Sport.RUN))
    val title = rememberTextFieldState()
    val distance = rememberTextFieldState()
    val duration = rememberTextFieldState()
    val temperature = rememberTextFieldState()
    var units by rememberSaveable { mutableStateOf(unitsRepository.units.value) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    val saving = flights.isRunning(ManualActivityEvent.Save)
    // Read in composition, so the categories offered follow the fields: Pace and speed once a distance is entered.
    val offered =
        manualDraft(type.selected, title.text, distance.text, parseDecimal(duration.text.toString()) ?: 0.0, temperature.text, units, clock)
            .heldCategories()
            .toImmutableSet()
    val visibility = rememberNewActivityVisibility(defaults, offered)

    return ManualActivityState(type.field, title, distance, duration, temperature, units, saving, error, visibility.fields) { event ->
        when (event) {
            is ManualActivityEvent.UnitsChanged -> {
                units = event.value
            }

            ManualActivityEvent.Back -> {
                navigator.pop()
            }

            ManualActivityEvent.Save -> {
                val minutes = parseDecimal(duration.text.toString()) ?: return@ManualActivityState
                flights.launch(ManualActivityEvent.Save) {
                    runSuspendCatching {
                        val saved =
                            activities.publish(
                                manualDraft(type.selected, title.text, distance.text, minutes, temperature.text, units, clock)
                                    .withVisibility(visibility.current)
                            )
                        // The saved activity opens, asking whether to combine it with another of the athlete's.
                        navigator.pop()
                        navigator.goTo(ActivityDetailScreen(saved.value, suggestOnOpen = true))
                    }
                        .onFailure { e -> error = uiErrorOf(UiError.Kind.SaveActivity, e) }
                }
            }
        }
    }
}

/** The activity the manual entry's fields describe, ending now and lasting [minutes]. */
@Suppress("LongParameterList") // One per field of the form.
private fun manualDraft(
    type: ActivityType,
    title: CharSequence,
    distance: CharSequence,
    minutes: Double,
    temperature: CharSequence,
    units: UnitSystem,
    clock: Clock,
): ActivityDraft =
    ActivityDraft(
        type = type,
        title = title.toString().ifBlank { null },
        description = null,
        startedAt = clock.now() - minutes.minutes,
        elapsed = minutes.minutes,
        movingSeconds = (minutes * 60).toLong(),
        distanceMeters = parseDecimal(distance.toString())?.let { UnitConversions.fromDistanceUnit(it, units) },
        avgTemperatureCelsius = parseDecimal(temperature.toString())?.let { UnitConversions.fromTemperatureUnit(it, units) },
    )
