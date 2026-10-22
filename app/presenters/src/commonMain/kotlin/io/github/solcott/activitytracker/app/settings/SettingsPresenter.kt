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
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.moderation.ModerationScreen
import io.github.solcott.activitytracker.app.profile.BlockListKind
import io.github.solcott.activitytracker.app.profile.BlockListScreen
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityDefaults
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.moderation.ModerationRepository
import io.github.solcott.activitytracker.data.segments.LeaderboardBracketsRepository
import io.github.solcott.activitytracker.data.segments.SegmentPrefsRepository
import io.github.solcott.activitytracker.data.units.UnitsRepository

@CircuitInject(SettingsScreen::class, SessionScope::class)
@Composable
fun SettingsPresenter(
    navigator: Navigator,
    unitsRepository: UnitsRepository,
    auth: AuthRepository,
    defaults: ActivityDefaults,
    privacy: PrivacyZones,
    moderation: ModerationRepository,
    segmentPrefs: SegmentPrefsRepository,
    leaderboardBrackets: LeaderboardBracketsRepository,
): SettingsState {
    // UnitsRepository updates optimistically, so the choice shows at once and reverts if the save fails.
    val units by unitsRepository.units.collectAsState()
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    val saving = flights.isRunning(SettingsEvent.UnitsChanged::class)
    val defaultVisibility = rememberDefaultVisibilitySettings(defaults, flights) { error = it }
    val privacySettings by privacy.current.collectAsState()
    // Only the summary line: a failed read leaves it out, and the zones screen says why.
    LaunchedEffect(Unit) { privacy.refresh() }
    // Asked once a session; an AppView that can't say leaves the entry out.
    var canModerate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { canModerate = runSuspendCatching { moderation.canModerate() }.getOrDefault(false) }
    val segments = rememberSegmentMatchingSetting(segmentPrefs, leaderboardBrackets, flights) { error = it }

    return SettingsState(
        units,
        saving,
        error,
        defaultVisibility.fields,
        defaultVisibility.saving,
        privacyZoneCount = privacySettings?.zones?.size,
        hidesRouteEnds = (privacySettings?.hideEndsMeters ?: 0) > 0,
        canModerate = canModerate,
        segmentMatching = segments.matching,
        canUseSegments = segments.canUse,
        savingSegments = segments.saving,
        showOnLeaderboards = segments.shownOnLeaderboards,
        savingLeaderboards = segments.savingLeaderboards,
        bracketBoards = segments.bracketBoards,
        canUseBracketBoards = segments.canUseBracketBoards,
        savingBracketBoards = segments.savingBracketBoards,
    ) { event ->
        when (event) {
            SettingsEvent.Back -> {
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

            SettingsEvent.OpenZones -> {
                navigator.goTo(TrainingZonesScreen)
            }

            SettingsEvent.OpenPrivacyZones -> {
                navigator.goTo(PrivacyZonesScreen)
            }

            SettingsEvent.OpenBlocked -> {
                navigator.goTo(BlockListScreen(BlockListKind.Blocked))
            }

            SettingsEvent.OpenMuted -> {
                navigator.goTo(BlockListScreen(BlockListKind.Muted))
            }

            SettingsEvent.OpenModeration -> {
                if (canModerate) navigator.goTo(ModerationScreen)
            }

            is SettingsEvent.SegmentSettingEvent -> {
                segments.onEvent(event)
            }

            // Dropping the session tears down the SessionGraph and returns to LoginScreen.
            SettingsEvent.SignOut -> {
                flights.launch(SettingsEvent.SignOut) { auth.signOut() }
            }
        }
    }
}
