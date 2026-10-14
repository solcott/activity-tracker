package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.atproto.space.SpaceCredentials
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.io.IOException
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * The Spaces side of an athlete's PDS, as the AppView's syncer sees it: credentials, `registerNotify`, `listRepoOps` and `listRecords` for
 * the authority's repo in each space. Every athlete is on `http://<name>pds.test`, resolved through `http://plc.test`. Writes go straight
 * into the op log; nothing is sent to the AppView unless a test calls `notifyWrite` itself.
 */
/** [clock] dates notification registrations; credentials use real time, as [SpaceCredentials] reads them with its own clock. */
class FakeSpacesPds(private val clock: Clock = Clock.System) {
    private class Op(val rev: String, val collection: String, val rkey: String, val cid: String?, val value: JsonObject?)

    private val logs = mutableMapOf<SpaceUri, MutableList<Op>>()
    private var tick = 0

    /** Spaces whose `getSpaceCredential` answers `SpaceDeleted`. */
    val deleted = mutableSetOf<SpaceUri>()

    /** Authorities whose PDS can't be reached. */
    val unreachable = mutableSetOf<Did>()

    /** Spaces whose op log was compacted up to a revision: `listRepoOps` answers only the ops after it, as a PDS that compacts may. */
    val compactedTo = mutableMapOf<SpaceUri, String>()

    /** Runs once, the next time `listRepoOps` is answered, before the answer is built: a write that lands mid-pull. */
    var duringNextPull: (() -> Unit)? = null

    /** Every `registerNotify` input, in order. */
    val registrations = mutableListOf<JsonObject>()
    val requests = mutableListOf<HttpRequestData>()

    val client = HttpClient(MockEngine { request -> handle(request) }) { expectSuccess = false }

    fun put(space: SpaceUri, collection: String, rkey: String, value: String): String {
        val cid = "bafyrei${collection.substringAfterLast('.').lowercase()}$rkey${++tick}"
        log(space) += Op(nextRev(), collection, rkey, cid, AtJson.parseToJsonElement(value).jsonObject)
        return cid
    }

    fun delete(space: SpaceUri, collection: String, rkey: String) {
        log(space) += Op(nextRev(), collection, rkey, null, null)
    }

    /** The current revision of [space]'s authority repo, for a `notifyWrite`. */
    fun rev(space: SpaceUri): String = log(space).last().rev

    private fun log(space: SpaceUri) = logs.getOrPut(space) { mutableListOf() }

    // TIDs sort as strings; these do too.
    private fun nextRev() = "3m" + (++tick).toString().padStart(REV_DIGITS, '0')

    private fun MockRequestHandleScope.handle(request: HttpRequestData) = run {
        requests += request
        val host = request.url.host
        val path = request.url.encodedPath
        when {
            host == "plc.test" -> didDocument(path.removePrefix("/"))
            unreachable.any { host == "${name(it.value)}pds.test" } -> throw IOException("$host unreachable")
            path == "/xrpc/com.atproto.space.getSpaceCredential" -> {
                val space = SpaceUri(body(request).getValue("space").toString().trim('"'))
                if (space in deleted) error("SpaceDeleted") else json("""{"credential":"${credential()}"}""")
            }
            path == "/xrpc/com.atproto.space.registerNotify" -> {
                registrations += body(request)
                json("""{"expiresAt":"${clock.now() + 24.hours}"}""")
            }
            path == "/xrpc/com.atproto.space.listRepoOps" -> {
                val params = request.url.parameters
                val answer = listRepoOps(params).toString()
                // The write lands after the log was read, as a notification racing a pull would.
                if (params["since"] != null) duringNextPull?.also { duringNextPull = null }?.invoke()
                json(answer)
            }
            path == "/xrpc/com.atproto.space.listRecords" -> json(listRecords(request.url.parameters).toString())
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    private fun MockRequestHandleScope.didDocument(did: String) =
        json(
            """{"id":"$did","alsoKnownAs":["at://${name(did)}.test"],
               "service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"http://${name(did)}pds.test"}]}"""
        )

    /** The ops after `since`, values and all unless `excludeValues`, and the head as the commit. */
    private fun listRepoOps(params: Parameters): JsonObject {
        val space = SpaceUri(params["space"]!!)
        val since = params["since"]
        return buildJsonObject {
            put(
                "ops",
                buildJsonArray {
                    log(space)
                        .filter { since == null || it.rev > since }
                        .filter { op -> compactedTo[space]?.let { op.rev > it } ?: true }
                        .forEach { op ->
                            add(
                                buildJsonObject {
                                    put("rev", op.rev)
                                    put("collection", op.collection)
                                    put("rkey", op.rkey)
                                    put("cid", op.cid)
                                    if (params["excludeValues"] != "true" && op.value != null) put("value", op.value)
                                }
                            )
                        }
                },
            )
            log(space).lastOrNull()?.let { put("commit", buildJsonObject { put("rev", it.rev) }) }
        }
    }

    /** Each record of `collection` as it stands: the last op for its rkey, unless that deleted it. */
    private fun listRecords(params: Parameters): JsonObject {
        val space = SpaceUri(params["space"]!!)
        val collection = params["collection"]
        val current =
            log(space)
                .filter { collection == null || it.collection == collection }
                .groupBy { it.collection to it.rkey }
                .mapNotNull { (_, ops) -> ops.last().takeIf { it.cid != null } }
        return buildJsonObject {
            put(
                "records",
                buildJsonArray {
                    current.forEach { op ->
                        add(
                            buildJsonObject {
                                put("collection", op.collection)
                                put("rkey", op.rkey)
                                put("cid", op.cid)
                                put("value", op.value!!)
                            }
                        )
                    }
                },
            )
        }
    }

    private fun name(did: String) = did.substringAfterLast(':')

    private fun body(request: HttpRequestData): JsonObject = AtJson.parseToJsonElement((request.body as TextContent).text).jsonObject

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun MockRequestHandleScope.error(name: String) = json("""{"error":"$name","message":"$name"}""", HttpStatusCode.BadRequest)

    private fun credential(): String {
        val b64 = Base64.getUrlEncoder().withoutPadding()
        val exp = Clock.System.now().epochSeconds + 600
        return b64.encodeToString("""{"alg":"ES256K"}""".toByteArray()) +
            "." +
            b64.encodeToString("""{"exp":$exp}""".toByteArray()) +
            ".c2ln"
    }

    private companion object {
        /** Digits after the `3m` prefix: fixed width, so revisions sort as strings like TIDs. */
        const val REV_DIGITS = 11
    }
}
