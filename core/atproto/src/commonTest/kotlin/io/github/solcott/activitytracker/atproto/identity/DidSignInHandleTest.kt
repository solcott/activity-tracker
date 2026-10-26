package io.github.solcott.activitytracker.atproto.identity

import io.github.solcott.activitytracker.atproto.MockNetwork
import io.github.solcott.activitytracker.atproto.respondJson
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

/** identity/handle-verification: signing in with a DID shows its claimed handle only once that handle resolves back to it. */
class DidSignInHandleTest {
    private val network = MockNetwork()
    private val resolver = IdentityResolver(network.client, plcUrl = "https://plc.test", handleResolverUrl = "https://pds.test")
    private val alice = "did:plc:alice"

    private fun claims(handle: String) =
        network.on("plc.test/$alice") {
            respondJson(
                """{"id":"$alice","alsoKnownAs":["at://$handle"],""" +
                    """"service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"https://pds.test"}]}"""
            )
        }

    private fun handleResolvesTo(did: String) =
        network.on("pds.test/xrpc/com.atproto.identity.resolveHandle") { respondJson("""{"did":"$did"}""") }

    private fun otherLookupsFail() {
        network.on("cloudflare-dns.com/dns-query") { throw IOException("offline") }
        network.on("alice.example.com/.well-known/atproto-did") { throw IOException("offline") }
    }

    @Test
    fun aMatchingHandleIsShown() = runTest {
        claims("Alice.Example.com")
        handleResolvesTo(alice)
        assertEquals("alice.example.com", resolver.resolve(alice).handle.value)
    }

    @Test
    fun someoneElsesHandleIsInvalidAndSignInGoesOn() = runTest {
        claims("alice.example.com")
        handleResolvesTo("did:plc:bob")
        val identity = resolver.resolve(alice)
        assertEquals(IdentityResolver.INVALID_HANDLE, identity.handle)
        assertEquals("https://pds.test", identity.pdsUrl)
    }

    @Test
    fun aHandleThatResolvesToNothingIsInvalid() = runTest {
        claims("alice.example.com")
        network.on("pds.test/xrpc/com.atproto.identity.resolveHandle") {
            respondJson("""{"error":"InvalidRequest","message":"Unable to resolve handle"}""", HttpStatusCode.BadRequest)
        }
        otherLookupsFail()
        assertEquals(IdentityResolver.INVALID_HANDLE, resolver.resolve(alice).handle)
    }

    @Test
    fun unreachableLookupsAreInvalid() = runTest {
        claims("alice.example.com")
        network.on("pds.test/xrpc/com.atproto.identity.resolveHandle") { throw IOException("offline") }
        otherLookupsFail()
        assertEquals(IdentityResolver.INVALID_HANDLE, resolver.resolve(alice).handle)
    }
}
