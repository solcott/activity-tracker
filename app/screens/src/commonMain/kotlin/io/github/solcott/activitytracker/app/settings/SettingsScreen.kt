package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitSystem

/** The signed-in athlete's preferences, and sign-out. */
@CircuitSerializable(SessionScope::class) data object SettingsScreen : Screen

@Immutable
data class SettingsState(
    val units: UnitSystem,
    val saving: Boolean,
    val error: UiError?,
    /** Who new activities and each detail category are shown to unless the athlete chooses otherwise; null while they load. */
    val defaults: VisibilityFields? = null,
    val savingDefaults: Boolean = false,
    /** How many privacy zones the athlete keeps; null until read. */
    val privacyZoneCount: Int? = null,
    /** Whether the start and end of every route are hidden. */
    val hidesRouteEnds: Boolean = false,
    /** Whether segment matching is on; null while it's unknown (not read yet, or unreadable). */
    val segmentMatching: Boolean? = null,
    /** Whether this session can use segment matching: false for one signed in before its `space:` grant listed it. */
    val canUseSegments: Boolean = true,
    val savingSegments: Boolean = false,
    val eventSink: (SettingsEvent) -> Unit,
) : CircuitUiState

sealed interface SettingsEvent {
    data object Back : SettingsEvent

    data class UnitsChanged(val units: UnitSystem) : SettingsEvent

    data object OpenZones : SettingsEvent

    data object OpenPrivacyZones : SettingsEvent

    data object OpenBlocked : SettingsEvent

    data object OpenMuted : SettingsEvent

    /** Turns segment matching [on] or off: a copy of each route without the privacy zones is shared with the AppView while it's on. */
    data class SegmentMatchingChanged(val on: Boolean) : SettingsEvent

    data object SignOut : SettingsEvent
}
