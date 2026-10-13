package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.request.HttpRequestData
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A viewer (bob, on `pds.test`) and an authority (alice, on `alicepds.test`) for Spaces tests. Credentials are fake JWTs whose payload
 * carries only `exp`; the client never verifies them.
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

    init {
        network.on("plc.test/${alice.value}") {
            respondJson(
                """{"id":"${alice.value}","alsoKnownAs":["at://alice.test"],
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

    companion object {
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
