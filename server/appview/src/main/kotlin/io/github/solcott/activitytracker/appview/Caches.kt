package io.github.solcott.activitytracker.appview

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * A cache of at most [maxSize] entries (the least useful go first), each kept for [ttl] after it's written when one is given. It's timed by
 * the system's monotonic clock, so a step of the wall clock can't stretch or cut a TTL, unless a test passes its own [clock] to move time.
 * Eviction runs on the calling thread, so the cache is within its bound whenever a call returns.
 */
internal fun <K : Any, V : Any> boundedCache(maxSize: Long, ttl: Duration? = null, clock: Clock = Clock.System): Cache<K, V> =
    Caffeine.newBuilder()
        .maximumSize(maxSize)
        .apply { if (ttl != null) expireAfterWrite(ttl.toJavaDuration()) }
        .ticker(if (clock == Clock.System) Ticker.systemTicker() else clock.ticker())
        .executor(Runnable::run)
        .build()

/** [clock] as Caffeine reads time: nanoseconds, here from the epoch. */
internal fun Clock.ticker(): Ticker = Ticker { now().toEpochMilliseconds() * NANOS_PER_MILLI }

private const val NANOS_PER_MILLI = 1_000_000L
