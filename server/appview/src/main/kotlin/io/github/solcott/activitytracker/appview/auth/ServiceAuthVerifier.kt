package io.github.solcott.activitytracker.appview.auth

import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.identity.isResolvableDid
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Verifies inter-service auth JWTs: issued by the user's PDS via `getServiceAuth` (direct calls), attached by the PDS when proxying
 * (`atproto-proxy`), or sent by a space authority's PDS to the managing app. Returns the issuing account's DID. Tokens must be addressed to
 * one of [audiences]: keep each purpose in its own verifier, so a token minted for one can't be replayed on another's routes.
 */
class ServiceAuthVerifier(
    private val audiences: Set<String>,
    private val keys: suspend (Did) -> String?,
    private val clock: Clock = Clock.System,
) {
    /** The AppView's own routes: tokens addressed to [serviceDid] or one of [extraAudiences]. */
    constructor(
        serviceDid: String,
        keys: suspend (Did) -> String?,
        clock: Clock = Clock.System,
        extraAudiences: Set<String> = emptySet(),
    ) : this(appViewAudiences(serviceDid, extraAudiences), keys, clock)

    constructor(
        serviceDid: String,
        resolver: IdentityResolver,
        extraAudiences: Set<String> = emptySet(),
    ) : this(serviceDid, { did -> resolver.resolveDid(did).signingKey }, extraAudiences = extraAudiences)

    private val keyCache = ConcurrentHashMap<Did, SigningKey>()
    private val failed = FailedLookups(clock)

    /** A verifier with the same keys and clock that accepts only [audiences]. */
    fun forAudiences(audiences: Set<String>) = ServiceAuthVerifier(audiences, keys, clock)

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
        val did = iss?.let { runCatching { Did(it.substringBefore('#')) }.getOrNull() }?.takeIf { isResolvableDid(it.value) }
        val exp = payload["exp"]?.jsonPrimitive?.longOrNull
        val lxm = payload.string("lxm")
        val failure =
            when {
                iss == null -> "Missing iss"
                did == null -> "Bad iss"
                payload.string("aud") !in audiences -> "Wrong audience"
                exp == null -> "Missing exp"
                exp < clock.now().epochSeconds -> "Token expired"
                lxm != nsid -> "Token not valid for $nsid"
                else -> return did
            }
        throw AuthException(failure)
    }

    private suspend fun verifySigned(did: Did, alg: String?, data: ByteArray, signature: ByteArray) {
        // Retry once with a freshly resolved key in case the account rotated its signing key.
        for (fresh in listOf(false, true)) {
            val key = signingKey(did, fresh)
            if (alg == key.curve.jwtAlg && key.verifies(data, signature)) return
            if (fresh && alg != key.curve.jwtAlg) throw AuthException("Algorithm $alg does not match key")
        }
        throw AuthException("Invalid signature")
    }

    /** [did]'s key, from the cache unless [fresh]. A DID whose lookup failed recently is refused without another lookup. */
    private suspend fun signingKey(did: Did, fresh: Boolean): SigningKey {
        keyCache[did]
            ?.takeUnless { fresh }
            ?.let {
                return it
            }
        if (failed.contains(did)) throw AuthException("No signing key for $did")
        val key = runSuspendCatching { keys(did)?.let(Multikey::decode) }.getOrNull()
        if (key == null) {
            failed.add(did)
            throw AuthException("No signing key for $did")
        }
        failed.remove(did)
        keyCache[did] = key
        return key
    }

    private fun decodeJson(segment: String): JsonObject = runCatching {
        AtJson.parseToJsonElement(Base64.getUrlDecoder().decode(segment).decodeToString()).jsonObject
    }
        .getOrElse { throw AuthException("Malformed JWT") }

    private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.content

    companion object {
        /** Each of [serviceDid] and [extraAudiences], bare or with the `#activity_appview` service fragment. */
        fun appViewAudiences(serviceDid: String, extraAudiences: Set<String> = emptySet()): Set<String> =
            (extraAudiences + serviceDid).flatMap { listOf(it, "$it#activity_appview") }.toSet()

        private const val JWT_PARTS = 3
    }
}

class AuthException(message: String) : Exception(message)

/** DIDs whose signing key couldn't be looked up, each remembered for [ttl], at most [capacity] of them (the oldest go first). */
private class FailedLookups(private val clock: Clock, private val ttl: Duration = 5.minutes, capacity: Int = 10_000) {
    private val failedAt =
        object : LinkedHashMap<Did, Instant>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Did, Instant>?) = size > capacity
        }

    fun contains(did: Did): Boolean =
        synchronized(failedAt) {
            val at = failedAt[did] ?: return false
            val recent = clock.now() - at < ttl
            if (!recent) failedAt.remove(did)
            recent
        }

    fun add(did: Did) {
        synchronized(failedAt) {
            failedAt.remove(did)
            failedAt[did] = clock.now()
        }
    }

    fun remove(did: Did) {
        synchronized(failedAt) { failedAt.remove(did) }
    }
}
