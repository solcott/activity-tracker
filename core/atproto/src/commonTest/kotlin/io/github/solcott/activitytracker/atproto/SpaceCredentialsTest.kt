package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.space.SpaceCredentials
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

class SpaceCredentialsTest {
    private val f = SpaceFixtures()

    private suspend fun credentials(key: DpopKey? = null) =
        SpaceCredentials(f.viewerPds, f.network.client, f.identity, { key ?: DpopKey.generate() }, f.clock)

    @Test
    fun exchangesAtTheAuthoritysPdsWithASignedDelegation() = runTest {
        val key = DpopKey.generate()
        val credential = credentials(key).get(f.aliceSpace)

        assertEquals(f.clock.now + 600.seconds, credential.expiresAt)
        val delegation = f.requestsTo("pds.test/xrpc/com.atproto.space.getDelegationToken").single()
        assertEquals(f.aliceSpace.value, delegation.url.parameters["space"])
        val exchange = f.requestsTo("alicepds.test/xrpc/com.atproto.space.getSpaceCredential").single()
        assertEquals("Bearer delegation-1", exchange.headers["Authorization"])
        assertEquals("""atproto-space=("authorization");keyid="${key.didKey}"""", exchange.headers["Signature-Input"])
        assertTrue(exchange.headers["Signature"].orEmpty().startsWith("atproto-space=:"))
    }

    @Test
    fun repeatedReadsReuseTheCredential() = runTest {
        val credentials = credentials()
        val first = credentials.get(f.aliceSpace)
        f.clock.now += 500.seconds
        assertEquals(first.token, credentials.get(f.aliceSpace).token)
        assertEquals(1, f.delegations)
        assertEquals(1, f.exchanges)
    }

    @Test
    fun concurrentFirstReadsShareOneExchange() = runTest {
        val gate = CompletableDeferred<Unit>()
        f.network.on("pds.test/xrpc/com.atproto.space.getDelegationToken") {
            gate.await()
            respondJson("""{"token":"delegation-${++f.delegations}"}""")
        }
        val credentials = credentials()
        val reads = List(5) { async { credentials.get(f.aliceSpace) } }
        yield()
        gate.complete(Unit)
        val tokens = reads.awaitAll().map { it.token }.toSet()
        assertEquals(1, tokens.size)
        assertEquals(1, f.delegations)
        assertEquals(1, f.exchanges)
    }

    @Test
    fun refreshesWithinFiveSecondsOfExpiry() = runTest {
        val credentials = credentials()
        val first = credentials.get(f.aliceSpace)
        f.clock.now += 594.seconds
        assertEquals(first.token, credentials.get(f.aliceSpace).token)
        f.clock.now += 2.seconds
        val second = credentials.get(f.aliceSpace)
        assertTrue(second.token != first.token)
        assertEquals(2, f.exchanges)
    }

    @Test
    fun invalidateForcesANewExchange() = runTest {
        val credentials = credentials()
        val first = credentials.get(f.aliceSpace)
        credentials.invalidate(f.aliceSpace)
        assertTrue(credentials.get(f.aliceSpace).token != first.token)
        assertEquals(2, f.delegations)
        assertEquals(2, f.exchanges)
    }

    @Test
    fun refusalsAreNotCached() = runTest {
        var refuse = true
        f.network.on("alicepds.test/xrpc/com.atproto.space.getSpaceCredential") {
            f.exchanges++
            if (refuse) {
                respondJson("""{"error":"UserNotAuthorized","message":"not a mutual"}""", HttpStatusCode.BadRequest)
            } else {
                respondJson("""{"credential":"${SpaceFixtures.credential(f.clock.now.epochSeconds + 600)}"}""")
            }
        }
        val credentials = credentials()
        assertEquals("UserNotAuthorized", assertFailsWith<XrpcException> { credentials.get(f.aliceSpace) }.error)
        refuse = false
        credentials.get(f.aliceSpace)
        assertEquals(2, f.exchanges)
    }

    @Test
    fun resolvesTheAuthoritysPdsOncePerSession() = runTest {
        val credentials = credentials()
        credentials.get(f.aliceSpace)
        credentials.invalidate(f.aliceSpace)
        credentials.get(f.aliceSpace)
        assertEquals(1, f.requestsTo("plc.test/${f.alice.value}").size)
    }
}
