package io.github.solcott.activitytracker.appview.ops

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.slf4j.Logger

/**
 * Runs [block] as one step of a worker: a failure is reported to this worker and logged with [what] (a capitalised phrase such as "Checking
 * due handles"), never thrown, so the worker carries on. Cancellation propagates. Returns the block's result, or null when it failed.
 */
suspend fun <T : Any> WorkerHandle.attempt(log: Logger, what: String, block: suspend () -> T): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        failure(e)
        log.warn("{} failed; retrying: {}", what, e.message)
        null
    }

/**
 * Runs [round] until cancelled, [attempt]ing each and reporting it to this worker as a success when it didn't fail, then [pause]s before
 * the next one (a fixed delay, or a wait for a wakeup with a timeout).
 */
suspend fun WorkerHandle.everyRound(log: Logger, what: String, pause: suspend () -> Unit, round: suspend () -> Unit): Nothing {
    while (true) {
        if (attempt(log, what, round) != null) success()
        pause()
    }
}

/**
 * Runs [connect] until cancelled, connecting again whenever it ends: right after a connection that ended cleanly ([INITIAL_BACKOFF]), else
 * after a pause doubling with each failure up to [maxBackoff]. Each failure is reported to this worker and passed to [onFailure] with the
 * pause before the next try, for logging. Cancellation propagates.
 */
suspend fun WorkerHandle.reconnecting(
    maxBackoff: Duration,
    onFailure: (Throwable, Duration) -> Unit,
    connect: suspend () -> Unit,
): Nothing {
    var backoff = INITIAL_BACKOFF
    while (true) {
        try {
            connect()
            backoff = INITIAL_BACKOFF
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            failure(e)
            onFailure(e, backoff)
        }
        delay(backoff)
        backoff = (backoff * 2).coerceAtMost(maxBackoff)
    }
}

private val INITIAL_BACKOFF = 1.seconds
