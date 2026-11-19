package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.ops.WorkerHandle
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.slf4j.LoggerFactory

/**
 * Checks stored handles again once they're due (identity/handle-verification): every [POLL_INTERVAL], up to [BATCH] actors whose
 * `handle_check_due_at` is null or past, oldest first, one at a time through [Indexer.recheckHandle], which sets the next due time.
 */
class HandleSweep(private val db: Database, private val indexer: Indexer, private val clock: Clock = Clock.System) {
    private val log = LoggerFactory.getLogger(HandleSweep::class.java)

    /** Runs until cancelled, reporting each round to [worker]. */
    suspend fun run(worker: WorkerHandle = WorkerHandle.None): Nothing {
        while (true) {
            try {
                runDue()
                worker.success()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                worker.failure(e)
                log.warn("Checking due handles failed; retrying: {}", e.message)
            }
            delay(POLL_INTERVAL)
        }
    }

    /** Checks every actor that is due, up to [BATCH]. Exposed for tests. */
    suspend fun runDue() {
        val now = clock.now().toEpochMilliseconds()
        val due = db.tx {
            Actors.select(Actors.did)
                .where { Actors.handleCheckDueAt.isNull() or (Actors.handleCheckDueAt lessEq now) }
                .orderBy(Actors.handleCheckDueAt to SortOrder.ASC_NULLS_FIRST, Actors.did to SortOrder.ASC)
                .limit(BATCH)
                .map { it[Actors.did] }
        }
        for (did in due) indexer.recheckHandle(Did(did))
    }

    private companion object {
        val POLL_INTERVAL = 10.minutes
        const val BATCH = 200
    }
}
