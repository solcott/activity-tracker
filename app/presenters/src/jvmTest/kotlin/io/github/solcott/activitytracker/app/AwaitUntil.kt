package io.github.solcott.activitytracker.app

import app.cash.turbine.ReceiveTurbine

/** Skips states until one matches [predicate], and returns it. */
suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    var state = awaitItem()
    while (!predicate(state)) state = awaitItem()
    return state
}
