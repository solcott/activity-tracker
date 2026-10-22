package io.github.solcott.activitytracker.app

import app.cash.turbine.ReceiveTurbine
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Skips states until one matches [predicate], and returns it. */
suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    var state = awaitItem()
    while (!predicate(state)) state = awaitItem()
    return state
}

/** Waits in real time until [condition] holds, then a little longer: for work no state shows, like a reload a loaded form ignores. */
@Suppress("InjectDispatcher") // Real time, off the test scheduler, is the point.
suspend fun awaitSettled(condition: () -> Boolean) =
    withContext(Dispatchers.Default) {
        withTimeout(5.seconds) { while (!condition()) delay(10.milliseconds) }
        delay(100.milliseconds)
    }
