package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList

/** The places the signed-in athlete hides from their routes, and how much of each route's start and end. */
@CircuitSerializable(SessionScope::class) data object PrivacyZonesScreen : Screen

@Immutable
data class PrivacyZonesState(
    /** The zones as first read: loading, or failed when they can't be read (then nothing can change), until they are. */
    val content: ContentState<Unit>,
    /** The zones as saved; null until [content] has loaded. */
    val zones: ImmutableList<PrivacyZone>?,
    /** Meters hidden from every route's start and end; 0 is off. */
    val hideEndsMeters: Int,
    /** The radii and hidden start/end distances to choose from, in meters. */
    val choices: ImmutableList<Int>,
    /** Whether zones can be changed: loaded, and not [needsSignIn]. */
    val canEdit: Boolean,
    val saving: Boolean,
    /** A phone: zones can be added at the current location. Desktop and web add them from an activity only. */
    val canUseLocation: Boolean,
    val hasLocationPermission: Boolean,
    /** Finding the current location for a new zone. */
    val locating: Boolean,
    /** The label for a zone added at the current location. */
    val label: TextFieldState,
    /** A session signed in before its grant covered the zones: they show, and can't change until the athlete signs in again. */
    val needsSignIn: Boolean = false,
    /** Why a change failed. */
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
