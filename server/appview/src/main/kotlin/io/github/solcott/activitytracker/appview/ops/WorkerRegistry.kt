package io.github.solcott.activitytracker.appview.ops

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock
import kotlin.time.Instant

/** How a worker runs: a polling loop, a long-lived stream, or a task that runs once at start. */
enum class WorkerKind {
    LOOP,
    STREAM,
    ONE_OFF,
}

/** Where a worker stands (dev/worker-telemetry). */
enum class WorkerState {
    STARTING,
    RUNNING,
    /** Its last round or connection failed; it keeps going on its own. */
    BACKING_OFF,
    /** Its run ended with an error; the supervisor runs it again after a pause. */
    RESTARTING,
    /** A one-off that completed. */
    FINISHED,
    /** Stopped by shutdown. */
    STOPPED,
    /** Switched off by configuration ([WorkerStatus.disabledReason]). */
    DISABLED,
}

/**
 * One background worker's status, as `admin-health` and the metrics read it. [lastError] is an [ErrorSummary], never record content.
 * [iterations] counts completed rounds (a loop) or events (a stream).
 */
data class WorkerStatus(
    val name: String,
    val kind: WorkerKind,
    val state: WorkerState,
    val startedAt: Instant? = null,
    val lastSuccessAt: Instant? = null,
    val lastErrorAt: Instant? = null,
    val lastError: String? = null,
    val iterations: Long = 0,
    val restarts: Int = 0,
    /** Restarts an operator asked for ([WorkerSupervisor.restart]), counted apart from crash [restarts]. */
    val manualRestarts: Int = 0,
    val disabledReason: String? = null,
)

/** What a worker reports through: a completed round or event, or a failed one it carries on after. */
interface WorkerHandle {
    fun success()

    fun failure(e: Throwable)

    /** Reports nothing: for workers run outside a registry, as in tests. */
    object None : WorkerHandle {
        override fun success() = Unit

        override fun failure(e: Throwable) = Unit
    }
}

/**
 * The status of every background worker the AppView runs, in this process (it's a single process, so this is the whole truth). Each
 * worker's status is an immutable value swapped atomically, so a [snapshot] never waits on a worker and a worker never waits on a reader.
 */
class WorkerRegistry(private val clock: Clock = Clock.System) {
    private val workers = ConcurrentHashMap<String, AtomicReference<WorkerStatus>>()

    /** Every worker's status, by name. */
    fun snapshot(): List<WorkerStatus> = workers.values.map { it.get() }.sortedBy { it.name }

    /** [name]'s status, or null when no such worker is registered. */
    operator fun get(name: String): WorkerStatus? = workers[name]?.get()

    /** Registers [name] as switched off by configuration, for [reason]. */
    fun disabled(name: String, kind: WorkerKind, reason: String) {
        workers[name] = AtomicReference(WorkerStatus(name, kind, WorkerState.DISABLED, disabledReason = reason))
    }

    /** Registers [name] (or finds it again) and returns the handle it reports through. */
    fun handle(name: String, kind: WorkerKind): WorkerHandle {
        val ref = workers.computeIfAbsent(name) { AtomicReference(WorkerStatus(name, kind, WorkerState.STARTING)) }
        return Handle(ref)
    }

    /** The supervisor's side: a run started, crashed, or ended. */
    internal fun started(name: String) = update(name) { it.copy(state = WorkerState.RUNNING, startedAt = clock.now()) }

    internal fun crashed(name: String, e: Throwable) =
        update(name) {
            it.copy(state = WorkerState.RESTARTING, lastError = ErrorSummary.of(e), lastErrorAt = clock.now(), restarts = it.restarts + 1)
        }

    /** A run ended because a restart was requested; [e] is how it ended if it failed first, recorded as its last error. */
    internal fun restartedOnRequest(name: String, e: Throwable? = null) =
        update(name) { status ->
            val failed = e?.let { status.copy(lastError = ErrorSummary.of(it), lastErrorAt = clock.now()) } ?: status
            failed.copy(state = WorkerState.RESTARTING, manualRestarts = status.manualRestarts + 1)
        }

    internal fun ended(name: String, state: WorkerState) = update(name) { it.copy(state = state) }

    private fun update(name: String, change: (WorkerStatus) -> WorkerStatus) {
        workers[name]?.updateAndGet(change)
    }

    private inner class Handle(private val ref: AtomicReference<WorkerStatus>) : WorkerHandle {
        override fun success() {
            val now = clock.now()
            ref.updateAndGet { it.copy(state = WorkerState.RUNNING, lastSuccessAt = now, iterations = it.iterations + 1) }
        }

        override fun failure(e: Throwable) {
            val now = clock.now()
            val summary = ErrorSummary.of(e)
            ref.updateAndGet { it.copy(state = WorkerState.BACKING_OFF, lastError = summary, lastErrorAt = now) }
        }
    }
}
