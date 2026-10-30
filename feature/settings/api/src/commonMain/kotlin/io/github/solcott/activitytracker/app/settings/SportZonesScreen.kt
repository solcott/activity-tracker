package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.training.EffectiveTraining
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.data.training.ThresholdInput
import io.github.solcott.activitytracker.data.training.Zone
import io.github.solcott.activitytracker.data.training.ZoneKind
import io.github.solcott.activitytracker.data.training.ZoneModel
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.hasLoaded
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

/** One sport's own thresholds and zone models, overriding the defaults on [ZonesScreen]. [sport] is a `Sport` value. */
@CircuitSerializable(SessionScope::class) data class SportZonesScreen(val sport: String) : Screen

@Immutable
data class SportZonesState(
    val sport: String,
    /** The zone kinds this sport offers; only these sections show. */
    val kinds: ImmutableSet<ZoneKind>,
    /** Lactate threshold heart rate, bpm as typed; blank uses the default. */
    val thresholdHeartRate: TextFieldState,
    /** Bpm as typed; blank uses the default. */
    val maxHeartRate: TextFieldState,
    /** Threshold power, watts as typed; blank uses the default. */
    val functionalThresholdPower: TextFieldState,
    /** Threshold pace as `m:ss` per [paceUnit]. */
    val thresholdPace: TextFieldState,
    val paceUnit: PaceUnit,
    /** The defaults, shown in place of blank fields. */
    val defaults: EffectiveTraining,
    val heartRateModel: ZoneModel,
    val heartRateModels: ImmutableList<ZoneModel>,
    /** [heartRateModel]'s zones, or null while a threshold it needs is missing. */
    val heartRateZones: ImmutableList<Zone>?,
    val powerModel: ZoneModel,
    val powerModels: ImmutableList<ZoneModel>,
    val powerZones: ImmutableList<Zone>?,
    val speedModel: ZoneModel,
    val speedModels: ImmutableList<ZoneModel>,
    /** [speedModel]'s zones in mm/s, slowest first, or null while threshold pace is missing. */
    val speedZones: ImmutableList<Zone>?,
    /** True when the sport already has its own thresholds, so it can be removed. */
    val saved: Boolean,
    /** The saved values the form is filled from: loading or failed until it is, then loaded. */
    val content: ContentState<Unit>,
    val saving: Boolean,
    /** Why a save failed. */
    val error: UiError?,
    val eventSink: (SportZonesEvent) -> Unit,
) : CircuitUiState {
    /** False until the form is filled from the saved values, so a save can't blank values the athlete never saw. */
    val loaded: Boolean
        get() = content.hasLoaded

    val valid: Boolean
        get() =
            listOf(thresholdHeartRate, maxHeartRate).all { ThresholdInput.isValidHeartRate(it.text.toString()) } &&
                ThresholdInput.isValidPower(functionalThresholdPower.text.toString()) &&
                ThresholdInput.isValidPace(thresholdPace.text.toString(), paceUnit)
}

sealed interface SportZonesEvent {
    data object Back : SportZonesEvent

    /** Loads the saved values again after a failed first load. */
    data object Retry : SportZonesEvent

    data class HeartRateModelChanged(val token: String) : SportZonesEvent

    data class PowerModelChanged(val token: String) : SportZonesEvent

    data class SpeedModelChanged(val token: String) : SportZonesEvent

    data object Save : SportZonesEvent

    /** Drop the sport's own thresholds, so it uses the defaults again. */
    data object Remove : SportZonesEvent
}
