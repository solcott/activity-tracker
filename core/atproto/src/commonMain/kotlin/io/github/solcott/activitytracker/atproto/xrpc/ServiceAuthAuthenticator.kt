package io.github.solcott.activitytracker.atproto.xrpc

import io.github.solcott.activitytracker.lexicon.atproto.GetServiceAuth
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Authenticates direct calls to the AppView with short-lived service-auth JWTs minted by the user's PDS
 * (`com.atproto.server.getServiceAuth`), bound to the AppView DID and the method NSID. [pds] must be an authenticated client for the
 * signed-in account; null means anonymous reads.
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
            tokens[nsid]?.takeIf { (_, exp) -> exp - 10.seconds > clock.now() }?.first
                ?: run {
                    val exp = clock.now() + TOKEN_LIFETIME
                    val minted =
                        client
                            .query(
                                GetServiceAuth.method,
                                GetServiceAuth.Params(
                                    aud = appViewDid,
                                    lxm = nsid,
                                    exp = exp.epochSeconds,
                                ),
                            )
                            .token
                    tokens[nsid] = minted to exp
                    minted
                }
        }
        request.header(HttpHeaders.Authorization, "Bearer $token")
    }

    override suspend fun shouldRetry(response: HttpResponse): Boolean {
        if (response.status.value != HttpStatusCode.Unauthorized.value) return false
        mutex.withLock { tokens.clear() }
        return true
    }

    fun clear() {
        tokens.clear()
    }

    private companion object {
        val TOKEN_LIFETIME = 60.seconds
    }
}
