package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs a presenter's event-driven work at most once at a time per key, so a double tap can't save, follow or like twice. Presenters launch
 * through this, never a bare `rememberCoroutineScope()` (detekt's `UseSingleFlight` enforces that).
 *
 * The key names the action: the event itself (`XEvent.Save`), or what it acts on (an activity's URI for its like). [isRunning] is snapshot
 * state, so a presenter can show it as its `saving` or pending flag.
 */
@Stable
class SingleFlight internal constructor(private val scope: CoroutineScope) {
    private val running = mutableStateMapOf<Any, Job>()

    fun isRunning(key: Any): Boolean = key in running

    /** Starts [block] unless work under [key] is already running, in which case this does nothing and returns null. */
    fun launch(key: Any, block: suspend CoroutineScope.() -> Unit): Job? = if (key in running) null else start(key, block)

    /** Cancels any work running under [key] and starts [block] in its place, for when only the latest request matters (a new file). */
    fun launchLatest(key: Any, block: suspend CoroutineScope.() -> Unit): Job {
        running[key]?.cancel()
        return start(key, block)
    }

    private fun start(key: Any, block: suspend CoroutineScope.() -> Unit): Job {
        // Registered before it can run, and released however it ends, including a cancel before it was ever dispatched.
        val job = scope.launch(start = CoroutineStart.LAZY, block = block)
        running[key] = job
        job.invokeOnCompletion { if (running[key] === job) running.remove(key) }
        job.start()
        return job
    }
}

/** A [SingleFlight] whose work is cancelled when the presenter leaves the composition. */
@Composable
fun rememberSingleFlight(): SingleFlight {
    val scope = rememberCoroutineScope()
    return remember(scope) { SingleFlight(scope) }
}
