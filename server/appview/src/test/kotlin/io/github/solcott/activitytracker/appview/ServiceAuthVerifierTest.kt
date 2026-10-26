package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.AuthException
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest

class ServiceAuthVerifierTest {
    private val service = "did:web:localhost%3A8081"
    private val alice = "did:plc:alice"
    private val key = TestAccountKey()
    private val verifier =
        ServiceAuthVerifier(
            service,
            keys = { did -> if (did.value == alice) key.multibase else null },
        )

    @Test
    fun acceptsValidToken() = runTest {
        assertEquals(
            Did(alice),
            verifier.verify(key.serviceJwt(alice, service, "app.x.y"), "app.x.y"),
        )
    }

    @Test
    fun rejectsWrongAudienceMethodExpiryAndSigner() = runTest {
        assertFailsWith<AuthException> { verifier.verify(key.serviceJwt(alice, "did:web:other", "app.x.y"), "app.x.y") }
        assertFailsWith<AuthException> { verifier.verify(key.serviceJwt(alice, service, "app.x.z"), "app.x.y") }
        assertFailsWith<AuthException> { verifier.verify(key.serviceJwt(alice, service, "app.x.y", expSeconds = -10), "app.x.y") }
        assertFailsWith<AuthException> { verifier.verify(TestAccountKey().serviceJwt(alice, service, "app.x.y"), "app.x.y") }
    }

    @Test
    fun acceptsExtraAudiences() = runTest {
        val tunnel = "did:web:example.trycloudflare.com"
        val both = ServiceAuthVerifier(tunnel, keys = { key.multibase }, extraAudiences = setOf(service))
        assertEquals(Did(alice), both.verify(key.serviceJwt(alice, tunnel, "app.x.y"), "app.x.y"))
        assertEquals(Did(alice), both.verify(key.serviceJwt(alice, service, "app.x.y"), "app.x.y"))
        assertEquals(Did(alice), both.verify(key.serviceJwt(alice, "$service#activity_appview", "app.x.y"), "app.x.y"))
        assertFailsWith<AuthException> { both.verify(key.serviceJwt(alice, "did:web:other", "app.x.y"), "app.x.y") }
    }

    @Test
    fun managingAppAudienceIsKeptApart() = runTest {
        val managingApp = "$service#activitytracker_space"
        val callouts = ServiceAuthVerifier(setOf(managingApp), keys = { key.multibase })
        assertEquals(Did(alice), callouts.verify(key.serviceJwt(alice, managingApp, "app.x.y"), "app.x.y"))
        assertFailsWith<AuthException> { callouts.verify(key.serviceJwt(alice, service, "app.x.y"), "app.x.y") }
        assertFailsWith<AuthException> { callouts.verify(key.serviceJwt(alice, "$service#activity_appview", "app.x.y"), "app.x.y") }
        assertFailsWith<AuthException> { verifier.verify(key.serviceJwt(alice, managingApp, "app.x.y"), "app.x.y") }
    }

    @Test
    fun requiresLxmForTheCalledMethod() = runTest {
        val missing = assertFailsWith<AuthException> { verifier.verify(key.serviceJwt(alice, service, null), "app.x.y") }
        assertEquals("Token not valid for app.x.y", missing.message)
        val other = assertFailsWith<AuthException> { verifier.verify(key.serviceJwt(alice, service, "app.x.z"), "app.x.y") }
        assertEquals("Token not valid for app.x.y", other.message)
    }

    @Test
    fun malformedIssuerIsRefusedWithoutALookup() = runTest {
        val noLookups = ServiceAuthVerifier(service, keys = { error("looked up $it") })
        for (iss in listOf("did:plc:abc/../../admin", "did:plc:abc?x", "did:web:example.com:users:alice", "did:key:z6Mk", "alice")) {
            val e = assertFailsWith<AuthException>(iss) { noLookups.verify(key.serviceJwt(iss, service, "app.x.y"), "app.x.y") }
            assertEquals("Bad iss", e.message)
        }
    }

    @Test
    fun failedLookupsAreRememberedForFiveMinutes() = runTest {
        val clock = MutableClock(Clock.System.now())
        var lookups = 0
        var resolvable = false
        val flaky =
            ServiceAuthVerifier(
                service,
                keys = { did ->
                    lookups++
                    if (resolvable) key.multibase else error("unreachable $did")
                },
                clock = clock,
            )
        fun token() = key.serviceJwt(alice, service, "app.x.y", expSeconds = 3600)

        repeat(5) { assertFailsWith<AuthException> { flaky.verify(token(), "app.x.y") } }
        assertEquals(1, lookups, "one lookup within the window")

        resolvable = true
        clock.now += 4.minutes
        assertFailsWith<AuthException> { flaky.verify(token(), "app.x.y") }
        assertEquals(1, lookups)

        clock.now += 2.minutes
        assertEquals(Did(alice), flaky.verify(token(), "app.x.y"))
        assertEquals(2, lookups)
    }

    @Test
    fun aSignatureMismatchDoesNotRelookUpAFailedDid() = runTest {
        var lookups = 0
        val once = ServiceAuthVerifier(service, keys = { if (lookups++ == 0) TestAccountKey().multibase else null })
        // The cached key doesn't match, so the forced re-resolve finds nothing and is remembered.
        assertFailsWith<AuthException> { once.verify(key.serviceJwt(alice, service, "app.x.y"), "app.x.y") }
        assertFailsWith<AuthException> { once.verify(TestAccountKey().serviceJwt(alice, service, "app.x.y"), "app.x.y") }
        assertEquals(2, lookups)
    }
}
