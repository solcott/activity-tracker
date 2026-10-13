package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.AuthException
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        assertFailsWith<AuthException> { verifier.verify(key.serviceJwt(alice, "did:web:other", null), "app.x.y") }
        assertFailsWith<AuthException> { verifier.verify(key.serviceJwt(alice, service, "app.x.z"), "app.x.y") }
        assertFailsWith<AuthException> { verifier.verify(key.serviceJwt(alice, service, null, expSeconds = -10), "app.x.y") }
        assertFailsWith<AuthException> { verifier.verify(TestAccountKey().serviceJwt(alice, service, null), "app.x.y") }
    }

    @Test
    fun acceptsExtraAudiences() = runTest {
        val tunnel = "did:web:example.trycloudflare.com"
        val both = ServiceAuthVerifier(tunnel, keys = { key.multibase }, extraAudiences = setOf(service))
        assertEquals(Did(alice), both.verify(key.serviceJwt(alice, tunnel, null), "app.x.y"))
        assertEquals(Did(alice), both.verify(key.serviceJwt(alice, service, null), "app.x.y"))
        assertEquals(Did(alice), both.verify(key.serviceJwt(alice, "$service#activity_appview", null), "app.x.y"))
        assertFailsWith<AuthException> { both.verify(key.serviceJwt(alice, "did:web:other", null), "app.x.y") }
    }

    @Test
    fun managingAppAudienceIsKeptApart() = runTest {
        val managingApp = "$service#activitytracker_space"
        val callouts = ServiceAuthVerifier(setOf(managingApp), keys = { key.multibase })
        assertEquals(Did(alice), callouts.verify(key.serviceJwt(alice, managingApp, null), "app.x.y"))
        assertFailsWith<AuthException> { callouts.verify(key.serviceJwt(alice, service, null), "app.x.y") }
        assertFailsWith<AuthException> { callouts.verify(key.serviceJwt(alice, "$service#activity_appview", null), "app.x.y") }
        assertFailsWith<AuthException> { verifier.verify(key.serviceJwt(alice, managingApp, null), "app.x.y") }
    }
}
