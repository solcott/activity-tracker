package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.AtJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * A fake PDS (at [PDS]) and AppView (at [APPVIEW]) on Ktor's `MockEngine`. The PDS signs in [ACCOUNT] with [PASSWORD] and mints service
 * auth naming its `aud` and `lxm`; anything else goes to the routes a test adds. Every request is recorded.
 */
internal class FakeServices {
    /** One recorded request: its service, XRPC method, query parameters, `Authorization` header and JSON body. */
    data class Call(val host: String, val nsid: String, val params: Map<String, String>, val auth: String?, val body: JsonObject?)

    val calls = mutableListOf<Call>()
    var sessions = 0
    var refreshes = 0
    /** The access JWT the PDS accepts; a test changes it to expire the held one. */
    var validAccess = "access-1"
    /** Whether the PDS still honours the refresh JWT. */
    var refreshAllowed = true
    private val routes = mutableMapOf<String, suspend MockRequestHandleScope.(Call) -> HttpResponseData>()

    val settings = Settings(ACCOUNT, PASSWORD, PDS, APPVIEW, APPVIEW_DID)

    val http = HttpClient(MockEngine { request -> handle(request) })

    /** Answers [nsid] on either service with [handler]. */
    fun on(nsid: String, handler: suspend MockRequestHandleScope.(Call) -> HttpResponseData) {
        routes[nsid] = handler
    }

    fun calls(nsid: String) = calls.filter { it.nsid == nsid }

    @Suppress("ReturnCount") // Each refusal a real PDS answers with.
    private suspend fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        val text = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()
        val call =
            Call(
                request.url.host,
                request.url.encodedPath.removePrefix("/xrpc/"),
                request.url.parameters.entries().associate { (k, v) -> k to v.first() },
                request.headers[HttpHeaders.Authorization],
                text?.takeIf { it.isNotBlank() }?.let { AtJson.parseToJsonElement(it).jsonObject },
            )
        calls += call
        return when (call.nsid) {
            "com.atproto.server.createSession" -> {
                val body = call.body!!
                if (body["password"].toString().trim('"') != PASSWORD) return error(HttpStatusCode.Unauthorized, "AuthenticationRequired")
                sessions++
                session()
            }
            "com.atproto.server.refreshSession" -> {
                if (call.auth != "Bearer refresh" || !refreshAllowed) return error(HttpStatusCode.BadRequest, "ExpiredToken")
                refreshes++
                validAccess = "access-r$refreshes"
                session()
            }
            "com.atproto.server.getServiceAuth" -> {
                if (call.auth != "Bearer $validAccess") return error(HttpStatusCode.BadRequest, "ExpiredToken")
                json("""{"token":"svc:${call.params["aud"]}:${call.params["lxm"]}"}""")
            }
            else -> {
                if (call.host == PDS_HOST && call.auth != "Bearer $validAccess") return error(HttpStatusCode.BadRequest, "ExpiredToken")
                routes[call.nsid]?.invoke(this, call) ?: error(HttpStatusCode.NotImplemented, "MethodNotImplemented")
            }
        }
    }

    private fun MockRequestHandleScope.session() =
        json("""{"accessJwt":"$validAccess","refreshJwt":"refresh","did":"$DID","handle":"importer.test"}""")

    companion object {
        const val PDS_HOST = "pds.test"
        const val PDS = "http://$PDS_HOST"
        const val APPVIEW = "http://appview.test"
        const val APPVIEW_DID = "did:web:appview.test"
        const val ACCOUNT = "importer.test"
        const val PASSWORD = "app-password"
        const val DID = "did:plc:importer"
    }
}

internal fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

internal fun MockRequestHandleScope.error(status: HttpStatusCode, error: String, message: String = error) =
    json("""{"error":"$error","message":"$message"}""", status)
