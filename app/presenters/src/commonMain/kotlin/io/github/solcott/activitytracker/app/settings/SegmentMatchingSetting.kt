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
 * The segment matching toggle in Settings: read again on opening (another device may have changed it), and saved as soon as it's flipped.
 * It shows the saved setting, so a failed save leaves it as it was. Each save sets the repository's `saved`, after which
 * `ActivityRepository.repairRoutesOnSave` (run for the session) writes or deletes the segment tracks.
 */
@Stable
class SegmentMatchingSetting
internal constructor(
    private val prefs: SegmentPrefsRepository,
    private val current: State<Boolean?>,
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

    fun set(on: Boolean) {
        if (on == matching) return
        onError(null)
        flights.launch(KEY) { runSuspendCatching { prefs.save(on) }.onFailure { e -> onError(UiError.of(UiError.Kind.SaveSettings, e)) } }
    }

    private companion object {
        val KEY = SettingsEvent.SegmentMatchingChanged::class
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
    val setting = remember(prefs, matching) { SegmentMatchingSetting(prefs, matching, flights, onError) }
    LaunchedEffect(prefs) { runSuspendCatching { prefs.refresh() } }
    return setting
}
