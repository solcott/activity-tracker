package io.github.solcott.activitytracker.app.record

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Asks the signed-in app to show the recording screen, from outside the composition: the platform's recording notification was tapped.
 * `App` navigates to [RecordScreen] on each request; one made before it collects waits for it.
 */
@SingleIn(AppScope::class)
@Inject
class RecordScreenRequests {
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
