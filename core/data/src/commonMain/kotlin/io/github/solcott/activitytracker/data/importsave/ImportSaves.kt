package io.github.solcott.activitytracker.data.importsave

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.activityfile.ImportSource
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.ImportSaveSourceEntity
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Starts a persisted batch save running for an account where the platform keeps it alive: Android hands it to a foreground WorkManager job,
 * which also runs it again after the process is killed; iOS asks for background time and a background task. Set by the platform's app at
 * startup ([ImportSaves.launcher]); without one, saves run in this process ([ImportSaves.launch]).
 */
fun interface ImportSaveLauncher {
    /**
     * Starts [did]'s saves: through the platform's own job, or [inProcess], which runs them in this process ([ImportSaves.runInProcess]).
     */
    fun launch(did: Did, inProcess: suspend () -> Unit)
}

/** Works through an account's persisted batch saves (feed/batch-import), in whichever session graph runs it. */
interface ImportSaveRunner {
    /** Runs each of the account's saves still to run, oldest first, unless another runner has them; returns when none is left to run. */
    suspend fun runAll()
}

/**
 * The batch saves running app-wide (feed/batch-import): one runner per account at a time, whichever session graph or process entry point
 * (the screen, a background job) starts it; the picked files of saves started in this process, which reach a save before any kept handle
 * does; and why the last run of a save failed, for the screen that started it. Cancelled per account at sign-out.
 */
@SingleIn(AppScope::class)
@Inject
@OptIn(ExperimentalAtomicApi::class)
@Suppress("TooManyFunctions") // One per step of a save's life: holding files, running, ending, signing out.
class ImportSaves(private val db: AppDatabase, private val keeper: SourceKeeper) {
    @Suppress("InjectDispatcher") // App-wide work that outlives every screen and session graph, as `ActivityRecorder`'s does.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val locks = AtomicReference(emptyMap<Did, Mutex>())
    private val jobs = AtomicReference(emptyMap<Did, Job>())

    /** The platform's way of keeping a save alive, set at startup; null runs saves in this process. */
    var launcher: ImportSaveLauncher? = null

    /** Batch saves whose runner is working on them now, by batch id. */
    val running: StateFlow<Set<String>>
        field = MutableStateFlow(emptySet<String>())

    /** Why each save's last run stopped short, by batch id, until it runs again. */
    val failures: StateFlow<Map<String, Throwable>>
        field = MutableStateFlow(emptyMap<String, Throwable>())

    private val picked = AtomicReference(emptyMap<String, List<ImportSource>>())

    /** Keeps [sources], the files picked for save [batchId], for runs in this process. */
    fun hold(batchId: String, sources: List<ImportSource>) {
        update(picked) { it + (batchId to sources) }
    }

    /** The files picked for [batchId] in this process, if it was started here. */
    fun picked(batchId: String): List<ImportSource>? = picked.load()[batchId]

    /** Lets go of [batchId]'s picked files once its save is over. */
    fun drop(batchId: String) {
        update(picked) { it - batchId }
    }

    /**
     * Runs [block] holding [did]'s save lock, or returns null at once when another runner holds it: that one finishes what's queued,
     * including saves written after it started.
     */
    suspend fun <T> exclusive(did: Did, block: suspend () -> T): T? {
        val lock = lockOf(did)
        if (!lock.tryLock()) return null
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    /** Starts [did]'s saves: through the platform's [launcher], else [inProcess] in this app-wide scope. */
    fun launch(did: Did, inProcess: suspend () -> Unit) {
        val platform = launcher
        if (platform != null) platform.launch(did, inProcess) else runInProcess(did, inProcess)
    }

    /** Runs [block], [did]'s saves, in this app-wide scope, until it ends or [did] signs out. */
    fun runInProcess(did: Did, block: suspend () -> Unit) {
        val job = scope.launch { block() }
        update(jobs) { it + (did to job) }
    }

    /** Stops [did]'s in-process saves: the athlete signed out. */
    fun cancel(did: Did) {
        jobs.load()[did]?.cancel()
        update(jobs) { it - did }
    }

    /** Stops every in-process save, for tests. */
    internal fun cancelAll() = scope.coroutineContext[Job]?.cancelChildren()

    /** Marks [batchId] running; a runner calls it as it starts the save. */
    fun started(batchId: String) {
        running.update { it + batchId }
        failures.update { it - batchId }
    }

    /** Marks [batchId] no longer running, with why it stopped short if it did. */
    fun ended(batchId: String, failure: Throwable?) {
        running.update { it - batchId }
        if (failure != null) failures.update { it + (batchId to failure) }
    }

    /** Ends save [batchId]: lets go of its files and drops it. Its import stays in the upload queue with what it saved. */
    suspend fun end(batchId: String) =
        withContext(NonCancellable) {
            release(batchId)
            db.importSaves().delete(batchId)
        }

    /** Lets go of what keeps save [batchId]'s files (grants, copies, the files picked in this process), once. */
    suspend fun release(batchId: String) =
        withContext(NonCancellable) {
            val kept = db.importSaves().sources(batchId).mapNotNull { row -> row.kept() }
            if (kept.isNotEmpty()) keeper.release(batchId, kept)
            db.importSaves().clearHandles(batchId)
            drop(batchId)
        }

    /** Whether [did] has a save not yet finished, running or waiting to resume: [signedOut] would drop its unsaved activities. */
    suspend fun hasSaves(did: Did): Boolean = db.importSaves().idsOf(did.value).isNotEmpty()

    /** [did] signed out: their saves stop, and are dropped with what keeps their files (feed/batch-import). */
    suspend fun signedOut(did: Did) =
        withContext(NonCancellable) {
            cancel(did)
            db.importSaves().idsOf(did.value).forEach { end(it) }
        }

    /** Lets go of grants and copies that no save names any more: a crash between a save's end and its release. */
    suspend fun sweep() {
        keeper.sweep(db.importSaves().allSources().groupBy({ it.batchId }) { it.kept() }.mapValues { (_, kept) -> kept.filterNotNull() })
    }

    /**
     * A session for [did] started: lets go of what no save names, then [launch]es [runner] if [store] holds a save left unfinished (the app
     * was killed, or quit, mid-save).
     */
    suspend fun resume(did: Did, store: ImportSaveStore, runner: ImportSaveRunner) {
        sweep()
        if (store.runnable().isNotEmpty()) launch(did) { runner.runAll() }
    }

    private fun ImportSaveSourceEntity.kept(): KeptSource? = kind?.let { kind -> handle?.let { KeptSource.of(kind, it) } }

    private fun lockOf(did: Did): Mutex {
        while (true) {
            val current = locks.load()
            current[did]?.let {
                return it
            }
            if (locks.compareAndSet(current, current + (did to Mutex()))) return lockOf(did)
        }
    }

    private inline fun <T> update(ref: AtomicReference<T>, change: (T) -> T) {
        while (true) {
            val current = ref.load()
            if (ref.compareAndSet(current, change(current))) return
        }
    }
}
