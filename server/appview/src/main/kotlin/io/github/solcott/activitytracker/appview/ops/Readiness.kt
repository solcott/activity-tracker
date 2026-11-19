package io.github.solcott.activitytracker.appview.ops

import io.github.solcott.activitytracker.appview.db.tx
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory

/**
 * The readiness check (`/xrpc/_ready`, dev/worker-telemetry): whether the database answers a trivial query within [timeout]. Background
 * workers don't count. An answer is reused for [cacheFor], and checks that arrive while one is running wait for it, so a flood of probes
 * queries the database at most once per [cacheFor].
 */
class Readiness(
    private val ping: suspend () -> Unit,
    private val timeout: Duration = 2.seconds,
    private val cacheFor: Duration = 5.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(db: Database) : this({ db.tx { exec("SELECT 1") } })

    private val log = LoggerFactory.getLogger(Readiness::class.java)
    private val lock = Mutex()
    private var last: Pair<Boolean, TimeMark>? = null

    // A ping stuck in a blocking JDBC call can't be cancelled; it's left to finish here rather than in the request.
    private val pings = CoroutineScope(SupervisorJob() + dispatcher)

    /** Whether the database answers. */
    suspend fun databaseUp(): Boolean = lock.withLock {
        last?.let { (up, at) -> if (at.elapsedNow() < cacheFor) return@withLock up }
        val up = withTimeoutOrNull(timeout) { pings.async { runCatching { ping() }.isSuccess }.await() } ?: false
        if (!up) log.warn("Readiness: the database didn't answer within {}", timeout)
        last = up to timeSource.markNow()
        up
    }
}
