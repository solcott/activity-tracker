package io.github.solcott.activitytracker.atproto.xrpc

import io.ktor.http.Headers
import io.ktor.http.fromHttpToGmtDate
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A server's rate limit as its `RateLimit-*` headers state it (data/outbox): the tightest of the limits the call counted against, with
 * [remaining] of [limit] left until [resetAt]. The PDS sends them on every response from a limited route, a 429 included.
 */
data class RateLimit(
    val limit: Int,
    val remaining: Int,
    val resetAt: Instant?,
    val policy: String? = null,
    /** The XRPC method whose response stated it, when known: blob uploads and record writes count against different limits. */
    val method: String? = null,
) {
    /** The policy's window (`w=` seconds), or null when it states none. */
    val windowSeconds: Long?
        get() = policy?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("w=") }?.removePrefix("w=")?.toLongOrNull()

    companion object {
        /** The limit [headers] state, or null without `RateLimit-Limit` and `RateLimit-Remaining`. */
        fun of(headers: Headers, now: Instant): RateLimit? {
            val limit = headers[LIMIT]?.trim()?.toIntOrNull()
            val remaining = headers[REMAINING]?.trim()?.toIntOrNull()
            if (limit == null || remaining == null) return null
            val reset =
                headers[RESET]?.trim()?.toLongOrNull()?.let { value ->
                    // The PDS sends epoch seconds; the IETF draft's form is seconds from now.
                    if (value > EPOCH_SECONDS_FLOOR) Instant.fromEpochSeconds(value) else now + value.seconds
                }
            return RateLimit(limit, remaining, reset, headers[POLICY])
        }

        /** When `Retry-After` in [headers] says to try again (seconds, or an HTTP date), or null without it. */
        fun retryAfter(headers: Headers, now: Instant): Instant? {
            val value = headers[RETRY_AFTER]?.trim() ?: return null
            val seconds = value.toLongOrNull()
            return if (seconds != null) now + seconds.coerceAtLeast(0).seconds
            else runCatching { Instant.fromEpochMilliseconds(value.fromHttpToGmtDate().timestamp) }.getOrNull()
        }

        private const val LIMIT = "RateLimit-Limit"
        private const val REMAINING = "RateLimit-Remaining"
        private const val RESET = "RateLimit-Reset"
        private const val POLICY = "RateLimit-Policy"
        private const val RETRY_AFTER = "Retry-After"

        /** A reset above this (2001-09-09) is an epoch time rather than a number of seconds from now. */
        private const val EPOCH_SECONDS_FLOOR = 1_000_000_000L
    }
}
