package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.atproto.xrpc.RateLimit
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class RateLimitTest {
    private val now = Instant.fromEpochSeconds(1_800_000_000)

    @Test
    fun theResetIsEpochSecondsAsThePdsSendsIt() {
        val headers =
            headersOf(
                "RateLimit-Limit" to listOf("5000"),
                "RateLimit-Remaining" to listOf("4998"),
                "RateLimit-Reset" to listOf("1800003600"),
                "RateLimit-Policy" to listOf("5000;w=3600"),
            )
        assertEquals(RateLimit(5000, 4998, Instant.fromEpochSeconds(1_800_003_600), "5000;w=3600"), RateLimit.of(headers, now))
    }

    @Test
    fun aSmallResetIsSecondsFromNow() {
        val headers = headersOf("RateLimit-Limit" to listOf("100"), "RateLimit-Remaining" to listOf("0"), "RateLimit-Reset" to listOf("90"))
        assertEquals(now + 90.seconds, RateLimit.of(headers, now)?.resetAt)
    }

    @Test
    fun noHeadersIsNoLimit() {
        assertNull(RateLimit.of(headersOf(), now))
        assertNull(RateLimit.of(headersOf("RateLimit-Limit" to listOf("100")), now))
        assertNull(RateLimit.retryAfter(headersOf(), now))
    }

    @Test
    fun retryAfterIsSecondsOrAnHttpDate() {
        assertEquals(now + 120.seconds, RateLimit.retryAfter(headersOf("Retry-After" to listOf("120")), now))
        assertEquals(
            Instant.parse("2027-01-15T08:49:37Z"),
            RateLimit.retryAfter(headersOf("Retry-After" to listOf("Fri, 15 Jan 2027 08:49:37 GMT")), now),
        )
        assertNull(RateLimit.retryAfter(headersOf("Retry-After" to listOf("soon")), now))
    }

    @Test
    fun theClientReportsLimitsFromSuccessesAndFailures() = runTest {
        val network = MockNetwork()
        val heard = mutableListOf<RateLimit>()
        val clock =
            object : Clock {
                override fun now() = this@RateLimitTest.now
            }
        val client = XrpcClient(network.client, { "http://pds.test" }, onRateLimit = { heard += it }, clock = clock)
        var calls = 0
        network.on("pds.test/xrpc/app.activitytracker.feed.getTimeline") {
            calls++
            if (calls == 1) {
                respond(
                    """{"activities":[]}""",
                    HttpStatusCode.OK,
                    headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        "RateLimit-Limit" to listOf("5000"),
                        "RateLimit-Remaining" to listOf("2"),
                        "RateLimit-Reset" to listOf("1800000600"),
                    ),
                )
            } else {
                respond(
                    """{"error":"RateLimitExceeded","message":"Rate Limit Exceeded"}""",
                    HttpStatusCode.TooManyRequests,
                    headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        "RateLimit-Limit" to listOf("5000"),
                        "RateLimit-Remaining" to listOf("0"),
                        "RateLimit-Reset" to listOf("1800000600"),
                        "Retry-After" to listOf("600"),
                    ),
                )
            }
        }

        client.query(GetTimeline.method, GetTimeline.Params())
        val e = assertFailsWith<XrpcException> { client.query(GetTimeline.method, GetTimeline.Params()) }

        assertEquals(listOf(2, 0), heard.map { it.remaining })
        assertEquals(429, e.status)
        assertEquals(0, e.rateLimit?.remaining)
        assertEquals(now + 600.seconds, e.retryAfter)
    }
}
