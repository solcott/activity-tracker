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
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

    /** The writes each `applyWrites` call carried, refused ones too: each write's `$type` suffix, collection and rkey. */
    fun commits(): List<List<Triple<String, String, String>>> =
        calls(APPLY_WRITES).map { call ->
            call.body!!.getValue("writes").jsonArray.map {
                val write = it.jsonObject
                Triple(
                    write.getValue("\$type").jsonPrimitive.content.substringAfter('#'),
                    write.getValue("collection").jsonPrimitive.content,
                    write.getValue("rkey").jsonPrimitive.content,
                )
            }
        }

    /**
     * Serves [records], the repo by collection and rkey, as a PDS does: `getRecord`, and `applyWrites`, all or nothing, refusing a whole
     * call that creates a key the repo holds or updates one it doesn't. [refuse] may answer the nth `applyWrites` (from 1) itself.
     */
    fun serveRepo(
        records: MutableMap<Pair<String, String>, JsonObject>,
        refuse: MockRequestHandleScope.(Int) -> HttpResponseData? = { null },
    ) {
        on("com.atproto.repo.getRecord") { call ->
            val key = call.params.getValue("collection") to call.params.getValue("rkey")
            records[key]?.let { json("""{"uri":"at://$DID/${key.first}/${key.second}","value":$it}""") }
                ?: error(HttpStatusCode.BadRequest, "RecordNotFound")
        }
        var commits = 0
        on(APPLY_WRITES) { call ->
            refuse(++commits)?.let {
                return@on it
            }
            val body = call.body!!
            assertEquals(DID, body.getValue("repo").jsonPrimitive.content)
            val writes = body.getValue("writes").jsonArray.map { it.jsonObject }
            for (write in writes) {
                val key = write.getValue("collection").jsonPrimitive.content to write.getValue("rkey").jsonPrimitive.content
                val type = write.getValue("\$type").jsonPrimitive.content
                val create = type.endsWith("#create")
                if (!type.endsWith("#delete") && create == key in records) {
                    return@on error(HttpStatusCode.BadRequest, "InvalidRequest", "Bad write: $key")
                }
            }
            for (write in writes) {
                val key = write.getValue("collection").jsonPrimitive.content to write.getValue("rkey").jsonPrimitive.content
                if (write.getValue("\$type").jsonPrimitive.content.endsWith("#delete")) records.remove(key)
                else records[key] = write.getValue("value").jsonObject
            }
            json("""{"results":[]}""")
        }
    }

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
        const val APPLY_WRITES = "com.atproto.repo.applyWrites"
    }
}

internal fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

internal fun MockRequestHandleScope.error(status: HttpStatusCode, error: String, message: String = error) =
    json("""{"error":"$error","message":"$message"}""", status)
