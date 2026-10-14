package io.github.solcott.activitytracker.atproto.space

import io.github.solcott.activitytracker.atproto.crypto.base64Url
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Where [SpaceCredentials] gets the single-use delegation token it exchanges for a space credential: one naming the reader, addressed to
 * the space's authority.
 */
fun interface DelegationTokenSource {
    suspend fun token(space: SpaceUri): String

    companion object {
        /** A signed-in viewer's: their PDS mints the token with `getDelegationToken`. */
        fun pds(viewerPds: XrpcClient) = DelegationTokenSource { space ->
            viewerPds.query(Space.GetDelegationToken.method, Space.GetDelegationToken.Params(space)).token
        }
    }
}

/**
 * A service's own delegation tokens, signed with the key its DID document publishes as `#atproto` (how the AppView reads athletes' tier
 * spaces). Shaped as `@atproto/space`'s `createSpaceToken('delegation', ...)`: `typ` `atproto-space-delegation+jwt`, `kid` `#atproto`,
 * `aud` the authority's `#atproto_space_host`, 60 s and a random `jti`, since the PDS refuses a token twice.
 */
class SignedDelegationTokens(
    private val issuer: Did,
    /** The JWT `alg` of [sign]'s signatures, e.g. `ES256`. */
    private val alg: String,
    /** Signs the JWT signing input; a raw (`r || s`) low-S ECDSA signature. */
    private val sign: suspend (ByteArray) -> ByteArray,
    private val clock: Clock = Clock.System,
    private val random: Random = Random.Default,
) : DelegationTokenSource {
    override suspend fun token(space: SpaceUri): String {
        val header = buildJsonObject {
            put("alg", alg)
            put("typ", TYP)
            put("kid", KID)
        }
        val iat = clock.now().epochSeconds
        val payload = buildJsonObject {
            put("iss", issuer.value)
            put("sub", space.value)
            put("aud", "${space.authority.value}$SPACE_HOST_FRAGMENT")
            put("iat", iat)
            put("exp", iat + LIFETIME.inWholeSeconds)
            put("jti", random.nextBytes(JTI_BYTES).toHexString())
        }
        val input = "${header.toString().encodeToByteArray().base64Url()}.${payload.toString().encodeToByteArray().base64Url()}"
        return "$input.${sign(input.encodeToByteArray()).base64Url()}"
    }

    companion object {
        const val TYP = "atproto-space-delegation+jwt"
        const val KID = "#atproto"
        const val SPACE_HOST_FRAGMENT = "#atproto_space_host"
        val LIFETIME = 60.seconds
        private const val JTI_BYTES = 16
    }
}
