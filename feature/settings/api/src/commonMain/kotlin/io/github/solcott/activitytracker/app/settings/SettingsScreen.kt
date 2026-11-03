package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitSystem

/**
 * The signed-in athlete's preferences, and sign-out: a grouped list whose rows open Settings' pages (ui/settings-layout). On a wide window
 * the navigation shell lays it out beside the page it opened.
 */
@CircuitSerializable(SessionScope::class) data object SettingsScreen : Screen

@Immutable
data class SettingsState(
    val units: UnitSystem,
    val saving: Boolean,
    val error: UiError?,
    /** Who new activities are shown to unless the athlete chooses otherwise, a `Visibility` value; null until read. */
    val defaultActivityVisibility: String? = null,
    /** How many privacy zones the athlete keeps; null until read. */
    val privacyZoneCount: Int? = null,
    /** Whether the start and end of every route are hidden. */
    val hidesRouteEnds: Boolean = false,
    /** Whether segment matching is on; null while it's unknown (not read yet, or unreadable). */
    val segmentMatching: Boolean? = null,
    /** The athlete is a moderator or admin on the app's labeler team: desktop and web offer Moderation. */
    val canModerate: Boolean = false,
    val eventSink: (SettingsEvent) -> Unit,
) : CircuitUiState

sealed interface SettingsEvent {
    data object Back : SettingsEvent

    data class UnitsChanged(val units: UnitSystem) : SettingsEvent

    /** A row: opens [page] in place of whichever page Settings has open. */
    data class Open(val page: SettingsPage) : SettingsEvent

    data object OpenModeration : SettingsEvent

    data object SignOut : SettingsEvent
}
