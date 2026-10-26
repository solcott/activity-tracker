package io.github.solcott.activitytracker.atproto.identity

import io.github.solcott.activitytracker.atproto.MockNetwork
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** identity/outbound-requests: a DID is checked for syntax before any request is made to resolve it. */
class DidSyntaxTest {
    private val network = MockNetwork()
    private val resolver = IdentityResolver(network.client, plcUrl = "https://plc.test", handleResolverUrl = null)

    @Test
    fun malformedDidsAreRefusedWithoutARequest() = runTest {
        val malformed =
            listOf(
                "did:plc:abc/../../export",
                "did:plc:abc?x=1",
                "did:plc:abc#frag",
                "did:plc:ABC",
                "did:plc:",
                "did:web:example.com:users:alice",
                "did:web:example.com/path",
                "did:web:-bad.example",
                "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
            )
        for (did in malformed) {
            assertFailsWith<IdentityException>(did) { resolver.resolveDid(Did(did)) }
        }
        assertEquals(emptyList(), network.requests.map { it.url.toString() })
    }

    @Test
    fun localStackFormsAreFetched() = runTest {
        for (did in listOf("did:plc:alice", "did:plc:ewvi7nxzyoun6zhxrhs64oiz", "did:web:localhost%3A8081", "did:web:example.com")) {
            assertTrue(isResolvableDid(did), did)
            runCatching { resolver.resolveDid(Did(did)) }
        }
        assertEquals(
            listOf(
                "https://plc.test/did:plc:alice",
                "https://plc.test/did:plc:ewvi7nxzyoun6zhxrhs64oiz",
                "http://localhost:8081/.well-known/did.json",
                "https://example.com/.well-known/did.json",
            ),
            network.requests.map { it.url.toString() },
        )
    }
}
