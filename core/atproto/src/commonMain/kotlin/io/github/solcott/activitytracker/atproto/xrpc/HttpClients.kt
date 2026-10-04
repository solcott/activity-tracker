package io.github.solcott.activitytracker.atproto.xrpc

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.ktor.KermitKtorLogger
import io.github.solcott.activitytracker.lexicon.AtJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json

private const val REQUEST_TIMEOUT_MS = 60_000L
private const val CONNECT_TIMEOUT_MS = 15_000L

/**
 * The engine comes from `ktor-client-engine-defaults` (OkHttp / Darwin / Js).
 *
 * @param logger receives request and response headers under the `Http` tag. Null skips the Logging plugin.
 * @param cacheStorage persistent [HttpCache] storage (e.g. `FileStorage`). Null skips the plugin: the browser already caches for the web.
 */
fun createHttpClient(logger: Logger? = null, cacheStorage: CacheStorage? = null): HttpClient = HttpClient {
    expectSuccess = false
    install(ContentNegotiation) { json(AtJson) }
    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MS
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
    }
    if (cacheStorage != null) {
        install(HttpCache) { publicStorage(cacheStorage) }
    }
    if (logger != null) {
        install(Logging) {
            level = LogLevel.HEADERS
            this.logger = KermitKtorLogger(Severity.Debug, logger.withTag("Http"))
        }
    }
}
