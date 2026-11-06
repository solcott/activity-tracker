package io.github.solcott.activitytracker.data.account

import io.github.solcott.activitytracker.lexicon.Did
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Starts an account's long-running work (a batch save, a data export) where the platform keeps it alive: Android hands it to a foreground
 * WorkManager job, which also runs it again after the process is killed; iOS asks for background time and a background task. Set by the
 * platform's app at startup; without one, the work runs in this process.
 */
fun interface AccountJobLauncher {
    /**
     * Starts [did]'s work: through the platform's own job, or [inProcess], which runs it in this process ([AccountRunner.runInProcess]).
     */
    fun launch(did: Did, inProcess: suspend () -> Unit)
}

/**
 * One kind of app-wide work per account, whichever session graph or process entry point starts it: one runner per account at a time
 * ([exclusive]), started through the platform's [launcher] or else in an app-wide scope, and stopped per account ([cancel]).
 */
@OptIn(ExperimentalAtomicApi::class)
internal class AccountRunner {
    @Suppress("InjectDispatcher") // App-wide work that outlives every screen and session graph.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val locks = AtomicReference(emptyMap<Did, Mutex>())
    private val jobs = AtomicReference(emptyMap<Did, Job>())

    /** The platform's way of keeping the work alive, set at startup; null runs it in this process. */
    var launcher: AccountJobLauncher? = null

    /** Runs [block] holding [did]'s lock, or returns null at once when another runner holds it: that one finishes the work. */
    suspend fun <T> exclusive(did: Did, block: suspend () -> T): T? {
        val lock = locks.getOrPut(did) { Mutex() }
        if (!lock.tryLock()) return null
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    /** Starts [did]'s work: through the platform's [launcher], else [inProcess] in this app-wide scope. */
    fun launch(did: Did, inProcess: suspend () -> Unit) {
        val platform = launcher
        if (platform != null) platform.launch(did, inProcess) else runInProcess(did, inProcess)
    }

    /** Runs [block], [did]'s work, in this app-wide scope, until it ends or is [cancel]led. */
    fun runInProcess(did: Did, block: suspend () -> Unit) {
        val job = scope.launch { block() }
        jobs.update { it + (did to job) }
    }

    /** Stops [did]'s in-process work. */
    fun cancel(did: Did) {
        jobs.load()[did]?.cancel()
        jobs.update { it - did }
    }

    /** Stops all in-process work, for tests. */
    fun cancelAll() = scope.coroutineContext[Job]?.cancelChildren()
}

/** [key]'s value in this app-wide map, or [create]'s, added, when it has none; concurrent callers all get the one added. */
@OptIn(ExperimentalAtomicApi::class)
internal fun <K, V> AtomicReference<Map<K, V>>.getOrPut(key: K, create: () -> V): V {
    while (true) {
        val current = load()
        current[key]?.let {
            return it
        }
        val created = create()
        if (compareAndSet(current, current + (key to created))) return created
    }
}
