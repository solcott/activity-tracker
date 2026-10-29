package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.sync.LtHash
import io.github.solcott.activitytracker.atproto.space.SpaceCredentials
import io.github.solcott.activitytracker.atproto.space.SpaceMac
import io.github.solcott.activitytracker.atproto.space.SpaceMacHost
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.atproto.LexBytes
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The Spaces side of an athlete's PDS, as the AppView's syncer sees it: credentials, `registerNotify`, `listRepoOps` and `listRecords` for
 * the authority's repo in each space. Every athlete is on `http://<name>pds.test`, resolved through `http://plc.test`, whose DID document
 * publishes a `#atproto_kem` key ([rotateKem] replaces it). `listRepoOps` answers as the Oct 22 alpha does: it requires
 * `Atproto-Space-Want-Mac` and MACs the commit, which [macMode] and [commitSpace] can spoil. The commit's `data` is the repo's real set
 * hash ([LtHash]), as the Oct 29 alpha's is, which [clear] and [migrate] change the way `clearRecords` and an account move do. Writes go
 * straight into the op log; nothing is sent to the AppView unless a test calls `notifyWrite` itself.
 */
/** [clock] dates notification registrations; credentials use real time, as [SpaceCredentials] reads them with its own clock. */
class FakeSpacesPds(private val clock: Clock = Clock.System) {
    private class Op(
        val rev: String,
        val collection: String,
        val rkey: String,
        val cid: String?,
        val prev: String?,
        val value: JsonObject?,
    )

    private class Record(val cid: String, val value: JsonObject)

    /** One space's authority repo: its op log (which [clear] and [migrate] empty), its records, its set hash and its head revision. */
    private class Repo {
        val log = mutableListOf<Op>()
        val records = sortedMapOf<String, Record>()
        val hash = LtHash()
        var head: String? = null
    }

    private val repos = mutableMapOf<SpaceUri, Repo>()
    private var tick = 0

    /** Spaces whose `getSpaceCredential` answers `SpaceDeleted`. */
    val deleted = mutableSetOf<SpaceUri>()

    /** Spaces whose `getSpaceCredential` answers `SpaceNotFound`: missing, or refusing the AppView (the managing app unreachable). */
    val notFound = mutableSetOf<SpaceUri>()

    /** Spaces whose commits carry a `data` their records don't fold to, as a host that lost track would. */
    val wrongData = mutableSetOf<SpaceUri>()

    /**
     * Runs once, the next time `listRecords` is answered, before the answer is built: a write that lands after the backfill read its head.
     */
    var duringNextListing: (() -> Unit)? = null

    /** Authorities whose PDS can't be reached. */
    val unreachable = mutableSetOf<Did>()

    /** Spaces whose op log was compacted up to a revision: `listRepoOps` answers only the ops after it, as a PDS that compacts may. */
    val compactedTo = mutableMapOf<SpaceUri, String>()

    /** Runs once, the next time `listRepoOps` is answered, before the answer is built: a write that lands mid-pull. */
    var duringNextPull: (() -> Unit)? = null

    /** How `listRepoOps` MACs its commit. */
    enum class MacMode {
        /** With the athlete's current KEM key. */
        VALID,

        /** With some other key: a forged response, or a stale one. */
        WRONG_KEY,

        /** No `Atproto-Space-Mac` at all. */
        OMITTED,
    }

    var macMode = MacMode.VALID

    /** The commit names this space instead of the one asked for, while set. */
    var commitSpace: SpaceUri? = null

    private val kems = mutableMapOf<String, SpaceMacHost.KemKey>()

    private suspend fun kem(did: String): SpaceMacHost.KemKey = kems.getOrPut(did) { SpaceMacHost.generateKemKey() }

    /** [did]'s KEM key rotated: its DID document publishes a new one from now on. */
    suspend fun rotateKem(did: Did) {
        kems[did.value] = SpaceMacHost.generateKemKey()
    }

    /** Every `registerNotify` input, in order. */
    val registrations = mutableListOf<JsonObject>()
    val requests = mutableListOf<HttpRequestData>()

    val client = HttpClient(MockEngine { request -> handle(request) }) { expectSuccess = false }

    fun put(space: SpaceUri, collection: String, rkey: String, value: String): String {
        val cid = "bafyrei${collection.substringAfterLast('.').lowercase()}$rkey${++tick}"
        val json = AtJson.parseToJsonElement(value).jsonObject
        write(space, collection, rkey, Record(cid, json))
        return cid
    }

    fun delete(space: SpaceUri, collection: String, rkey: String) = write(space, collection, rkey, null)

    /** `clearRecords` on [space]'s authority repo: no records, an empty op log, and a new revision whose `data` is the empty set's. */
    fun clear(space: SpaceUri) {
        val repo = repo(space)
        repo.records.forEach { (path, record) -> repo.hash.remove("$path/${record.cid}") }
        repo.records.clear()
        repo.log.clear()
        repo.head = nextRev()
    }

    /** The authority's account moved hosts: the same records and `data` at a new revision, with an empty op log. */
    fun migrate(space: SpaceUri) {
        val repo = repo(space)
        repo.log.clear()
        repo.head = nextRev()
    }

    /** The current revision of [space]'s authority repo, for a `notifyWrite`. */
    fun rev(space: SpaceUri): String = repo(space).head!!

    private fun write(space: SpaceUri, collection: String, rkey: String, record: Record?) {
        val repo = repo(space)
        val path = "$collection/$rkey"
        val prev = repo.records[path]?.cid
        prev?.let { repo.hash.remove(LtHash.element(collection, rkey, it)) }
        if (record == null) repo.records.remove(path) else repo.records[path] = record
        record?.let { repo.hash.add(LtHash.element(collection, rkey, it.cid)) }
        val rev = nextRev()
        repo.log += Op(rev, collection, rkey, record?.cid, prev, record?.value)
        repo.head = rev
    }

    private fun repo(space: SpaceUri) = repos.getOrPut(space) { Repo() }

    // TIDs sort as strings; these do too.
    private fun nextRev() = "3m" + (++tick).toString().padStart(REV_DIGITS, '0')

    private suspend fun MockRequestHandleScope.handle(request: HttpRequestData) = run {
        requests += request
        val host = request.url.host
        val path = request.url.encodedPath
        when {
            host == "plc.test" -> didDocument(path.removePrefix("/"))
            unreachable.any { host == "${name(it.value)}pds.test" } -> throw IOException("$host unreachable")
            path == "/xrpc/com.atproto.space.getSpaceCredential" -> {
                val input = body(request)
                val space = SpaceUri(input.getValue("space").jsonPrimitive.content)
                val kid = input["credentialKid"]?.jsonPrimitive?.content.orEmpty()
                when {
                    // The Oct 22 exchange is unauthenticated and binds the credential to a P-256 did:key.
                    request.headers[HttpHeaders.Authorization] != null || !kid.startsWith(P256_DID_KEY) -> error("InvalidRequest")
                    space in deleted -> error("SpaceDeleted")
                    space in notFound -> error("SpaceNotFound")
                    else -> json("""{"credential":"${credential()}"}""")
                }
            }
            path == "/xrpc/com.atproto.space.registerNotify" -> {
                registrations += body(request)
                json("""{"expiresAt":"${clock.now() + 24.hours}"}""")
            }
            path == "/xrpc/com.atproto.space.listRepoOps" -> {
                val params = request.url.parameters
                val wantMac = request.headers[SpaceMac.WANT_MAC_HEADER]
                if (wantMac == null) return@run error("BadSpaceMac")
                val (answer, mac) = listRepoOps(params, wantMac)
                // The write lands after the log was read, as a notification racing a pull would.
                if (params["since"] != null) duringNextPull?.also { duringNextPull = null }?.invoke()
                json(answer.toString(), extraHeaders = listOfNotNull(mac?.let { SpaceMac.MAC_HEADER to it }))
            }
            path == "/xrpc/com.atproto.space.listRecords" -> {
                duringNextListing?.also { duringNextListing = null }?.invoke()
                json(listRecords(request.url.parameters).toString())
            }
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    private suspend fun MockRequestHandleScope.didDocument(did: String) =
        json(
            """{"id":"$did","alsoKnownAs":["at://${name(did)}.test"],
               "verificationMethod":[{"id":"$did#atproto_kem","type":"Multikey","controller":"$did",
                 "publicKeyMultibase":"${kem(did).multikey}"}],
               "service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"http://${name(did)}pds.test"}]}"""
        )

    /**
     * The ops after `since`, values and all unless `excludeValues`, and the head as the commit, with the `Atproto-Space-Mac` for it under
     * [macMode] (none when there's no commit).
     */
    private suspend fun listRepoOps(params: Parameters, wantMac: String): Pair<JsonObject, String?> {
        val space = SpaceUri(params["space"]!!)
        val repo = Did(params["repo"]!!)
        val since = params["since"]
        val held = repo(space)
        val commit =
            held.head?.let {
                val data = if (space in wrongData) MessageDigest.getInstance("SHA-256").digest(it.toByteArray()) else held.hash.digest()
                Space.ListRepoOps.Commit(ver = 1, repo = repo, space = commitSpace ?: space, rev = it, data = LexBytes.of(data))
            }
        val mac = commit?.let {
            when (macMode) {
                MacMode.VALID -> SpaceMacHost.commitMacHeader(kem(repo.value), wantMac, it)
                MacMode.WRONG_KEY -> SpaceMacHost.commitMacHeader(SpaceMacHost.generateKemKey(), wantMac, it)
                MacMode.OMITTED -> null
            }
        }
        return buildJsonObject {
            put(
                "ops",
                buildJsonArray {
                    held.log
                        .filter { since == null || it.rev > since }
                        .filter { op -> compactedTo[space]?.let { op.rev > it } ?: true }
                        .forEach { op ->
                            add(
                                buildJsonObject {
                                    put("rev", op.rev)
                                    put("collection", op.collection)
                                    put("rkey", op.rkey)
                                    put("cid", op.cid)
                                    put("prev", op.prev)
                                    if (params["excludeValues"] != "true" && op.value != null) put("value", op.value)
                                }
                            )
                        }
                },
            )
            commit?.let { put("commit", AtJson.encodeToJsonElement(Space.ListRepoOps.Commit.serializer(), it)) }
        } to mac
    }

    /** Each record of `collection`, or of every collection when there's none, as it stands, in path order. */
    private fun listRecords(params: Parameters): JsonObject {
        val space = SpaceUri(params["space"]!!)
        val collection = params["collection"]
        val current = repo(space).records.filterKeys { collection == null || it.substringBeforeLast('/') == collection }
        return buildJsonObject {
            put(
                "records",
                buildJsonArray {
                    current.forEach { (path, record) ->
                        add(
                            buildJsonObject {
                                put("collection", path.substringBeforeLast('/'))
                                put("rkey", path.substringAfterLast('/'))
                                put("cid", record.cid)
                                put("value", record.value)
                            }
                        )
                    }
                },
            )
        }
    }

    private fun name(did: String) = did.substringAfterLast(':')

    private fun body(request: HttpRequestData): JsonObject = AtJson.parseToJsonElement((request.body as TextContent).text).jsonObject

    private fun MockRequestHandleScope.json(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        extraHeaders: List<Pair<String, String>> = emptyList(),
    ): HttpResponseData {
        val headers = (listOf(HttpHeaders.ContentType to "application/json") + extraHeaders).map { (k, v) -> k to listOf(v) }
        return respond(body, status, headersOf(*headers.toTypedArray()))
    }

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

        /** Every P-256 `did:key` starts so (the `0x1200` multicodec in base58btc). */
        const val P256_DID_KEY = "did:key:zDn"
    }
}
