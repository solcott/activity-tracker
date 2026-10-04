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
        if (parts.size != JWT_PARTS) throw AuthException("Malformed JWT")
        val (header, payload) = parts.take(2).map(::decodeJson)
        val did = issuer(payload, nsid)
        val signature = runCatching { Base64.getUrlDecoder().decode(parts[2]) }.getOrElse { throw AuthException("Bad signature") }
        verifySigned(did, header.string("alg"), "${parts[0]}.${parts[1]}".toByteArray(), signature)
        return did
    }

    /** Checks the claims and returns the issuing account. */
    private fun issuer(payload: JsonObject, nsid: String): Did {
        val iss = payload.string("iss")
        val did = iss?.let { runCatching { Did(it.substringBefore('#')) }.getOrNull() }
        val exp = payload["exp"]?.jsonPrimitive?.longOrNull
        val lxm = payload.string("lxm")
        val failure =
            when {
                iss == null -> "Missing iss"
                did == null -> "Bad iss"
                payload.string("aud") !in audiences -> "Wrong audience"
                exp == null -> "Missing exp"
                exp < clock.now().epochSeconds -> "Token expired"
                lxm != null && lxm != nsid -> "Token not valid for $nsid"
                else -> return did
            }
        throw AuthException(failure)
    }

    private suspend fun verifySigned(did: Did, alg: String?, data: ByteArray, signature: ByteArray) {
        // Retry once with a freshly resolved key in case the account rotated its signing key.
        for (fresh in listOf(false, true)) {
            val key = signingKey(did, fresh)
            if (alg == key.curve.jwtAlg && verifySignature(key, data, signature)) return
            if (fresh && alg != key.curve.jwtAlg) throw AuthException("Algorithm $alg does not match key")
        }
        throw AuthException("Invalid signature")
    }

    private suspend fun signingKey(did: Did, fresh: Boolean): SigningKey =
        keyCache[did]?.takeUnless { fresh }
            ?: keys(did)?.let(Multikey::decode)?.also { keyCache[did] = it }
            ?: throw AuthException("No signing key for $did")

    private fun verifySignature(key: SigningKey, data: ByteArray, signature: ByteArray): Boolean {
        if (signature.size != SIGNATURE_BYTES) return false
        val r = BigInteger(1, signature.copyOfRange(0, SIGNATURE_BYTES / 2))
        val s = BigInteger(1, signature.copyOfRange(SIGNATURE_BYTES / 2, SIGNATURE_BYTES))
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return ECDSASigner().apply { init(false, key.params) }.verifySignature(digest, r, s)
    }

    private fun decodeJson(segment: String): JsonObject = runCatching {
        AtJson.parseToJsonElement(Base64.getUrlDecoder().decode(segment).decodeToString()).jsonObject
    }
        .getOrElse { throw AuthException("Malformed JWT") }

    private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.content

    private companion object {
        const val JWT_PARTS = 3

        /** Compact (r || s) ECDSA signature: two 32-byte integers. */
        const val SIGNATURE_BYTES = 64
    }
}

class AuthException(message: String) : Exception(message)
