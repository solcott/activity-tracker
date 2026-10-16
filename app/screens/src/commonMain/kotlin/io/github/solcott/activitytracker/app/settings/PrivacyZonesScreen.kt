package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import kotlinx.collections.immutable.ImmutableList

/** The places the signed-in athlete hides from their routes, and how much of each route's start and end. */
@CircuitSerializable(SessionScope::class) data object PrivacyZonesScreen : Screen

@Immutable
data class PrivacyZonesState(
    /** The zones as saved; null while they load or when they couldn't be read (then [error] says so and nothing can change). */
    val zones: ImmutableList<PrivacyZone>?,
    /** Meters hidden from every route's start and end; 0 is off. */
    val hideEndsMeters: Int,
    /** The radii and hidden start/end distances to choose from, in meters. */
    val choices: ImmutableList<Int>,
    /** Whether zones can be changed: loaded, and a session whose grant covers them. */
    val canEdit: Boolean,
    val saving: Boolean,
    /** A phone: zones can be added at the current location. Desktop and web add them from an activity only. */
    val canUseLocation: Boolean,
    val hasLocationPermission: Boolean,
    /** Finding the current location for a new zone. */
    val locating: Boolean,
    /** The label for a zone added at the current location. */
    val label: TextFieldState,
    val error: UiError?,
    val eventSink: (PrivacyZonesEvent) -> Unit,
) : CircuitUiState

sealed interface PrivacyZonesEvent {
    data object Back : PrivacyZonesEvent

    data object Retry : PrivacyZonesEvent

    /** The zone at [index] in [PrivacyZonesState.zones] gets [meters] of radius. */
    data class RadiusChanged(val index: Int, val meters: Int) : PrivacyZonesEvent

    data class Remove(val index: Int) : PrivacyZonesEvent

    /** Meters hidden from every route's start and end; 0 turns it off. */
    data class HideEndsChanged(val meters: Int) : PrivacyZonesEvent

    /** Adds a zone at the current location with [PrivacyZonesState.label]. */
    data object AddAtCurrentLocation : PrivacyZonesEvent

    data class PermissionResult(val granted: Boolean) : PrivacyZonesEvent
}
