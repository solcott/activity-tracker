package io.github.solcott.activitytracker.app.settings

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Asks the signed-in app to show the Account page, from outside the composition: the platform's "export ready" notification was tapped
 * (auth/data-export). `App` navigates to [AccountScreen] on each request; one made before it collects waits for it.
 */
@SingleIn(AppScope::class)
@Inject
class AccountScreenRequests {
    val requests: SharedFlow<Unit>
        field = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun request() {
        requests.tryEmit(Unit)
    }

    /** Marks the pending request handled, so a new session doesn't replay it. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun handled() {
        requests.resetReplayCache()
    }
}
