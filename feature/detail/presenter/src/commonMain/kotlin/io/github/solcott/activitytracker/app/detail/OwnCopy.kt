package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.lexicon.app.ActivityView

/**
 * Leaving the group on the owner's own copy of another athlete's activity, accepted from an invite (feed/activity-invites): [leave]
 * rewrites the copy without `copyOf`, then reloads the page.
 */
internal class OwnCopy(val isOwnCopy: Boolean, val leaving: Boolean, val error: UiError?, val leave: () -> Unit) {
    /** Handles a group event: leaving the copy's group here, leaving or rejoining a matched group through [group]. */
    fun on(event: ActivityDetailEvent, group: OwnGroup) {
        if (event == ActivityDetailEvent.LeaveCopyGroup) leave() else group.on(event)
    }
}

/** [OwnCopy] for [own], the activity when it's the athlete's own; [onLeft] reloads the page once the copy is rewritten. */
@Composable
internal fun rememberOwnCopy(own: ActivityView?, manager: ActivityManager, flights: SingleFlight, onLeft: () -> Unit): OwnCopy {
    var error by remember(own?.uri) { mutableStateOf<UiError?>(null) }
    return OwnCopy(
        isOwnCopy = own?.record?.copyOf != null,
        leaving = flights.isRunning(ActivityDetailEvent.LeaveCopyGroup),
        error = error,
    ) {
        if (own != null)
            flights.launch(ActivityDetailEvent.LeaveCopyGroup) {
                error = null
                runSuspendCatching { manager.leaveCopyGroup(own) }
                    .onSuccess { onLeft() }
                    .onFailure { error = uiErrorOf(UiError.Kind.SaveActivity, it) }
            }
    }
}
