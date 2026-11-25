package io.github.solcott.activitytracker.appview.net

import io.github.solcott.activitytracker.appview.boundedAsyncCache
import io.github.solcott.activitytracker.atproto.identity.IdentityRateLimitedException
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.atproto.DidDocument
import io.ktor.client.HttpClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.future.future

/**
 * The AppView's identity resolver (identity/outbound-requests): each DID document is kept for [ttl] after it's fetched, so the PLC
 * directory is asked about an account at most that often by every reader together (readers asking while it's fetched share the fetch), and
 * dropped early by [invalidate] when an `#identity` event names the account. [refreshDid] fetches past the cache and keeps what it got.
 * Every `did:plc` fetch also goes through [pacing]: once the directory answers 429, none is sent until its pause ends.
 */
@Suppress("LongParameterList") // The resolver's own four, then the cache's: pacing, TTL and a test clock.
class CachingIdentityResolver(
    http: HttpClient,
    plcUrl: String,
    handleResolverUrl: String?,
    untrusted: HttpClient = http,
    private val pacing: PlcPacing = PlcPacing(),
    ttl: Duration = DEFAULT_TTL,
    clock: Clock = Clock.System,
) : IdentityResolver(http, plcUrl, handleResolverUrl, untrusted) {
    /**
     * Bounded, since any DID can be asked about: every service-auth issuer, every photo's athlete. It holds each document's fetch, so
     * readers asking about an account at once share one request, and [invalidate] dropping a fetch under way keeps the document it read
     * out: it may be the one the `#identity` event replaced.
     */
    private val documents = boundedAsyncCache<Did, DidDocument>(MAX_DOCUMENTS, ttl, clock)

    override suspend fun resolveDid(did: Did): DidDocument {
        documents.getIfPresent(did)?.let {
            return it.asDeferred().await()
        }
        checkPacing(did)
        val context = currentCoroutineContext()
        return documents.get(did) { key, _ -> fetchShared(key, context) }.asDeferred().await()
    }

    override suspend fun refreshDid(did: Did): DidDocument {
        checkPacing(did)
        val fetch = fetchShared(did, currentCoroutineContext())
        documents.put(did, fetch)
        return fetch.asDeferred().await()
    }

    /** Drops [did]'s document, or the fetch under way for it, so the next read fetches it again. */
    fun invalidate(did: Did) = documents.synchronous().invalidate(did)

    private fun checkPacing(did: Did) {
        if (did.method == "plc") pacing.pausedUntil()?.let { throw IdentityRateLimitedException(did, it) }
    }

    /**
     * Fetches [did]'s document in a job of its own on the caller's dispatcher, so a reader that gives up doesn't cancel it for the others
     * waiting on it (they await it through `asDeferred`, which never cancels the future).
     */
    private fun fetchShared(did: Did, context: CoroutineContext): CompletableFuture<DidDocument> =
        CoroutineScope(context.minusKey(Job)).future {
            try {
                fetchDid(did)
            } catch (e: IdentityRateLimitedException) {
                if (did.method == "plc") pacing.pause(e.retryAt)
                throw e
            }
        }

    companion object {
        val DEFAULT_TTL = 10.minutes
        private const val MAX_DOCUMENTS = 50_000L
    }
}

/**
 * When the PLC directory last asked the AppView to slow down: the time its `Retry-After` named, else a minute on, never less than
 * [MIN_PAUSE] nor more than [MAX_PAUSE] from when it answered. Shared by every resolver that reads the same directory.
 */
class PlcPacing(private val clock: Clock = Clock.System) {
    private val until = AtomicReference<Instant?>(null)

    /** The end of the current pause, or null when the directory may be asked. */
    fun pausedUntil(): Instant? = until.get()?.takeIf { it > clock.now() }

    /** Pauses every `did:plc` fetch until [retryAt], within bounds. */
    fun pause(retryAt: Instant?) {
        val now = clock.now()
        val end = (retryAt ?: (now + DEFAULT_PAUSE)).coerceIn(now + MIN_PAUSE, now + MAX_PAUSE)
        until.updateAndGet { current -> if (current != null && current > end) current else end }
    }

    private companion object {
        val MIN_PAUSE = 10.seconds
        val DEFAULT_PAUSE = 1.minutes
        val MAX_PAUSE = 15.minutes
    }
}
