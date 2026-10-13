package io.github.solcott.activitytracker.atproto.space

import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.crypto.jwtPayload
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.xrpc.RequestAuthenticator
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Credentials for reading other athletes' spaces, one per space, for one signed-in viewer. Getting one takes a single-use delegation token
 * from the viewer's PDS ([viewerPds]), exchanged at the PDS of the space's authority with a request signed by the session's binding key
 * ([newKey], created on first use); the credential is then bound to that key. Each is reused until [REFRESH_MARGIN] before it expires, and
 * concurrent callers share one exchange.
 *
 * A refused exchange (`UserNotAuthorized` and friends) throws and is never cached, so a new follow counts on the next read. Everything is
 * in memory and lives as long as the session.
 */
class SpaceCredentials(
    private val viewerPds: XrpcClient,
    private val http: HttpClient,
    private val identity: IdentityResolver,
    private val newKey: suspend () -> DpopKey = DpopKey::generate,
    private val clock: Clock = Clock.System,
) {
    class Credential(val token: String, val expiresAt: Instant)

    private val mutex = Mutex()
    private val cache = mutableMapOf<SpaceUri, Credential>()
    private val inFlight = mutableMapOf<SpaceUri, CompletableDeferred<Credential>>()
    private val pdsUrls = mutableMapOf<Did, String>()

    private var key: DpopKey? = null

    /** The key credentials are bound to; credentialed reads are signed with it. In memory only, like the credentials. */
    suspend fun bindingKey(): DpopKey = mutex.withLock { key ?: newKey().also { key = it } }

    suspend fun get(space: SpaceUri): Credential {
        var credential: Credential? = null
        while (credential == null) {
            var owner = false
            val (cached, pending) =
                mutex.withLock {
                    val fresh = cache[space]?.takeIf { it.expiresAt - REFRESH_MARGIN > clock.now() }
                    fresh to
                        (if (fresh == null) inFlight.getOrPut(space) { CompletableDeferred<Credential>().also { owner = true } } else null)
                }
            credential =
                when {
                    cached != null -> cached
                    owner -> exchangeFor(space, checkNotNull(pending))
                    else -> awaitExchange(checkNotNull(pending))
                }
        }
        return credential
    }

    /** Drops [space]'s credential, after the PDS reported it expired or revoked. */
    suspend fun invalidate(space: SpaceUri) = mutex.withLock { cache.remove(space) }

    /** The authority's PDS base URL, resolved from its DID document once per session. */
    suspend fun pdsUrl(did: Did): String =
        mutex.withLock { pdsUrls[did] } ?: identity.resolve(did.value).pdsUrl.also { url -> mutex.withLock { pdsUrls[did] = url } }

    /** Runs the exchange for [space] and hands its outcome, failure and cancellation included, to everyone waiting on [pending]. */
    private suspend fun exchangeFor(space: SpaceUri, pending: CompletableDeferred<Credential>): Credential {
        val result = runCatching { exchange(space) }
        withContext(NonCancellable) {
            mutex.withLock {
                result.onSuccess { cache[space] = it }
                inFlight.remove(space)
            }
        }
        result.fold(pending::complete, pending::completeExceptionally)
        return result.getOrThrow()
    }

    /** Another caller's exchange; null if that caller was cancelled, so this one starts over. */
    private suspend fun awaitExchange(pending: CompletableDeferred<Credential>): Credential? =
        try {
            pending.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            if (!pending.isCancelled) throw e
            null
        }

    private suspend fun exchange(space: SpaceUri): Credential {
        val delegation = viewerPds.query(Space.GetDelegationToken.method, Space.GetDelegationToken.Params(space)).token
        val authority = XrpcClient(http, pdsUrl(space.authority), DelegationAuthenticator(delegation, bindingKey()))
        val token = authority.procedure(Space.GetSpaceCredential.method, Space.GetSpaceCredential.Input(space)).credential
        val exp = jwtPayload(token)["exp"]?.jsonPrimitive?.long ?: error("Space credential has no exp")
        return Credential(token, Instant.fromEpochSeconds(exp))
    }

    /** `Authorization: Bearer <delegation token>`, signed with the key the credential will be bound to. */
    private class DelegationAuthenticator(private val delegation: String, private val key: DpopKey) : RequestAuthenticator {
        override suspend fun authenticate(request: HttpRequestBuilder, nsid: String) {
            val authorization = "Bearer $delegation"
            request.header(HttpHeaders.Authorization, authorization)
            HttpMessageSignature.sign(key, authorization).forEach { (name, value) -> request.header(name, value) }
        }

        override suspend fun shouldRetry(response: HttpResponse) = false
    }

    private companion object {
        /** bulletin refreshes 5 s early too; the PDS allows 5 s of clock skew. */
        val REFRESH_MARGIN = 5.seconds
    }
}
