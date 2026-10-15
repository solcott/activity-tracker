package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.PrivateMediaTokens
import io.github.solcott.activitytracker.appview.api.SignedViewer
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Signed photo URLs are short-lived (`spaces/appview-syncer`). */
class PrivateMediaTokensTest {
    private val boundary = Instant.parse("2026-10-15T12:00:00Z")
    private val clock = MutableClock(boundary)
    private val tokens = PrivateMediaTokens(TEST_SIGNING_KEY, clock)
    private val viewer = Did("did:plc:bob")
    private val did = "did:plc:alice"
    private val cid = "bafkphoto"

    private fun params() = tokens.params(did, cid, viewer).toMap()

    private fun validFor(): Duration = (params().getValue(PrivateMediaTokens.EXPIRES).toLong() - clock.now.epochSeconds).seconds

    private fun verify(params: Map<String, String>) =
        tokens.verify(
            did,
            cid,
            params[PrivateMediaTokens.VIEWER],
            params[PrivateMediaTokens.EXPIRES],
            params[PrivateMediaTokens.SIGNATURE],
        )

    @Test
    fun expiryIsFifteenToThirtyMinutesOut() {
        assertEquals(15.minutes, validFor(), "on a boundary")
        clock.now = boundary + 1.seconds
        assertEquals(30.minutes - 1.seconds, validFor(), "just after one")
        clock.now = boundary - 1.seconds
        assertEquals(15.minutes + 1.seconds, validFor(), "just before one")
        for (offset in 0..(15 * 60) step 7) {
            clock.now = boundary + offset.seconds
            val left = validFor()
            assertTrue(left >= 15.minutes && left <= 30.minutes, "$left at +${offset}s")
        }
    }

    @Test
    fun urlIsStableWithinAWindow() {
        clock.now = boundary + 1.seconds
        val first = params()
        clock.now = boundary + 14.minutes
        assertEquals(first, params())
        clock.now = boundary + 15.minutes + 1.seconds
        assertEquals(
            first.getValue(PrivateMediaTokens.EXPIRES).toLong() + 15 * 60,
            params().getValue(PrivateMediaTokens.EXPIRES).toLong(),
        )
    }

    @Test
    fun verifyReportsTheTimeLeft() {
        clock.now = boundary + 1.seconds
        val url = params()
        clock.now = boundary + 20.minutes
        assertEquals(SignedViewer(viewer, 10.minutes), verify(url))
        clock.now = boundary + 30.minutes
        assertEquals(SignedViewer(viewer, Duration.ZERO), verify(url), "valid through its expiry second")
    }

    @Test
    fun expiredUrlIsRejected() {
        val url = params()
        clock.now = boundary + 15.minutes + 1.seconds
        assertNull(verify(url))
    }

    @Test
    fun stepMustBePositive() {
        assertFailsWith<IllegalArgumentException> { PrivateMediaTokens(TEST_SIGNING_KEY, clock, step = Duration.ZERO) }
    }
}
