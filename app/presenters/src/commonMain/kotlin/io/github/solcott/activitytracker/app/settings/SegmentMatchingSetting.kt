package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.segments.SegmentPrefsRepository

/**
 * The segment matching toggle in Settings, and while matching is on whether the athlete shows on leaderboards: read again on opening
 * (another device may have changed them), and saved as soon as one is flipped. Each shows the saved setting, so a failed save leaves it as
 * it was. Each matching save sets the repository's `saved`, after which `ActivityRepository.repairRoutesOnSave` (run for the session)
 * writes or deletes the segment tracks.
 */
@Stable
class SegmentMatchingSetting
internal constructor(
    private val prefs: SegmentPrefsRepository,
    private val current: State<Boolean?>,
    private val hidden: State<Boolean?>,
    private val flights: SingleFlight,
    private val onError: (UiError?) -> Unit,
) {
    /** Whether matching is on; null while it's unknown (not read yet, or unreadable). */
    val matching: Boolean?
        get() = current.value

    val canUse: Boolean
        get() = prefs.canUse

    val saving: Boolean
        get() = flights.isRunning(KEY)

    /** Whether the athlete shows on segment leaderboards; null while it's unknown. */
    val shownOnLeaderboards: Boolean?
        get() = hidden.value?.not()

    val savingLeaderboards: Boolean
        get() = flights.isRunning(LEADERBOARDS_KEY)

    /** Shows the athlete on leaderboards, or hides them from every one; offered only while matching is on. */
    private fun setShownOnLeaderboards(shown: Boolean) {
        if (shown == shownOnLeaderboards || matching != true) return
        onError(null)
        flights.launch(LEADERBOARDS_KEY) {
            runSuspendCatching { prefs.saveLeaderboards(hidden = !shown) }
                .onFailure { e -> onError(UiError.of(UiError.Kind.SaveSettings, e)) }
        }
    }

    fun onEvent(event: SettingsEvent.SegmentSettingEvent) {
        when (event) {
            is SettingsEvent.SegmentMatchingChanged -> set(event.on)
            is SettingsEvent.LeaderboardsChanged -> setShownOnLeaderboards(event.shown)
        }
    }

    private fun set(on: Boolean) {
        if (on == matching) return
        onError(null)
        flights.launch(KEY) { runSuspendCatching { prefs.save(on) }.onFailure { e -> onError(UiError.of(UiError.Kind.SaveSettings, e)) } }
    }

    private companion object {
        val KEY = SettingsEvent.SegmentMatchingChanged::class
        val LEADERBOARDS_KEY = SettingsEvent.LeaderboardsChanged::class
    }
}

@Composable
internal fun rememberSegmentMatchingSetting(
    prefs: SegmentPrefsRepository,
    flights: SingleFlight,
    onError: (UiError?) -> Unit,
): SegmentMatchingSetting {
    val current = remember(prefs) { prefs.current }.collectAsState()
    val matching = remember(current) { derivedStateOf { current.value?.isOn } }
    val hidden = remember(prefs) { prefs.hiddenFromLeaderboards }.collectAsState()
    val setting = remember(prefs, matching, hidden) { SegmentMatchingSetting(prefs, matching, hidden, flights, onError) }
    LaunchedEffect(prefs) { runSuspendCatching { prefs.refresh() } }
    return setting
}
