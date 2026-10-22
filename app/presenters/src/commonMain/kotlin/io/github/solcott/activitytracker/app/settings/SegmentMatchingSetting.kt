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
import io.github.solcott.activitytracker.data.segments.LeaderboardBracketsRepository
import io.github.solcott.activitytracker.data.segments.SegmentPrefsRepository

/**
 * The segment matching toggle in Settings, and while matching is on whether the athlete shows on leaderboards and on age, weight and gender
 * boards: read again on opening (another device may have changed them), and saved as soon as one is flipped. Each shows the saved setting,
 * so a failed save leaves it as it was. Each matching save sets the repository's `saved`, after which
 * `ActivityRepository.repairRoutesOnSave` (run for the session) writes or deletes the segment tracks.
 */
@Stable
class SegmentMatchingSetting
@Suppress("LongParameterList") // Each setting's repository and saved state, and the flights and error they share.
internal constructor(
    private val prefs: SegmentPrefsRepository,
    private val brackets: LeaderboardBracketsRepository,
    private val current: State<Boolean?>,
    private val hidden: State<Boolean?>,
    private val bracketsOn: State<Boolean?>,
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

    /** Whether the athlete shows on bracket boards; null while it's unknown. */
    val bracketBoards: Boolean?
        get() = bracketsOn.value

    val canUseBracketBoards: Boolean
        get() = brackets.canUse

    val savingBracketBoards: Boolean
        get() = flights.isRunning(BRACKETS_KEY)

    /** Writes the athlete's brackets, or deletes them; offered only while matching is on. */
    private fun setBracketBoards(on: Boolean) {
        if (on == bracketBoards || matching != true) return
        onError(null)
        flights.launch(BRACKETS_KEY) {
            runSuspendCatching { brackets.save(on) }.onFailure { e -> onError(UiError.of(UiError.Kind.SaveSettings, e)) }
        }
    }

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
            is SettingsEvent.BracketBoardsChanged -> setBracketBoards(event.on)
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
        val BRACKETS_KEY = SettingsEvent.BracketBoardsChanged::class
    }
}

@Composable
internal fun rememberSegmentMatchingSetting(
    prefs: SegmentPrefsRepository,
    brackets: LeaderboardBracketsRepository,
    flights: SingleFlight,
    onError: (UiError?) -> Unit,
): SegmentMatchingSetting {
    val current = remember(prefs) { prefs.current }.collectAsState()
    val matching = remember(current) { derivedStateOf { current.value?.isOn } }
    val hidden = remember(prefs) { prefs.hiddenFromLeaderboards }.collectAsState()
    val bracketsOn = remember(brackets) { brackets.current }.collectAsState()
    val setting =
        remember(prefs, brackets, matching, hidden, bracketsOn) {
            SegmentMatchingSetting(prefs, brackets, matching, hidden, bracketsOn, flights, onError)
        }
    LaunchedEffect(prefs) { runSuspendCatching { prefs.refresh() } }
    LaunchedEffect(brackets) { if (brackets.canUse) runSuspendCatching { brackets.reload() } }
    return setting
}
