package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.retained.rememberRetained
import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.segments.OwnGroups
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.GroupPartner
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Leaving and rejoining the group on the owner's own activity (feed/group-activities): [state] is null on someone else's, or while it can't
 * be offered. The choice shows at once and stays over the stored one while its write is queued, since the stored track reads as unknown
 * until then.
 */
internal class OwnGroup(val state: OwnGroupState?, private val set: (Boolean) -> Unit) {
    fun on(event: ActivityDetailEvent) {
        when (event) {
            ActivityDetailEvent.LeaveGroup -> set(true)
            ActivityDetailEvent.RejoinGroup -> set(false)
            else -> Unit
        }
    }

    /** [partners] as the page lists them: none once the owner left here, before the AppView catches up. */
    fun shown(partners: List<GroupPartner>?): ImmutableList<GroupPartner> =
        if (state?.left == true) persistentListOf() else partners.orEmpty().toImmutableList()
}

@Composable
internal fun rememberOwnGroup(activity: AtUri?, loadKey: Int, groups: OwnGroups, flights: SingleFlight): OwnGroup {
    // As stored, read for the owner's own activity; null when it can't be offered.
    var stored by rememberRetained(activity) { mutableStateOf<Boolean?>(null) }
    // As chosen here, over the stored value until the page is reloaded.
    var chosen by rememberRetained(activity) { mutableStateOf<Boolean?>(null) }
    var error by rememberRetained(activity) { mutableStateOf<UiError?>(null) }
    LaunchedEffect(activity, loadKey) { stored = activity?.let { runSuspendCatching { groups.left(it) }.getOrNull() } }
    val key = ActivityDetailEvent.LeaveGroup
    val left = chosen ?: stored
    if (activity == null || left == null) return OwnGroup(null) {}
    return OwnGroup(OwnGroupState(left, busy = flights.isRunning(key), error = error)) { leave ->
        if (leave != left) {
            flights.launch(key) {
                error = null
                runSuspendCatching { groups.setLeft(activity, leave) }
                    .onSuccess { chosen = leave }
                    .onFailure { e -> error = uiErrorOf(UiError.Kind.ChangeGroup, e) }
            }
        }
    }
}
