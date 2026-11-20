package io.github.solcott.activitytracker.appview.ops

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** A [WorkerHandle] that records what a worker reported: "ok" per success, the error summary per failure. */
class RecordingWorker : WorkerHandle {
    val reports: MutableList<String> = CopyOnWriteArrayList()

    override fun success() {
        reports += OK
    }

    override fun failure(e: Throwable) {
        reports += ErrorSummary.of(e)
    }

    companion object {
        const val OK = "ok"

        /** Runs [body] with a fresh recorder until it has reported [count] times (or [timeout] passes), then cancels it. */
        suspend fun CoroutineScope.reportsOf(
            count: Int = 1,
            timeout: Duration = 10.seconds,
            body: suspend (WorkerHandle) -> Unit,
        ): List<String> {
            val worker = RecordingWorker()
            val job = launch { body(worker) }
            async { withTimeout(timeout) { while (worker.reports.size < count) delay(10) } }.await()
            job.cancelAndJoin()
            return worker.reports.toList()
        }
    }
}
