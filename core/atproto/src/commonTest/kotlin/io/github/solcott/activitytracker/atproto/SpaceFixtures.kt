package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.space.SpaceMac
import io.github.solcott.activitytracker.atproto.space.SpaceMacHost
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A viewer (bob, on `pds.test`) and an authority (alice, on `alicepds.test`) for Spaces tests. Credentials are fake JWTs whose payload
 * carries only `exp`; the client never verifies them. Alice's DID document publishes a `#atproto_kem` key ([aliceKem], replaced by
 * [rotateKem]), so [respondRecord] can answer a MAC'd `getRecord` as her PDS would.
 */
class SpaceFixtures {
    val network = MockNetwork()
    val clock = MutableClock(Instant.fromEpochSeconds(1_000_000))
    val bob = Did("did:plc:bob")
    val alice = Did("did:plc:alice")
    val aliceSpace = SpaceUri.of(alice, Nsid.SPACE_ATHLETE, "mutuals")
    val viewerPds = XrpcClient(network.client, "http://pds.test")
    val identity = IdentityResolver(network.client, "http://plc.test", null)

    var delegations = 0
    var exchanges = 0

    /** Seconds each new credential lasts, from [clock]'s now. */
    var credentialLifetime = 600L

    private var kem: SpaceMacHost.KemKey? = null

    /** The KEM key alice's DID document publishes now. */
    suspend fun aliceKem(): SpaceMacHost.KemKey = kem ?: SpaceMacHost.generateKemKey().also { kem = it }

    /** Alice's KEM key rotated: her DID document publishes a new one from now on. */
    suspend fun rotateKem(): SpaceMacHost.KemKey = SpaceMacHost.generateKemKey().also { kem = it }

    init {
        network.on("plc.test/${alice.value}") {
            respondJson(
                """{"id":"${alice.value}","alsoKnownAs":["at://alice.test"],
                   "verificationMethod":[{"id":"${alice.value}#atproto_kem","type":"Multikey","controller":"${alice.value}",
                     "publicKeyMultibase":"${aliceKem().multikey}"}],
                   "service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"http://alicepds.test"}]}"""
            )
        }
        network.on("pds.test/xrpc/com.atproto.space.getDelegationToken") { respondJson("""{"token":"delegation-${++delegations}"}""") }
        network.on("alicepds.test/xrpc/com.atproto.space.getSpaceCredential") {
            exchanges++
            respondJson("""{"credential":"${credential(clock.now().epochSeconds + credentialLifetime, exchanges)}"}""")
        }
    }

    fun requestsTo(hostAndPath: String): List<HttpRequestData> = network.requests.filter { it.url.host + it.url.encodedPath == hostAndPath }

    /**
     * Alice's PDS answering `getRecord` [request] with [value], at the URI and CID the request names. The MAC is computed with [macKem]
     * (her current key by default; another key stands in for a stale or forged one), or left out when [withMac] is false.
     */
    suspend fun MockRequestHandleScope.respondRecord(
        request: HttpRequestData,
        value: String,
        macKem: SpaceMacHost.KemKey? = null,
        withMac: Boolean = true,
        uri: AtUri = recordUri(request),
    ): HttpResponseData {
        val mac =
            if (withMac) SpaceMacHost.recordMacHeader(macKem ?: aliceKem(), request.headers[SpaceMac.WANT_MAC_HEADER], uri, RECORD_CID)
            else null
        val headers = listOfNotNull(mac?.let { SpaceMac.MAC_HEADER to it })
        return respondJson("""{"uri":"$uri","cid":"${RECORD_CID.value}","value":$value}""", extraHeaders = headers.toTypedArray())
    }

    /** `<space>/<repo>/<collection>/<rkey>`, the URI a space `getRecord` answers with. */
    fun recordUri(request: HttpRequestData): AtUri {
        val p = request.url.parameters
        return AtUri("${p["space"]}/${p["repo"]}/${p["collection"]}/${p["rkey"]}")
    }

    companion object {
        val RECORD_CID = Cid(SpaceMacVectors.CID)

        fun credential(exp: Long, serial: Int = 1): String {
            val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            val header = b64.encode("""{"alg":"ES256K","typ":"atproto-space-credential+jwt"}""".encodeToByteArray())
            val payload = b64.encode("""{"exp":$exp,"n":$serial}""".encodeToByteArray())
            return "$header.$payload.c2ln"
        }
    }
}

class MutableClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}
