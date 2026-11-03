package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.popUntil
import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.moderation.ModerationScreen
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityDefaults
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.moderation.ModerationRepository
import io.github.solcott.activitytracker.data.segments.SegmentPrefsRepository
import io.github.solcott.activitytracker.data.units.UnitsRepository

@CircuitInject(SettingsScreen::class, SessionScope::class)
@Composable
@Suppress("LongParameterList") // A summary's repository per row, plus units, moderation and sign-out.
fun SettingsPresenter(
    navigator: Navigator,
    unitsRepository: UnitsRepository,
    auth: AuthRepository,
    defaults: ActivityDefaults,
    privacy: PrivacyZones,
    moderation: ModerationRepository,
    segmentPrefs: SegmentPrefsRepository,
): SettingsState {
    // UnitsRepository updates optimistically, so the choice shows at once and reverts if the save fails.
    val units by unitsRepository.units.collectAsState()
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    val saving = flights.isRunning(SettingsEvent.UnitsChanged::class)
    // The rows' summaries, each left out while unread: a failed read leaves it out, and the page says why.
    val defaultVisibility by defaults.current.collectAsState()
    LaunchedEffect(Unit) { runSuspendCatching { defaults.load() } }
    val privacySettings by privacy.current.collectAsState()
    LaunchedEffect(Unit) { privacy.refresh() }
    val segments by segmentPrefs.current.collectAsState()
    LaunchedEffect(Unit) { runSuspendCatching { segmentPrefs.refresh() } }
    // Asked once a session; an AppView that can't say leaves the entry out.
    var canModerate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { canModerate = runSuspendCatching { moderation.canModerate() }.getOrDefault(false) }

    return SettingsState(
        units,
        saving,
        error,
        defaultActivityVisibility = defaultVisibility?.activity,
        privacyZoneCount = privacySettings?.zones?.size,
        hidesRouteEnds = (privacySettings?.hideEndsMeters ?: 0) > 0,
        segmentMatching = segments?.isOn,
        canModerate = canModerate,
    ) { event ->
        when (event) {
            // Settings' own back leaves it: on a wide window that also closes the page shown beside the list.
            SettingsEvent.Back -> {
                navigator.popUntil { it is SettingsScreen }
                navigator.pop()
            }

            is SettingsEvent.UnitsChanged -> {
                if (event.units != units && !saving) {
                    error = null
                    flights.launch(SettingsEvent.UnitsChanged::class) {
                        runSuspendCatching { unitsRepository.set(event.units) }
                            .onFailure { e -> error = UiError.of(UiError.Kind.SaveSettings, e) }
                    }
                }
            }

            is SettingsEvent.Open -> {
                navigator.openFromSettings(event.page)
            }

            SettingsEvent.OpenModeration -> {
                if (canModerate) navigator.openFromSettings(ModerationScreen)
            }

            // Dropping the session tears down the SessionGraph and returns to LoginScreen.
            SettingsEvent.SignOut -> {
                flights.launch(SettingsEvent.SignOut) { auth.signOut() }
            }
        }
    }
}

/**
 * Opens [screen] in place of whichever page Settings has open. On a phone Settings is on top, so nothing pops; on a wide window this swaps
 * the detail pane's page rather than stacking a second one on it.
 */
private fun Navigator.openFromSettings(screen: Screen) {
    popUntil { it is SettingsScreen }
    goTo(screen)
}
