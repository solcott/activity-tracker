package io.github.solcott.activitytracker.atproto

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Routes mock requests by `host + path`; records every request for assertions. The engine handles requests on several threads at once, so
 * the record is written under a lock: a plain list loses one of two requests added together.
 */
class MockNetwork {
    private val recorded = mutableListOf<HttpRequestData>()
    private val recording = Mutex()

    /** The requests so far, in the order they reached the engine. Read it once the requests being asserted have finished. */
    val requests: List<HttpRequestData>
        get() = recorded.toList()

    private val routes = mutableMapOf<String, suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData>()

    fun on(
        hostAndPath: String,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) {
        routes[hostAndPath] = handler
    }

    /** The handler [on] set for [hostAndPath], so a test can wrap it. */
    fun handlerFor(hostAndPath: String): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        checkNotNull(routes[hostAndPath])

    val client =
        HttpClient(
            MockEngine { request ->
                recording.withLock { recorded += request }
                val key = request.url.host + request.url.encodedPath
                val handler = routes[key] ?: return@MockEngine respond("no route for $key", HttpStatusCode.NotFound)
                handler(request)
            },
        ) {
            expectSuccess = false
        }
}

fun MockRequestHandleScope.respondJson(
    json: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    vararg extraHeaders: Pair<String, String>,
): HttpResponseData =
    respond(
        json,
        status,
        headersOf(
            *(listOf(HttpHeaders.ContentType to listOf("application/json")) + extraHeaders.map { it.first to listOf(it.second) })
                .toTypedArray(),
        ),
    )
