package io.github.solcott.activitytracker.data

import io.ktor.client.engine.js.JsError

/**
 * Ktor's browser engine fails with an `Error("Fail to fetch")` caused by a [JsError] when `fetch` itself rejects: offline, DNS, a refused
 * connection or CORS. Either one, wrapped or not, means no response came.
 */
internal actual fun Throwable.isPlatformNetworkFailure(): Boolean = this is JsError || cause is JsError
