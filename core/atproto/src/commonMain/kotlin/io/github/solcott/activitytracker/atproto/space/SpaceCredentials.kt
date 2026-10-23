package io.github.solcott.activitytracker.atproto.space

import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.crypto.jwtPayload
import io.github.solcott.activitytracker.atproto.identity.IdentityException
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
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
 * Credentials for reading other athletes' spaces, one per space, for one reader: a signed-in viewer, or the AppView syncing athletes' tier
 * spaces. Getting one takes a single-use delegation token naming the reader ([delegation]: from the viewer's PDS, or signed by the AppView
 * itself), exchanged at the PDS of the space's authority together with a fresh P-256 key ([newKey]) the credential is then bound to, so no
 * two credentials share a key. Each is reused until [REFRESH_MARGIN] before it expires, and concurrent callers share one exchange.
 *
 * A refused exchange (`UserNotAuthorized` and friends) throws and is never cached, so a new follow counts on the next read. Everything is
 * in memory and lives as long as the session (or the AppView process).
 */
class SpaceCredentials(
    private val delegation: DelegationTokenSource,
    private val http: HttpClient,
    private val identity: IdentityResolver,
    private val newKey: suspend () -> DpopKey = DpopKey::generate,
    private val clock: Clock = Clock.System,
) {
    /** A signed-in viewer's credentials, with delegation tokens from their PDS. */
    constructor(
        viewerPds: XrpcClient,
        http: HttpClient,
        identity: IdentityResolver,
        newKey: suspend () -> DpopKey = DpopKey::generate,
        clock: Clock = Clock.System,
    ) : this(DelegationTokenSource.pds(viewerPds), http, identity, newKey, clock)

    /** A space credential and the key it's bound to; reads with it are signed with [key]. In memory only. */
    class Credential(val token: String, val expiresAt: Instant, val key: DpopKey)

    private val mutex = Mutex()
    private val cache = mutableMapOf<SpaceUri, Credential>()
    private val inFlight = mutableMapOf<SpaceUri, CompletableDeferred<Credential>>()
    private val pdsUrls = mutableMapOf<Did, String>()
    private val kemKeys = mutableMapOf<Did, String>()

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

    /**
     * [did]'s `#atproto_kem` key, which MAC'd reads of its repos agree a secret with: resolved once per session, or again with [refresh]
     * after a MAC failed (the key may have rotated). Throws when the DID document publishes none: that account can't serve credentialed
     * reads.
     */
    suspend fun kemKey(did: Did, refresh: Boolean = false): String {
        if (!refresh)
            mutex
                .withLock { kemKeys[did] }
                ?.let {
                    return it
                }
        val key = identity.resolveDid(did).kemKey ?: throw IdentityException("$did publishes no #atproto_kem key")
        mutex.withLock { kemKeys[did] = key }
        return key
    }

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

    /** An unauthenticated POST: the delegation token and the new key travel in the body. */
    private suspend fun exchange(space: SpaceUri): Credential {
        val key = newKey()
        val authority = XrpcClient(http, pdsUrl(space.authority))
        val input = Space.GetSpaceCredential.Input(space, credentialKid = key.didKey, delegationToken = delegation.token(space))
        val token = authority.procedure(Space.GetSpaceCredential.method, input).credential
        val exp = jwtPayload(token)["exp"]?.jsonPrimitive?.long ?: error("Space credential has no exp")
        return Credential(token, Instant.fromEpochSeconds(exp), key)
    }

    private companion object {
        /** bulletin refreshes 5 s early too; the PDS allows 5 s of clock skew. */
        val REFRESH_MARGIN = 5.seconds
    }
}
