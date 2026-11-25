package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.ops.WorkerHandle
import io.github.solcott.activitytracker.appview.ops.everyRound
import io.github.solcott.activitytracker.lexicon.Did
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.slf4j.LoggerFactory

/**
 * Checks stored handles again once they're due (identity/handle-verification, identity/outbound-requests): actors whose
 * `handle_check_due_at` is null or past, oldest first, one at a time through [Indexer.recheckHandle], which sets the next due time, [pace]
 * apart so a burst of `#identity` events reaches the PLC directory and the handle lookups as a trickle. A round works until nothing is due,
 * then waits [POLL_INTERVAL] or until an `#identity` event wakes it. Accounts an `#identity` event named go ahead of the rest of what's
 * due, so a changed handle shows within a batch however long the backlog is. While [plcPausedUntil] says the directory asked to slow down
 * (a 429), it waits that out before the next check.
 */
class HandleSweep(
    private val db: Database,
    private val indexer: Indexer,
    private val clock: Clock = Clock.System,
    private val pace: Duration = DEFAULT_PACE,
    private val plcPausedUntil: () -> Instant? = { null },
) {
    private val log = LoggerFactory.getLogger(HandleSweep::class.java)
    private val wakeups = Channel<Unit>(Channel.CONFLATED)

    /** DIDs `#identity` events named since they were last checked, bounded: past [MAX_CHANGED] they wait their turn among the due. */
    private val changed = ConcurrentHashMap.newKeySet<String>()

    init {
        indexer.identityChanges.onChange { did ->
            if (changed.size < MAX_CHANGED) changed += did.value
            wakeups.trySend(Unit)
        }
    }

    /** Runs until cancelled, reporting each round to [worker]. */
    suspend fun run(worker: WorkerHandle = WorkerHandle.None): Nothing {
        worker.everyRound(log, "Checking due handles", { withTimeoutOrNull(POLL_INTERVAL) { wakeups.receive() } }) { runDue() }
    }

    /** Checks every actor that is due, [BATCH] at a time, until none is. Exposed for tests. */
    suspend fun runDue() {
        do {
            val named = takeChanged()
            for (did in named) check(did)
            val due = dueBatch()
            for (did in due) check(did)
        } while (due.size == BATCH || named.isNotEmpty())
    }

    private suspend fun check(did: String) {
        plcPausedUntil()?.let { until -> delay(until - clock.now()) }
        indexer.recheckHandle(Did(did))
        delay(pace)
    }

    /** Up to [BATCH] of the DIDs `#identity` events named, taken out of [changed]. */
    private fun takeChanged(): List<String> = buildList {
        val each = changed.iterator()
        while (size < BATCH && each.hasNext()) {
            add(each.next())
            each.remove()
        }
    }

    private suspend fun dueBatch(): List<String> {
        val now = clock.now().toEpochMilliseconds()
        return db.tx {
            Actors.select(Actors.did)
                .where { Actors.handleCheckDueAt.isNull() or (Actors.handleCheckDueAt lessEq now) }
                .orderBy(Actors.handleCheckDueAt to SortOrder.ASC_NULLS_FIRST, Actors.did to SortOrder.ASC)
                .limit(BATCH)
                .map { it[Actors.did] }
        }
    }

    companion object {
        /** Between checks: at most about 5 a second, each a DID document fetch and up to three handle lookups. */
        val DEFAULT_PACE = 200.milliseconds
        private val POLL_INTERVAL = 10.minutes
        private const val BATCH = 200
        private const val MAX_CHANGED = 10_000
    }
}
