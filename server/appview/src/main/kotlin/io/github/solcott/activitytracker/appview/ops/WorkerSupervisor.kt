package io.github.solcott.activitytracker.appview.ops

import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
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
    private val runners = ConcurrentHashMap<String, Runner>()

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
        val runner = Runner()
        runners[name] = runner
        return scope.launch(CoroutineName(name)) {
            var crashes = 0
            try {
                while (true) {
                    registry.started(name)
                    val mark = timeSource.markNow()
                    val run = runner.begin { async { runOnce(kind, handle, body) } }
                    val failure = run?.let { awaitRun(it) }
                    if (runner.restartRequested()) {
                        registry.restartedOnRequest(name, failure)
                        runner.awaitRestart()
                        continue
                    }
                    if (failure == null) return@launch registry.ended(name, WorkerState.FINISHED)
                    if (mark.elapsedNow() >= HEALTHY_RUN) crashes = 0
                    val pause = pauseAfter(crashes++)
                    log.error("Worker {} crashed; restarting in {}", name, pause, failure)
                    registry.crashed(name, failure)
                    runner.begin { launch { delay(pause) } }?.join()
                    if (runner.restartRequested()) {
                        // A restart during the crash pause ends the pause; the crash already counted, the restart counts too.
                        registry.restartedOnRequest(name)
                        runner.awaitRestart()
                    }
                }
            } catch (e: CancellationException) {
                registry.ended(name, WorkerState.STOPPED)
                throw e
            }
        }
    }

    /** How [run] ended: null when a one-off completed, else why; a run a restart cancelled ended with nothing to report. */
    private suspend fun awaitRun(run: Deferred<Throwable?>): Throwable? =
        try {
            run.await()
        } catch (e: CancellationException) {
            // Shutdown cancels this coroutine too; otherwise the run alone was cancelled, by a restart.
            if (!currentCoroutineContext().isActive) throw e
            null
        }

    /**
     * Restarts the loop or stream [name] on request (dev/worker-telemetry): ends its current run (or the pause after a crash), runs
     * [whileStopped] here once it has ended, then lets the next run start at once, with no pause and its crash count unchanged. Work it
     * launched in [scope] carries on. Null when it restarted, else why it was refused, leaving the worker as it was.
     */
    suspend fun restart(name: String, whileStopped: suspend () -> Unit = {}): RestartRefusal? {
        val status = registry[name]
        val runner = runners[name]
        val refusal =
            when {
                status == null -> RestartRefusal.UNKNOWN
                status.kind == WorkerKind.ONE_OFF -> RestartRefusal.ONE_OFF
                status.state in NOT_RESTARTABLE -> RestartRefusal.NOT_RUNNING
                runner == null -> RestartRefusal.UNKNOWN
                else -> null
            }
        val gate = CompletableDeferred<Unit>()
        val current = if (refusal == null) runner?.request(gate) else null
        if (current != null) {
            try {
                current.cancelAndJoin()
                whileStopped()
            } finally {
                gate.complete(Unit)
            }
        }
        return refusal ?: RestartRefusal.ALREADY_RESTARTING.takeIf { current == null }
    }

    /**
     * One worker's current run or crash pause, and a requested restart. [begin] and [request] share a lock, so a restart either sees the
     * job a loop just began or the loop sees the restart and begins nothing.
     */
    private class Runner {
        private val lock = Any()
        private var current: Job = Job().apply { complete() }
        private var pending: CompletableDeferred<Unit>? = null

        fun <J : Job> begin(start: () -> J): J? = synchronized(lock) { if (pending != null) null else start().also { current = it } }

        /** The job to cancel for a restart, or null when one is already pending. */
        fun request(gate: CompletableDeferred<Unit>): Job? =
            synchronized(lock) {
                if (pending != null) null
                else {
                    pending = gate
                    current
                }
            }

        fun restartRequested(): Boolean = synchronized(lock) { pending != null }

        /** Waits for the restart's step to finish, then clears it so the next run may begin. */
        suspend fun awaitRestart() {
            synchronized(lock) { pending }?.await()
            synchronized(lock) { pending = null }
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
        private val NOT_RESTARTABLE = setOf(WorkerState.DISABLED, WorkerState.STOPPED, WorkerState.FINISHED)
    }
}

/** Why [WorkerSupervisor.restart] refused, with the text an operator sees. */
enum class RestartRefusal(val message: String) {
    UNKNOWN("No such worker"),
    ONE_OFF("A one-off worker isn't restarted"),
    NOT_RUNNING("The worker is disabled, stopped or finished"),
    ALREADY_RESTARTING("The worker is already restarting"),
}
