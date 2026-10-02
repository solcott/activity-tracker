package io.github.solcott.activitytracker.atproto.xrpc

import io.github.solcott.activitytracker.lexicon.AtJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json

/**
 * The engine comes from `ktor-client-engine-defaults` (OkHttp / Darwin / Js).
 *
 * @param cacheStorage persistent [HttpCache] storage (e.g. `FileStorage`). Null skips the plugin: the browser already caches for the web.
 */
fun createHttpClient(logging: Boolean = false, cacheStorage: CacheStorage? = null): HttpClient = HttpClient {
    expectSuccess = false
    install(ContentNegotiation) { json(AtJson) }
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 15_000
    }
    if (cacheStorage != null) {
        install(HttpCache) { publicStorage(cacheStorage) }
    }
    if (logging) {
        install(Logging) { level = LogLevel.HEADERS }
    }
}
