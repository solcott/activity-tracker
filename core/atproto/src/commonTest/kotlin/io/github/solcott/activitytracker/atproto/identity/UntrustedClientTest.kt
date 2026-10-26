package io.github.solcott.activitytracker.atproto.identity

import io.github.solcott.activitytracker.atproto.MockNetwork
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** identity/outbound-requests: hosts named by an identity are fetched with the untrusted client, configured endpoints with the other. */
class UntrustedClientTest {
    private val trusted = MockNetwork()
    private val untrusted = MockNetwork()
    private val resolver = IdentityResolver(trusted.client, "https://plc.test", "https://resolver.test", untrusted.client)

    private fun MockNetwork.hosts() = requests.map { it.url.host }

    @Test
    fun didWebDocumentsUseTheUntrustedClient() = runTest {
        runCatching { resolver.resolveDid(Did("did:web:example.com")) }
        runCatching { resolver.resolveDid(Did("did:plc:alice")) }
        assertEquals(listOf("example.com"), untrusted.hosts())
        assertEquals(listOf("plc.test"), trusted.hosts())
    }

    @Test
    fun onlyTheWellKnownHandleLookupUsesTheUntrustedClient() = runTest {
        runCatching { resolver.resolveHandle(Handle("alice.example.com")) }
        assertEquals(listOf("alice.example.com"), untrusted.hosts())
        assertEquals(listOf("resolver.test", "cloudflare-dns.com"), trusted.hosts())
    }
}
