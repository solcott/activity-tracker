package io.github.solcott.activitytracker.appview.auth

import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.bouncycastle.crypto.signers.ECDSASigner

/**
 * Verifies inter-service auth JWTs: issued by the user's PDS via `getServiceAuth` (direct calls) or attached by the PDS when proxying
 * (`atproto-proxy`). Returns the authenticated user's DID. Tokens must be addressed to [serviceDid] or one of [extraAudiences].
 */
class ServiceAuthVerifier(
    serviceDid: String,
    private val keys: suspend (Did) -> String?,
    private val clock: Clock = Clock.System,
    extraAudiences: Set<String> = emptySet(),
) {
    constructor(
        serviceDid: String,
        resolver: IdentityResolver,
        extraAudiences: Set<String> = emptySet(),
    ) : this(serviceDid, { did -> resolver.resolveDid(did).signingKey }, extraAudiences = extraAudiences)

    private val audiences = (extraAudiences + serviceDid).flatMap { listOf(it, "$it#activity_appview") }.toSet()

    private val keyCache = ConcurrentHashMap<Did, SigningKey>()

    suspend fun verify(token: String, nsid: String): Did {
        val parts = token.split('.')
        if (parts.size != 3) throw AuthException("Malformed JWT")
        val header = decodeJson(parts[0])
        val payload = decodeJson(parts[1])

        val iss = payload.string("iss") ?: throw AuthException("Missing iss")
        val did = runCatching {
            Did(iss.substringBefore('#'))
        }
            .getOrElse { throw AuthException("Bad iss") }
        val aud = payload.string("aud")
        if (aud !in audiences) throw AuthException("Wrong audience")
        val exp = payload["exp"]?.jsonPrimitive?.longOrNull ?: throw AuthException("Missing exp")
        if (exp < clock.now().epochSeconds) throw AuthException("Token expired")
        payload.string("lxm")?.let { if (it != nsid) throw AuthException("Token not valid for $nsid") }

        val signingInput = "${parts[0]}.${parts[1]}".toByteArray()
        val signature = runCatching {
            Base64.getUrlDecoder().decode(parts[2])
        }
            .getOrElse { throw AuthException("Bad signature") }
        val alg = header.string("alg")

        // Retry once with a freshly resolved key in case the account rotated its signing key.
        for (fresh in listOf(false, true)) {
            val key = signingKey(did, fresh) ?: throw AuthException("No signing key for $did")
            if (alg != key.curve.jwtAlg) {
                if (fresh) throw AuthException("Algorithm $alg does not match key")
                continue
            }
            if (verifySignature(key, signingInput, signature)) return did
        }
        throw AuthException("Invalid signature")
    }

    private suspend fun signingKey(did: Did, fresh: Boolean): SigningKey? {
        if (!fresh)
            keyCache[did]?.let {
                return it
            }
        val multibase = keys(did) ?: return null
        return Multikey.decode(multibase).also { keyCache[did] = it }
    }

    private fun verifySignature(key: SigningKey, data: ByteArray, signature: ByteArray): Boolean {
        if (signature.size != 64) return false
        val r = BigInteger(1, signature.copyOfRange(0, 32))
        val s = BigInteger(1, signature.copyOfRange(32, 64))
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return ECDSASigner().apply { init(false, key.params) }.verifySignature(digest, r, s)
    }

    private fun decodeJson(segment: String): JsonObject = runCatching {
        AtJson.parseToJsonElement(Base64.getUrlDecoder().decode(segment).decodeToString()).jsonObject
    }
        .getOrElse { throw AuthException("Malformed JWT") }

    private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.content
}

class AuthException(message: String) : Exception(message)
