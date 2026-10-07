package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.training.ThresholdInput
import io.github.solcott.activitytracker.data.training.Zone
import io.github.solcott.activitytracker.data.training.ZoneModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

/** The signed-in athlete's training thresholds, and the heart rate and power zones derived from them. */
@CircuitSerializable(SessionScope::class) data object ZonesScreen : Screen

@Immutable
data class ZonesState(
    /** Lactate threshold heart rate, bpm as typed. */
    val thresholdHeartRate: TextFieldState,
    /** Bpm as typed. */
    val maxHeartRate: TextFieldState,
    /** Bpm as typed. */
    val restingHeartRate: TextFieldState,
    /** Cycling FTP, watts as typed. */
    val functionalThresholdPower: TextFieldState,
    val heartRateModel: ZoneModel,
    val heartRateModels: ImmutableList<ZoneModel>,
    /** [heartRateModel]'s zones, or null while a threshold it needs is missing. */
    val heartRateZones: ImmutableList<Zone>?,
    val powerModel: ZoneModel,
    val powerModels: ImmutableList<ZoneModel>,
    /** [powerModel]'s zones, or null while FTP is missing. */
    val powerZones: ImmutableList<Zone>?,
    /** A `Visibility` value for all the thresholds together. */
    val visibility: String,
    /** The `Threshold` values imported activities may update from the recording device's settings. */
    val autoUpdate: ImmutableSet<String>,
    /** False until the saved values have been read, so a save can't blank values the athlete never saw. */
    val loaded: Boolean,
    val saving: Boolean,
    val error: UiError?,
    val eventSink: (ZonesEvent) -> Unit,
) : CircuitUiState {
    val valid: Boolean
        get() =
            listOf(thresholdHeartRate, maxHeartRate, restingHeartRate).all { ThresholdInput.isValidHeartRate(it.text.toString()) } &&
                ThresholdInput.isValidPower(functionalThresholdPower.text.toString())
}

sealed interface ZonesEvent {
    data object Back : ZonesEvent

    data class HeartRateModelChanged(val token: String) : ZonesEvent

    data class PowerModelChanged(val token: String) : ZonesEvent

    data class VisibilityChanged(val visibility: String) : ZonesEvent

    /** Whether imports may update [threshold], a `Threshold` value. */
    data class AutoUpdateChanged(val threshold: String, val enabled: Boolean) : ZonesEvent

    data object Save : ZonesEvent
}
