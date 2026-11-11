package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.data.training.ThresholdInput
import io.github.solcott.activitytracker.data.training.Zone
import io.github.solcott.activitytracker.data.training.ZoneModel
import io.github.solcott.activitytracker.textfield.TextFieldState
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.hasLoaded
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

/** Edits the signed-in athlete's default training thresholds and the heart rate and power zones derived from them. */
@CircuitSerializable(SessionScope::class) data object ZonesScreen : SettingsPage

@Immutable
data class ZonesState(
    /** Lactate threshold heart rate, bpm as typed. */
    val thresholdHeartRate: TextFieldState,
    /** Bpm as typed. */
    val maxHeartRate: TextFieldState,
    /** Bpm as typed. */
    val restingHeartRate: TextFieldState,
    /** Threshold power (FTP), watts as typed. */
    val functionalThresholdPower: TextFieldState,
    val heartRateModel: ZoneModel,
    val heartRateModels: ImmutableList<ZoneModel>,
    /** [heartRateModel]'s zones, or null while a threshold it needs is missing. */
    val heartRateZones: ImmutableList<Zone>?,
    val powerModel: ZoneModel,
    val powerModels: ImmutableList<ZoneModel>,
    /** [powerModel]'s zones, or null while FTP is missing. */
    val powerZones: ImmutableList<Zone>?,
    /** The `Threshold` values imported activities may update from the recording device's settings. */
    val autoUpdate: ImmutableSet<String>,
    /** The saved values the form is filled from: loading or failed until it is, then loaded. */
    val content: ContentState<Unit>,
    val saving: Boolean,
    /** Why a save failed. */
    val error: UiError?,
    val eventSink: (ZonesEvent) -> Unit,
) : CircuitUiState {
    /** False until the form is filled from the saved values, so a save can't blank values the athlete never saw. */
    val loaded: Boolean
        get() = content.hasLoaded

    val valid: Boolean
        get() =
            listOf(thresholdHeartRate, maxHeartRate, restingHeartRate).all { ThresholdInput.isValidHeartRate(it.text.toString()) } &&
                ThresholdInput.isValidPower(functionalThresholdPower.text.toString())

    /** Whether Save can be tapped: the saved values are in, every field holds a value it takes, and no save is running. */
    val canSave: Boolean
        get() = loaded && valid && !saving

    /** Whether [text] is blank or a value [threshold] takes. A UI asks with the text it shows, which can be ahead of the state's. */
    fun isValid(threshold: ZoneThreshold, text: String): Boolean = threshold.isValid(text, PaceUnit.PerKilometer)

    /** Whether imported activities may update [threshold]. */
    fun autoUpdates(threshold: ZoneThreshold): Boolean = threshold.token in autoUpdate
}

sealed interface ZonesEvent {
    data object Back : ZonesEvent

    /** Loads the saved values again after a failed first load. */
    data object Retry : ZonesEvent

    data class HeartRateModelChanged(val token: String) : ZonesEvent

    data class PowerModelChanged(val token: String) : ZonesEvent

    /** Whether imports may update [threshold], a `Threshold` value. */
    data class AutoUpdateChanged(val threshold: String, val enabled: Boolean) : ZonesEvent

    data object Save : ZonesEvent
}
