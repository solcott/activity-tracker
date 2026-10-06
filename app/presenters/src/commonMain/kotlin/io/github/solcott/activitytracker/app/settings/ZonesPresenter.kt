package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.data.profile.OwnProfile
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.profile.saveTraining
import io.github.solcott.activitytracker.data.training.ThresholdInput
import io.github.solcott.activitytracker.data.training.ZoneModels
import io.github.solcott.activitytracker.data.training.Zones
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.collections.immutable.toImmutableList

@CircuitInject(ZonesScreen::class, SessionScope::class)
@Composable
fun ZonesPresenter(navigator: Navigator, profiles: ProfileRepository): ZonesState {
    val form = rememberForm()
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    val saving = flights.isRunning(ZonesEvent.Save)

    LaunchedEffect(Unit) {
        runSuspendCatching { profiles.ownProfile() }
            .onSuccess {
                form.fillOnce(it)
                loaded = true
            }
            .onFailure { error = UiError.of(UiError.Kind.LoadProfile, it) }
    }

    // Reading the typed text here keeps the zones live as the athlete types.
    val training = form.training()
    val heartRateModel = ZoneModels.heartRate(form.heartRateModel.value)
    val powerModel = ZoneModels.power(form.powerModel.value)

    return ZonesState(
        thresholdHeartRate = form.thresholdHeartRate,
        maxHeartRate = form.maxHeartRate,
        restingHeartRate = form.restingHeartRate,
        functionalThresholdPower = form.functionalThresholdPower,
        heartRateModel = heartRateModel,
        heartRateModels = ZoneModels.heartRate.toImmutableList(),
        heartRateZones = Zones.compute(heartRateModel, training)?.toImmutableList(),
        powerModel = powerModel,
        powerModels = ZoneModels.power.toImmutableList(),
        powerZones = Zones.compute(powerModel, training)?.toImmutableList(),
        visibility = form.visibility.value,
        loaded = loaded,
        saving = saving,
        error = error,
    ) { event ->
        when (event) {
            ZonesEvent.Back -> {
                navigator.pop()
            }

            is ZonesEvent.HeartRateModelChanged -> {
                form.heartRateModel.value = event.token
            }

            is ZonesEvent.PowerModelChanged -> {
                form.powerModel.value = event.token
            }

            is ZonesEvent.VisibilityChanged -> {
                form.visibility.value = event.visibility
            }

            ZonesEvent.Save -> {
                if (loaded && !saving) {
                    error = null
                    val saved = form.training().takeUnless { it == TrainingDetails() }
                    val visibility = form.visibility.value
                    flights.launch(ZonesEvent.Save) {
                        runSuspendCatching { profiles.saveTraining(saved, visibility) }
                            .onSuccess { navigator.pop() }
                            .onFailure { error = UiError.of(UiError.Kind.SaveZones, it) }
                    }
                }
            }
        }
    }
}

/** The thresholds as typed and the picked models, saved across configuration changes. A model is a token, or empty for the default. */
private class Form(
    val thresholdHeartRate: TextFieldState,
    val maxHeartRate: TextFieldState,
    val restingHeartRate: TextFieldState,
    val functionalThresholdPower: TextFieldState,
    val heartRateModel: MutableState<String>,
    val powerModel: MutableState<String>,
    val visibility: MutableState<String>,
    /** True once loaded, so a configuration change keeps what the athlete typed instead of reloading it. */
    val filled: MutableState<Boolean>,
) {
    fun fillOnce(profile: OwnProfile) {
        if (filled.value) return
        val saved = profile.details.training ?: TrainingDetails()
        thresholdHeartRate.setTextAndPlaceCursorAtEnd(saved.thresholdHeartRate?.toString().orEmpty())
        maxHeartRate.setTextAndPlaceCursorAtEnd(saved.maxHeartRate?.toString().orEmpty())
        restingHeartRate.setTextAndPlaceCursorAtEnd(saved.restingHeartRate?.toString().orEmpty())
        functionalThresholdPower.setTextAndPlaceCursorAtEnd(saved.functionalThresholdPower?.toString().orEmpty())
        heartRateModel.value = saved.heartRateZoneModel.orEmpty()
        powerModel.value = saved.powerZoneModel.orEmpty()
        visibility.value = DetailGroup.Training.visibility(profile.visibility) ?: Visibility.SELF
        filled.value = true
    }

    /** What would be saved. Blank or out-of-range thresholds and unpicked models are left out. */
    fun training(): TrainingDetails =
        TrainingDetails(
            functionalThresholdPower = ThresholdInput.power(functionalThresholdPower.text.toString()),
            maxHeartRate = ThresholdInput.heartRate(maxHeartRate.text.toString()),
            thresholdHeartRate = ThresholdInput.heartRate(thresholdHeartRate.text.toString()),
            restingHeartRate = ThresholdInput.heartRate(restingHeartRate.text.toString()),
            heartRateZoneModel = heartRateModel.value.ifEmpty { null },
            powerZoneModel = powerModel.value.ifEmpty { null },
        )
}

@Composable
private fun rememberForm(): Form =
    Form(
        thresholdHeartRate = rememberTextFieldState(),
        maxHeartRate = rememberTextFieldState(),
        restingHeartRate = rememberTextFieldState(),
        functionalThresholdPower = rememberTextFieldState(),
        heartRateModel = rememberSaveable { mutableStateOf("") },
        powerModel = rememberSaveable { mutableStateOf("") },
        visibility = rememberSaveable { mutableStateOf(Visibility.SELF) },
        filled = rememberSaveable { mutableStateOf(false) },
    )
