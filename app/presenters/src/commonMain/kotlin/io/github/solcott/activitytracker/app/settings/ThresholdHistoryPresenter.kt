package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.activityfile.TimeZones
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.profile.removeThresholdEntry
import io.github.solcott.activitytracker.data.training.SportZones
import io.github.solcott.activitytracker.data.training.TrainingHistory
import io.github.solcott.activitytracker.data.training.TrainingHistory.key
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.ThresholdEntry
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@CircuitInject(ThresholdHistoryScreen::class, SessionScope::class)
@Composable
fun ThresholdHistoryPresenter(
    navigator: Navigator,
    profiles: ProfileRepository,
    units: UnitsRepository,
): ThresholdHistoryState {
    val timeZone = remember { TimeZones.device() }
    val unitSystem = units.units.collectAsState().value
    var training by remember { mutableStateOf<TrainingDetails?>(null) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var removing by remember { mutableStateOf<ThresholdEntry?>(null) }
    val flights = rememberSingleFlight()

    LaunchedEffect(Unit) {
        runSuspendCatching { profiles.ownProfile() }
            .onSuccess { training = TrainingHistory.backfilled(it.details.training ?: TrainingDetails()) }
            .onFailure { error = UiError.of(UiError.Kind.LoadProfile, it) }
    }

    return ThresholdHistoryState(
        sections = training?.let { sections(it, unitSystem, timeZone).toImmutableList() },
        removing = removing,
        error = error,
    ) { event ->
        when (event) {
            ThresholdHistoryEvent.Back -> {
                navigator.pop()
            }

            is ThresholdHistoryEvent.Remove -> {
                val loaded = training
                if (loaded != null) {
                    flights.launch(ThresholdHistoryEvent.Remove::class) {
                        error = null
                        removing = event.entry
                        runSuspendCatching { profiles.removeThresholdEntry(event.entry) }
                            .onSuccess { training = TrainingHistory.remove(loaded, event.entry) }
                            .onFailure { error = UiError.of(UiError.Kind.SaveZones, it) }
                        removing = null
                    }
                }
            }
        }
    }
}

/** The order thresholds show in: as on the zones overview. */
private val order =
    listOf(
        Threshold.THRESHOLD_SPEED,
        Threshold.THRESHOLD_HEART_RATE,
        Threshold.MAX_HEART_RATE,
        Threshold.RESTING_HEART_RATE,
        Threshold.FUNCTIONAL_THRESHOLD_POWER,
    )

/**
 * A section per threshold with history: the defaults', then each sport's in saved order, then sports with history and no override left.
 * Thresholds this client doesn't know go after the known ones.
 */
private fun sections(training: TrainingDetails, units: UnitSystem, timeZone: TimeZone): List<ThresholdHistorySection> {
    val sportOrder = listOf(null) + training.sports.map { it.sport }
    val keys =
        training.history
            .map { it.key }
            .distinct()
            .sortedWith(
                compareBy(
                    { sportOrder.indexOf(it.sport).takeIf { i -> i >= 0 } ?: sportOrder.size },
                    { it.sport },
                    { order.indexOf(it.threshold).takeIf { i -> i >= 0 } ?: order.size },
                    { it.threshold },
                )
            )
    return keys.map { key ->
        val entries = TrainingHistory.entries(training.history, key)
        val value = TrainingHistory.value(training, key)
        // Only the newest entry can be current, and only while the threshold still has its value.
        val current = entries.first().takeIf { it.value == value }
        ThresholdHistorySection(
            threshold = key.threshold,
            sport = key.sport,
            paceUnit = SportZones.paceUnit(key.sport, units),
            rows =
                entries
                    .map { ThresholdHistoryRow(it, it.setAt?.toLocalDateTime(timeZone)?.date?.toString(), current = it == current) }
                    .toImmutableList(),
        )
    }
}
