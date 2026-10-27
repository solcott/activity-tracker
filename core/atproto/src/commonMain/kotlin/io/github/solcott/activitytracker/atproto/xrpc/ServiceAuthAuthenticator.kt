package io.github.solcott.activitytracker.atproto.xrpc

import io.github.solcott.activitytracker.atproto.crypto.jwtPayload
import io.github.solcott.activitytracker.lexicon.atproto.GetServiceAuth
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Authenticates direct calls to the AppView with short-lived service-auth JWTs minted by the user's PDS
 * (`com.atproto.server.getServiceAuth`), bound to the AppView DID and the method NSID. [pds] must be an authenticated client for the
 * signed-in account; null means anonymous reads.
 *
 * The PDS sets each token's expiry by its own clock, so a device clock that's off never gets a token refused. A token is reused for the
 * lifetime it states (`exp - iat`, both the PDS's), counted on [clock] from when it arrived, so the offset cancels out. A token whose
 * claims can't be read is sent once and not reused: the AppView decides whether it's good.
 */
class ServiceAuthAuthenticator(
    private val pds: () -> XrpcClient?,
    private val appViewDid: String,
    private val clock: Clock = Clock.System,
) : RequestAuthenticator {
    private val mutex = Mutex()
    private val tokens = mutableMapOf<String, Pair<String, Instant>>()

    override suspend fun authenticate(request: HttpRequestBuilder, nsid: String) {
        val client = pds() ?: return
        val token = mutex.withLock {
            tokens[nsid]?.takeIf { (_, expiresAt) -> expiresAt > clock.now() }?.first
                ?: run {
                    val minted = client.query(GetServiceAuth.method, GetServiceAuth.Params(aud = appViewDid, lxm = nsid)).token
                    val lifetime = lifetimeOf(minted)
                    if (lifetime != null && lifetime > MARGIN) tokens[nsid] = minted to clock.now() + lifetime - MARGIN
                    else tokens.remove(nsid)
                    minted
                }
        }
        request.header(HttpHeaders.Authorization, "Bearer $token")
    }

    /** On a 401, forgets the refused method's token so the retry mints a new one. Other methods' tokens stay. */
    override suspend fun shouldRetry(response: HttpResponse): Boolean {
        if (response.status.value != HttpStatusCode.Unauthorized.value) return false
        val nsid = response.request.url.encodedPath.substringAfterLast("/xrpc/")
        mutex.withLock { tokens.remove(nsid) }
        return true
    }

    /** The token's `exp - iat`, or null when it isn't a JWT carrying both with `exp` after `iat`. */
    private fun lifetimeOf(token: String): Duration? =
        try {
            val claims = jwtPayload(token)
            val iat = claims["iat"]?.jsonPrimitive?.longOrNull
            val exp = claims["exp"]?.jsonPrimitive?.longOrNull
            if (iat != null && exp != null && exp > iat) (exp - iat).seconds else null
        } catch (_: IllegalArgumentException) {
            // Not a JWT, or its payload isn't JSON (SerializationException is one too).
            null
        }

    private companion object {
        /** How long before a token's stated end it's no longer reused. */
        val MARGIN = 10.seconds
    }
}
