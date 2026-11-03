package io.github.solcott.activitytracker.data

import app.cash.turbine.ReceiveTurbine
import kotlin.time.Duration.Companion.seconds

/**
 * How long a flow test waits for the next item, in real time. Repositories reach Room and the fake PDS on real IO threads, which take
 * longer than Turbine's 3 s default while `./gradlew check` runs other test tasks alongside; a stuck test still fails within this.
 */
val FLOW_TEST_TIMEOUT = 10.seconds

/**
 * Skips items until one matches [predicate], and returns it: for a stream of states, where an in-between one may never be seen (Room
 * reading a page more than once, `combine` keeping only the latest of each input).
 */
suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
