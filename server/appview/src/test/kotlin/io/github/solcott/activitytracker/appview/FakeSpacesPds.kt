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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The Spaces side of an athlete's PDS, as the AppView's syncer sees it: `authorizeSpaceSync` (recording each `notify` registration it
 * carries), `listRepoOps` and `listRecords` for the authority's repo in each space. Every athlete is on `http://<name>pds.test`, resolved
 * through `http://plc.test`, whose DID document publishes a `#atproto_kem` key ([rotateKem] replaces it). `listRepoOps` answers as the Nov
 * 12 alpha does: it requires an `Accept-Signature` MAC request, answers `InvalidSpaceMac` without one and `RepoNotFound` for a repo never
 * written, and MACs the commit on every page, which [macMode] and [commitSpace] can spoil. The commit's `data` is the repo's real set hash
 * ([LtHash]), which [clear] and [migrate] change the way `clearRecords` and an account move do. Writes go straight into the op log; nothing
 * is sent to the AppView unless a test calls `notifySpaceUpdate` itself, and registrations are taken as confirmed.
 */
/** [clock] dates the credentials it issues, so pass the same clock to the [SpaceCredentials] reading them. */
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

    /** Spaces whose `authorizeSpaceSync` answers `SpaceDeleted`. */
    val deleted = mutableSetOf<SpaceUri>()

    /** Spaces whose `authorizeSpaceSync` answers `SpaceNotFound`: missing, or refusing the AppView. */
    val notFound = mutableSetOf<SpaceUri>()

    /**
     * Spaces whose `authorizeSpaceSync` answers with the given error, such as `RepoTakendown` for a suspended authority or
     * `UpstreamFailure` when the managing app can't be reached.
     */
    val credentialErrors = mutableMapOf<SpaceUri, String>()

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

        /** No MAC headers at all. */
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

    /** Every `authorizeSpaceSync` input that carried `notify`, in order: registrations and their renewals. */
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

    /** The current revision of [space]'s authority repo, for a `repoWrite` notice. */
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
            path == "/xrpc/com.atproto.space.authorizeSpaceSync" -> {
                val input = body(request)
                val space = SpaceUri(input.getValue("space").jsonPrimitive.content)
                val key = input["credentialKey"]?.jsonPrimitive?.content.orEmpty()
                when {
                    // The exchange is unauthenticated and binds the credential to a P-256 did:key.
                    request.headers[HttpHeaders.Authorization] != null || !key.startsWith(P256_DID_KEY) -> error("InvalidRequest")
                    // A deleted space answers so before anything else is checked.
                    space in deleted -> error("SpaceDeleted")
                    space in notFound -> error("SpaceNotFound")
                    space in credentialErrors -> error(credentialErrors.getValue(space))
                    else -> {
                        if (input["notify"] != null) registrations += input
                        val spaceRev = repo(space).head?.let { ""","spaceRev":"$it"""" }.orEmpty()
                        json("""{"credential":"${credential()}"$spaceRev}""")
                    }
                }
            }
            path == "/xrpc/com.atproto.space.listRepoOps" -> {
                val params = request.url.parameters
                val accept = request.headers[SpaceMac.ACCEPT_SIGNATURE] ?: return@run error("InvalidSpaceMac")
                val (answer, mac) = listRepoOps(params, accept) ?: return@run error("RepoNotFound")
                // The write lands after the log was read, as a notification racing a pull would.
                if (params["since"] != null) duringNextPull?.also { duringNextPull = null }?.invoke()
                json(answer.toString(), extraHeaders = mac.orEmpty().toList())
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
               "service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"http://${name(did)}pds.test"}]}""",
        )

    /**
     * The ops after `since`, values and all unless `excludeValues`, and the head as the commit, with its MAC headers under [macMode]; null
     * for a repo never written.
     */
    private suspend fun listRepoOps(params: Parameters, accept: String): Pair<JsonObject, Map<String, String>?>? {
        val space = SpaceUri(params["space"]!!)
        val repo = Did(params["repo"]!!)
        val since = params["since"]
        val held = repo(space)
        val head = held.head ?: return null
        val data = if (space in wrongData) MessageDigest.getInstance("SHA-256").digest(head.toByteArray()) else held.hash.digest()
        val commit = Space.ListRepoOps.Commit(ver = 1, repo = repo, space = commitSpace ?: space, rev = head, data = LexBytes.of(data))
        val mac =
            when (macMode) {
                MacMode.VALID -> SpaceMacHost.commitMacHeaders(kem(repo.value), accept, commit)
                MacMode.WRONG_KEY -> SpaceMacHost.commitMacHeaders(SpaceMacHost.generateKemKey(), accept, commit)
                MacMode.OMITTED -> null
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
                                },
                            )
                        }
                },
            )
            put("commit", AtJson.encodeToJsonElement(Space.ListRepoOps.Commit.serializer(), commit))
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
                            },
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
        val exp = clock.now().epochSeconds + CREDENTIAL_SECONDS
        return b64.encodeToString("""{"alg":"ES256K"}""".toByteArray()) +
            "." +
            b64.encodeToString("""{"exp":$exp}""".toByteArray()) +
            ".c2ln"
    }

    private companion object {
        /** Digits after the `3m` prefix: fixed width, so revisions sort as strings like TIDs. */
        const val REV_DIGITS = 11

        /** The reference host's credential lifetime: a registration lasts as long. */
        const val CREDENTIAL_SECONDS = 600

        /** Every P-256 `did:key` starts so (the `0x1200` multicodec in base58btc). */
        const val P256_DID_KEY = "did:key:zDn"
    }
}
