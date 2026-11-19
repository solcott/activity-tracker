package io.github.solcott.activitytracker.appview.ops

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Runs the AppView's background workers (dev/worker-telemetry), each reporting to [registry]. A worker whose run ends with an error, or a
 * loop or stream whose run ends at all, is run again after a pause doubling from [FIRST_PAUSE] to [MAX_PAUSE], reset once a run has lasted
 * [HEALTHY_RUN]. Workers run under one [SupervisorJob], a child of [parent]'s, so a crash stops no other worker, and cancelling [parent]
 * (shutdown) stops them all without a restart. A fatal [VirtualMachineError] isn't caught.
 */
class WorkerSupervisor(
    parent: CoroutineScope,
    val registry: WorkerRegistry,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val log = LoggerFactory.getLogger(WorkerSupervisor::class.java)

    /** The scope workers run in, for the coroutines a worker starts of its own (the space syncer's per-space syncs). */
    val scope: CoroutineScope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))

    /** A polling loop, which never returns. */
    fun loop(name: String, body: suspend (WorkerHandle) -> Unit): Job = launch(name, WorkerKind.LOOP, body)

    /** A long-lived stream (Jetstream, a labeler), which never returns. */
    fun stream(name: String, body: suspend (WorkerHandle) -> Unit): Job = launch(name, WorkerKind.STREAM, body)

    /** A task that runs once at start; it's run again only if it fails. */
    fun oneOff(name: String, body: suspend (WorkerHandle) -> Unit): Job = launch(name, WorkerKind.ONE_OFF, body)

    private fun launch(name: String, kind: WorkerKind, body: suspend (WorkerHandle) -> Unit): Job {
        val handle = registry.handle(name, kind)
        return scope.launch(CoroutineName(name)) {
            var crashes = 0
            try {
                while (true) {
                    registry.started(name)
                    val mark = timeSource.markNow()
                    val failure = runOnce(kind, handle, body) ?: return@launch registry.ended(name, WorkerState.FINISHED)
                    if (mark.elapsedNow() >= HEALTHY_RUN) crashes = 0
                    val pause = pauseAfter(crashes++)
                    log.error("Worker {} crashed; restarting in {}", name, pause, failure)
                    registry.crashed(name, failure)
                    delay(pause)
                }
            } catch (e: CancellationException) {
                registry.ended(name, WorkerState.STOPPED)
                throw e
            }
        }
    }

    /** One run of [body]: null when a one-off completed, else why the run ended. */
    private suspend fun runOnce(kind: WorkerKind, handle: WorkerHandle, body: suspend (WorkerHandle) -> Unit): Throwable? =
        try {
            body(handle)
            if (kind == WorkerKind.ONE_OFF) null else IllegalStateException("The worker returned")
        } catch (e: CancellationException) {
            // Shutdown; a cancellation that escaped some inner timeout while this worker is still active is a crash like any other.
            if (!currentCoroutineContext().isActive) throw e
            e
        } catch (e: VirtualMachineError) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            e
        }

    companion object {
        val FIRST_PAUSE = 1.seconds
        val MAX_PAUSE = 5.minutes
        val HEALTHY_RUN = 10.minutes

        /** The pause before restarting a worker that has crashed [crashes] times before in a row. */
        fun pauseAfter(crashes: Int): Duration = minOf(FIRST_PAUSE * (1L shl minOf(crashes, MAX_DOUBLINGS)).toDouble(), MAX_PAUSE)

        private const val MAX_DOUBLINGS = 20
    }
}
