package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.formContent
import io.github.solcott.activitytracker.app.rememberFormSeed
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.OwnProfile
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.profile.updateTraining
import io.github.solcott.activitytracker.data.training.ThresholdInput
import io.github.solcott.activitytracker.data.training.ThresholdUpdates
import io.github.solcott.activitytracker.data.training.ZoneModels
import io.github.solcott.activitytracker.data.training.Zones
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

@CircuitInject(ZonesScreen::class, SessionScope::class)
@Composable
fun ZonesPresenter(navigator: Navigator, profiles: ProfileRepository, clock: Clock): ZonesState {
    val form = rememberForm()
    var loadKey by remember { mutableIntStateOf(0) }
    val loaded = produceRetainedContentState<OwnProfile?>(null, loadKey) { profiles.ownProfileOutcome() }
    // Why a save failed; never a loading failure, which is [loaded]'s.
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    val saving = flights.isRunning(ZonesEvent.Save)

    // As saved when the form was filled, for each value's setAt.
    val saved = rememberFormSeed(loaded, form::fillOnce)?.let { it.details.training ?: TrainingDetails() }

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
        autoUpdate = form.autoUpdate.value.toImmutableSet(),
        content = loaded.formContent(seeded = saved != null),
        saving = saving,
        error = error,
    ) { event ->
        when (event) {
            ZonesEvent.Back -> {
                navigator.pop()
            }

            ZonesEvent.Retry -> {
                loadKey++
            }

            is ZonesEvent.HeartRateModelChanged -> {
                form.heartRateModel.value = event.token
            }

            is ZonesEvent.PowerModelChanged -> {
                form.powerModel.value = event.token
            }

            is ZonesEvent.AutoUpdateChanged -> {
                val others = form.autoUpdate.value - event.threshold
                form.autoUpdate.value = if (event.enabled) others + event.threshold else others
            }

            ZonesEvent.Save -> {
                val before = saved
                if (before != null && !saving) {
                    error = null
                    val edited = form.training().settingTimes(before, clock.now())
                    flights.launch(ZonesEvent.Save) {
                        // The sports as saved now: one may have been edited on its own screen since this one loaded.
                        runSuspendCatching { profiles.updateTraining { current -> edited.copy(sports = current.sports) } }
                            .onSuccess { navigator.pop() }
                            .onFailure { error = uiErrorOf(UiError.Kind.SaveZones, it) }
                    }
                }
            }
        }
    }
}

/** The thresholds as typed and the picked models, saved across configuration changes. A model is a token, or empty for the default. */
@Suppress("LongParameterList") // One field per form input.
private class Form(
    val thresholdHeartRate: TextFieldState,
    val maxHeartRate: TextFieldState,
    val restingHeartRate: TextFieldState,
    val functionalThresholdPower: TextFieldState,
    val heartRateModel: MutableState<String>,
    val powerModel: MutableState<String>,
    /** The `Threshold` values imports may update. A list, so it saves in a Bundle. */
    val autoUpdate: MutableState<List<String>>,
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
        autoUpdate.value = saved.autoUpdate.distinct()
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
            autoUpdate = Threshold.all.filter { it in autoUpdate.value } + autoUpdate.value.filterNot { it in Threshold.all },
        )
}

/** This with the times each threshold was set carried over from [before], and [now] for each one the athlete changed. */
private fun TrainingDetails.settingTimes(before: TrainingDetails, now: Instant): TrainingDetails =
    copy(
        setAt =
            Threshold.all
                .filter { ThresholdUpdates.value(this, it) != ThresholdUpdates.value(before, it) }
                .fold(before.setAt) { setAt, threshold -> ThresholdUpdates.markSet(setAt, threshold, now) }
    )

@Composable
private fun rememberForm(): Form =
    Form(
        thresholdHeartRate = rememberTextFieldState(),
        maxHeartRate = rememberTextFieldState(),
        restingHeartRate = rememberTextFieldState(),
        functionalThresholdPower = rememberTextFieldState(),
        heartRateModel = rememberSaveable { mutableStateOf("") },
        powerModel = rememberSaveable { mutableStateOf("") },
        autoUpdate = rememberSaveable { mutableStateOf(emptyList()) },
        filled = rememberSaveable { mutableStateOf(false) },
    )
