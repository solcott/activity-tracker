package io.github.solcott.activitytracker.appview.net

import io.github.solcott.activitytracker.appview.boundedCache
import io.github.solcott.activitytracker.atproto.identity.IdentityRateLimitedException
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.atproto.DidDocument
import io.ktor.client.HttpClient
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The AppView's identity resolver (identity/outbound-requests): each DID document is kept for [ttl] after it's fetched, so the PLC
 * directory is asked about an account at most that often by every reader together, and dropped early by [invalidate] when an `#identity`
 * event names the account. [refreshDid] fetches past the cache and keeps what it got. Every `did:plc` fetch also goes through [pacing]:
 * once the directory answers 429, none is sent until its pause ends.
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
    /** Bounded, since any DID can be asked about: every service-auth issuer, every photo's athlete. */
    private val documents = boundedCache<Did, DidDocument>(MAX_DOCUMENTS, ttl, clock)

    /**
     * Each invalidation's turn ([invalidations] counts them) by the DID it named, so a fetch that began before one never caches the
     * document it read: it may be the one the `#identity` event replaced. Kept as long as a fetch could still be running.
     */
    private val invalidated = boundedCache<Did, Long>(MAX_DOCUMENTS, ttl, clock)
    private val invalidations = AtomicLong()

    override suspend fun resolveDid(did: Did): DidDocument = documents.getIfPresent(did) ?: refreshDid(did)

    override suspend fun refreshDid(did: Did): DidDocument {
        val fromPlc = did.method == "plc"
        val started = invalidations.get()
        if (fromPlc) pacing.pausedUntil()?.let { throw IdentityRateLimitedException(did, it) }
        val document =
            try {
                fetchDid(did)
            } catch (e: IdentityRateLimitedException) {
                if (fromPlc) pacing.pause(e.retryAt)
                throw e
            }
        if ((invalidated.getIfPresent(did) ?: Long.MIN_VALUE) <= started) documents.put(did, document)
        return document
    }

    /** Drops [did]'s document, so the next read fetches it again, and keeps a fetch already under way from putting it back. */
    fun invalidate(did: Did) {
        invalidated.put(did, invalidations.incrementAndGet())
        documents.invalidate(did)
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
