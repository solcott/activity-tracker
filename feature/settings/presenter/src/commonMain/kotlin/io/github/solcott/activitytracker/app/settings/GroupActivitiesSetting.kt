package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.segments.GroupChoice
import io.github.solcott.activitytracker.data.segments.GroupPrefsRepository

/**
 * The group activities choice in Settings' Segments & groups page (feed/group-activities): read again on opening (another device may have
 * changed it), and saved as soon as one is picked. It shows the saved choice, so a failed save leaves it as it was. Each save sets the
 * repository's `saved`, after which `ActivityRepository.repairRoutesOnSave` (run for the session) writes or deletes the segment tracks.
 */
@Stable
class GroupActivitiesSetting
internal constructor(
    private val prefs: GroupPrefsRepository,
    private val current: State<GroupChoice?>,
    private val flights: SingleFlight,
    private val onError: (UiError?) -> Unit,
) {
    /** The choice; null while it's unknown (not read yet, or unreadable). */
    val choice: GroupChoice?
        get() = current.value

    val canUse: Boolean
        get() = prefs.canUse

    val saving: Boolean
        get() = flights.isRunning(KEY)

    fun set(choice: GroupChoice) {
        if (choice == this.choice || choice == GroupChoice.NEVER_ASKED || !canUse) return
        onError(null)
        flights.launch(KEY) {
            runSuspendCatching { prefs.save(choice) }.onFailure { e -> onError(uiErrorOf(UiError.Kind.SaveSettings, e)) }
        }
    }

    private companion object {
        val KEY = SegmentsAndGroupsEvent.GroupActivitiesChanged::class
    }
}

@Composable
internal fun rememberGroupActivitiesSetting(
    prefs: GroupPrefsRepository,
    flights: SingleFlight,
    onError: (UiError?) -> Unit,
): GroupActivitiesSetting {
    val current = remember(prefs) { prefs.current }.collectAsState()
    val setting = remember(prefs, current) { GroupActivitiesSetting(prefs, current, flights, onError) }
    LaunchedEffect(prefs) { runSuspendCatching { prefs.refresh() } }
    return setting
}
