package io.github.solcott.activitytracker.atproto.identity

import io.github.solcott.activitytracker.atproto.MockNetwork
import io.github.solcott.activitytracker.atproto.respondJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

/** ui/error-messages: handle resolution tells a handle that doesn't exist apart from lookups that couldn't be made. */
class HandleResolutionTest {
    private val handle = Handle("alice.test")
    private val network = MockNetwork()
    private val resolver = IdentityResolver(network.client, plcUrl = "https://plc.test", handleResolverUrl = "https://pds.test")

    private fun resolverAnswers(status: HttpStatusCode, json: String) =
        network.on("pds.test/xrpc/com.atproto.identity.resolveHandle") { respondJson(json, status) }

    private fun dohFails() = network.on("cloudflare-dns.com/dns-query") { throw IOException("offline") }

    private fun wellKnownFails() = network.on("alice.test/.well-known/atproto-did") { throw IOException("no such host") }

    @Test
    fun theResolversAnswerWins() = runTest {
        resolverAnswers(HttpStatusCode.OK, """{"did":"did:plc:alice"}""")
        assertEquals(Did("did:plc:alice"), resolver.resolveHandle(handle))
    }

    @Test
    fun aResolverThatAnswersMissingMeansNoAccount() = runTest {
        resolverAnswers(HttpStatusCode.BadRequest, """{"error":"InvalidRequest","message":"Unable to resolve handle"}""")
        dohFails()
        wellKnownFails()
        assertEquals(handle, assertFailsWith<HandleNotFoundException> { resolver.resolveHandle(handle) }.handle)
    }

    @Test
    fun aDnsAnswerWithoutADidMeansNoAccount() = runTest {
        network.on("pds.test/xrpc/com.atproto.identity.resolveHandle") { throw IOException("offline") }
        network.on("cloudflare-dns.com/dns-query") { respondJson("""{"Status":3}""") }
        wellKnownFails()
        assertFailsWith<HandleNotFoundException> { resolver.resolveHandle(handle) }
    }

    @Test
    fun offlineRethrowsTheTransportFailure() = runTest {
        network.on("pds.test/xrpc/com.atproto.identity.resolveHandle") { throw IOException("offline") }
        dohFails()
        wellKnownFails()
        assertEquals("offline", assertFailsWith<IOException> { resolver.resolveHandle(handle) }.message)
    }

    @Test
    fun aWellKnownHostThatAnswersWithoutADidMeansNoAccount() = runTest {
        network.on("pds.test/xrpc/com.atproto.identity.resolveHandle") { throw IOException("offline") }
        dohFails()
        network.on("alice.test/.well-known/atproto-did") { respond("not found", HttpStatusCode.NotFound) }
        assertFailsWith<HandleNotFoundException> { resolver.resolveHandle(handle) }
    }

    private fun dnsAnswers(vararg txt: String) {
        network.on("pds.test/xrpc/com.atproto.identity.resolveHandle") { throw IOException("offline") }
        val answers = txt.joinToString(",") { """{"name":"_atproto.alice.test","type":16,"data":"\"$it\""}""" }
        network.on("cloudflare-dns.com/dns-query") { respondJson("""{"Status":0,"Answer":[$answers]}""") }
    }

    @Test
    fun dnsRecordsNamingDifferentDidsMeanNoAccount() = runTest {
        dnsAnswers("did=did:plc:alice", "did=did:plc:mallory")
        network.on("alice.test/.well-known/atproto-did") { respond("did:plc:alice") }
        assertEquals(handle, assertFailsWith<HandleNotFoundException> { resolver.resolveHandle(handle) }.handle)
        assertTrue(network.requests.none { it.url.host == "alice.test" })
    }

    @Test
    fun duplicateDnsRecordsOfOneDidResolve() = runTest {
        dnsAnswers("did=did:plc:alice", "did=did:plc:alice")
        assertEquals(Did("did:plc:alice"), resolver.resolveHandle(handle))
    }

    @Test
    fun unrelatedTxtRecordsAreIgnored() = runTest {
        dnsAnswers("v=spf1 -all", "did=did:plc:alice", "did=not-a-did")
        assertEquals(Did("did:plc:alice"), resolver.resolveHandle(handle))
    }
}
