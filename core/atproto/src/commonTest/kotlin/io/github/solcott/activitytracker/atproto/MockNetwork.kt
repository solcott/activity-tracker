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

/** Routes mock requests by `host + path`; records every request for assertions. */
class MockNetwork {
    val requests = mutableListOf<HttpRequestData>()
    private val routes = mutableMapOf<String, suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData>()

    fun on(
        hostAndPath: String,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) {
        routes[hostAndPath] = handler
    }

    val client =
        HttpClient(
            MockEngine { request ->
                requests += request
                val key = request.url.host + request.url.encodedPath
                val handler = routes[key] ?: return@MockEngine respond("no route for $key", HttpStatusCode.NotFound)
                handler(request)
            }
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
                .toTypedArray()
        ),
    )
