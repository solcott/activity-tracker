package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.net.CachingIdentityResolver
import io.github.solcott.activitytracker.appview.net.PlcPacing
import io.github.solcott.activitytracker.atproto.identity.IdentityRateLimitedException
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

/** identity/outbound-requests: DID documents are cached, dropped on `#identity`, and a 429 pauses every `did:plc` fetch. */
class CachingIdentityResolverTest {
    private val alice = Did("did:plc:alice")
    private val clock = MutableClock(Clock.System.now())
    private val identities = FakeIdentities().apply { claims[alice.value] = "alice.example.com" }
    private val pacing = PlcPacing(clock)
    private val resolver =
        CachingIdentityResolver(identities.client, "http://plc.test", null, pacing = pacing, ttl = 10.minutes, clock = clock)

    @Test
    fun aDocumentIsFetchedOnceUntilItsTtlOrAnInvalidation() = runTest {
        repeat(3) { resolver.resolveDid(alice) }
        assertEquals(1, identities.requests)

        resolver.invalidate(alice)
        resolver.resolveDid(alice)
        assertEquals(2, identities.requests)

        clock.now += 11.minutes
        resolver.resolveDid(alice)
        assertEquals(3, identities.requests)
    }

    @Test
    fun aRefreshFetchesPastTheCacheAndKeepsWhatItGot() = runTest {
        resolver.resolveDid(alice)
        identities.claims[alice.value] = "alice.new.example"
        assertEquals("alice.new.example", resolver.refreshDid(alice).handle)
        assertEquals("alice.new.example", resolver.resolveDid(alice).handle)
        assertEquals(2, identities.requests)
    }

    @Test
    fun a429PausesEveryPlcFetchUntilRetryAfter() = runTest {
        identities.plcLimited = 30
        assertFailsWith<IdentityRateLimitedException> { resolver.resolveDid(alice) }
        // Retry-After is read against the real clock, a moment after the test's started.
        val until = assertNotNull(pacing.pausedUntil())
        assertTrue(until - clock.now in 29.seconds..35.seconds, "paused until $until")

        identities.plcLimited = null
        assertFailsWith<IdentityRateLimitedException> { resolver.resolveDid(Did("did:plc:bob")) }
        assertEquals(1, identities.requests, "nothing sent while paused")

        clock.now += 40.seconds
        assertEquals(null, pacing.pausedUntil())
        resolver.resolveDid(alice)
        assertEquals(2, identities.requests)
    }

    @Test
    fun aPauseIsBounded() {
        pacing.pause(clock.now + 2.seconds)
        assertEquals(clock.now + 10.seconds, pacing.pausedUntil())
        pacing.pause(clock.now + 5.minutes * 10)
        assertEquals(clock.now + 15.minutes, pacing.pausedUntil())
        pacing.pause(null)
        assertEquals(clock.now + 15.minutes, pacing.pausedUntil(), "a shorter pause never cuts a longer one")
    }
}
