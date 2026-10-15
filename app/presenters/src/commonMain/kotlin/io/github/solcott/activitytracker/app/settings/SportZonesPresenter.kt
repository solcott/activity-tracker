package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.profile.updateTraining
import io.github.solcott.activitytracker.data.training.PaceText
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.data.training.SportZones
import io.github.solcott.activitytracker.data.training.ThresholdInput
import io.github.solcott.activitytracker.data.training.ZoneKind
import io.github.solcott.activitytracker.data.training.ZoneModels
import io.github.solcott.activitytracker.data.training.Zones
import io.github.solcott.activitytracker.data.training.forSport
import io.github.solcott.activitytracker.data.training.override
import io.github.solcott.activitytracker.data.training.withOverride
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.lexicon.app.SportTraining
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.TrainingSetAt
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

@CircuitInject(SportZonesScreen::class, SessionScope::class)
@Composable
fun SportZonesPresenter(
    screen: SportZonesScreen,
    navigator: Navigator,
    profiles: ProfileRepository,
    units: UnitsRepository,
    clock: Clock,
): SportZonesState {
    val sport = screen.sport
    val paceUnit = SportZones.paceUnit(sport, units.units.collectAsState().value)
    val form = rememberSportForm()
    // As saved, for the defaults.
    var saved by remember { mutableStateOf<TrainingDetails?>(null) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    val saving = flights.isRunning(SportZonesEvent.Save) || flights.isRunning(SportZonesEvent.Remove)

    LaunchedEffect(Unit) {
        runSuspendCatching { profiles.ownProfile() }
            .onSuccess {
                val training = it.details.training ?: TrainingDetails()
                form.fillOnce(training.override(sport), paceUnit)
                saved = training
            }
            .onFailure { error = UiError.of(UiError.Kind.LoadProfile, it) }
    }

    // Saves [transform] of the training as saved now, then leaves; once loaded, and one write at a time.
    fun write(key: SportZonesEvent, transform: (TrainingDetails) -> TrainingDetails) {
        if (saved == null || saving) return
        error = null
        flights.launch(key) {
            runSuspendCatching { profiles.updateTraining(transform = transform) }
                .onSuccess { navigator.pop() }
                .onFailure { error = uiErrorOf(UiError.Kind.SaveZones, it) }
        }
    }

    val base = saved ?: TrainingDetails()
    val defaults = base.forSport(null)
    // Reading the typed text here keeps the zones live as the athlete types.
    val effective = base.withOverride(sport, form.entry(sport, paceUnit)).forSport(sport)
    val heartRateModel = ZoneModels.heartRate(effective.heartRateZoneModel)
    val powerModel = ZoneModels.power(effective.powerZoneModel)
    val speedModel = ZoneModels.speed(effective.speedZoneModel, sport)

    return SportZonesState(
        sport = sport,
        kinds = SportZones.kinds(sport).toImmutableSet(),
        thresholdHeartRate = form.thresholdHeartRate,
        maxHeartRate = form.maxHeartRate,
        functionalThresholdPower = form.functionalThresholdPower,
        thresholdPace = form.thresholdPace,
        paceUnit = paceUnit,
        defaults = defaults,
        heartRateModel = heartRateModel,
        heartRateModels = ZoneModels.heartRate.toImmutableList(),
        heartRateZones = Zones.compute(heartRateModel, effective)?.toImmutableList(),
        powerModel = powerModel,
        powerModels = ZoneModels.power.toImmutableList(),
        powerZones = Zones.compute(powerModel, effective)?.toImmutableList(),
        speedModel = speedModel,
        speedModels = ZoneModels.speed.toImmutableList(),
        speedZones = Zones.compute(speedModel, effective)?.toImmutableList(),
        saved = saved?.override(sport) != null,
        loaded = saved != null,
        saving = saving,
        error = error,
    ) { event ->
        when (event) {
            SportZonesEvent.Back -> {
                navigator.pop()
            }

            is SportZonesEvent.HeartRateModelChanged -> {
                form.heartRateModel.value = event.token
            }

            is SportZonesEvent.PowerModelChanged -> {
                form.powerModel.value = event.token
            }

            is SportZonesEvent.SpeedModelChanged -> {
                form.speedModel.value = event.token
            }

            SportZonesEvent.Save -> {
                val entry = form.entry(sport, paceUnit)
                val now = clock.now()
                write(SportZonesEvent.Save) { current -> current.withOverride(sport, entry.settingTimes(current.override(sport), now)) }
            }

            SportZonesEvent.Remove -> {
                write(SportZonesEvent.Remove) { it.withOverride(sport, null) }
            }
        }
    }
}

/** The sport's thresholds as typed and its picked models, saved across configuration changes. A model is a token, or empty to inherit. */
@Suppress("LongParameterList") // One field per form input.
private class SportForm(
    val thresholdHeartRate: TextFieldState,
    val maxHeartRate: TextFieldState,
    val functionalThresholdPower: TextFieldState,
    val thresholdPace: TextFieldState,
    val heartRateModel: MutableState<String>,
    val powerModel: MutableState<String>,
    val speedModel: MutableState<String>,
    /** True once loaded, so a configuration change keeps what the athlete typed instead of reloading it. */
    val filled: MutableState<Boolean>,
) {
    fun fillOnce(saved: SportTraining?, paceUnit: PaceUnit) {
        if (filled.value) return
        thresholdHeartRate.setTextAndPlaceCursorAtEnd(saved?.thresholdHeartRate?.toString().orEmpty())
        maxHeartRate.setTextAndPlaceCursorAtEnd(saved?.maxHeartRate?.toString().orEmpty())
        functionalThresholdPower.setTextAndPlaceCursorAtEnd(saved?.functionalThresholdPower?.toString().orEmpty())
        thresholdPace.setTextAndPlaceCursorAtEnd(saved?.thresholdSpeed?.let { PaceText.format(it, paceUnit) }.orEmpty())
        heartRateModel.value = saved?.heartRateZoneModel.orEmpty()
        powerModel.value = saved?.powerZoneModel.orEmpty()
        speedModel.value = saved?.speedZoneModel.orEmpty()
        filled.value = true
    }

    /** What would be saved for [sport]. Blank or out-of-range thresholds and unpicked models are left out, so they inherit the defaults. */
    fun entry(sport: String, paceUnit: PaceUnit): SportTraining {
        val kinds = SportZones.kinds(sport)
        val power = ZoneKind.Power in kinds
        val speed = ZoneKind.Speed in kinds
        return SportTraining(
            sport = sport,
            functionalThresholdPower = ThresholdInput.power(functionalThresholdPower.text.toString()).takeIf { power },
            maxHeartRate = ThresholdInput.heartRate(maxHeartRate.text.toString()),
            thresholdHeartRate = ThresholdInput.heartRate(thresholdHeartRate.text.toString()),
            thresholdSpeed = ThresholdInput.pace(thresholdPace.text.toString(), paceUnit).takeIf { speed },
            heartRateZoneModel = heartRateModel.value.ifEmpty { null },
            powerZoneModel = powerModel.value.ifEmpty { null }.takeIf { power },
            speedZoneModel = speedModel.value.ifEmpty { null }.takeIf { speed },
        )
    }
}

/** This with the times each threshold was set carried over from [before], and [now] for each one the athlete changed. */
private fun SportTraining.settingTimes(before: SportTraining?, now: Instant): SportTraining {
    var times = before?.setAt ?: TrainingSetAt()
    if (functionalThresholdPower != before?.functionalThresholdPower) times = times.copy(functionalThresholdPower = now)
    if (maxHeartRate != before?.maxHeartRate) times = times.copy(maxHeartRate = now)
    if (thresholdHeartRate != before?.thresholdHeartRate) times = times.copy(thresholdHeartRate = now)
    if (thresholdSpeed != before?.thresholdSpeed) times = times.copy(thresholdSpeed = now)
    return copy(setAt = times.takeUnless { it == TrainingSetAt() })
}

@Composable
private fun rememberSportForm(): SportForm =
    SportForm(
        thresholdHeartRate = rememberTextFieldState(),
        maxHeartRate = rememberTextFieldState(),
        functionalThresholdPower = rememberTextFieldState(),
        thresholdPace = rememberTextFieldState(),
        heartRateModel = rememberSaveable { mutableStateOf("") },
        powerModel = rememberSaveable { mutableStateOf("") },
        speedModel = rememberSaveable { mutableStateOf("") },
        filled = rememberSaveable { mutableStateOf(false) },
    )
