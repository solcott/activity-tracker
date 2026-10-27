package io.github.solcott.activitytracker.atproto

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestLogWriter
import co.touchlab.kermit.loggerConfigInit
import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.oauth.OAuthClient
import io.github.solcott.activitytracker.atproto.oauth.OAuthServerException
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
import io.github.solcott.activitytracker.atproto.oauth.SessionExpiredException
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.atproto.DescribeRepo
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.parseQueryString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

@OptIn(ExperimentalKermitApi::class)
class OAuthSessionManagerTest {
    private val did = "did:plc:alice"
    private val network = MockNetwork()
    private val store = InMemoryOAuthStore()
    private val oauth =
        OAuthClient(
            network.client,
            IdentityResolver(network.client, "http://plc.test", "http://pds.test"),
            OAuthClientConfig.loopback("http://127.0.0.1:8080/"),
        )
    private val logs = TestLogWriter(Severity.Verbose)
    private val manager = OAuthSessionManager(oauth, store, logger = Logger(loggerConfigInit(logs)))

    private var issued = 1

    private val tokenRequests
        get() = network.requests.filter { it.url.encodedPath == "/oauth/token" }

    private suspend fun signedIn(refreshToken: String? = "rt1") {
        store.session =
            OAuthSession(
                did = Did(did),
                handle = Handle("alice.test"),
                pdsUrl = "http://pds.test",
                issuer = "http://pds.test",
                tokenEndpoint = "http://pds.test/oauth/token",
                revocationEndpoint = "http://pds.test/oauth/revoke",
                accessToken = "at1",
                refreshToken = refreshToken,
                expiresAt = null,
                scope = "atproto",
                dpopKey = DpopKey.generate().serialize(),
            )
        manager.restore()
    }

    private fun tokensIssued(sub: String = did) =
        network.on("pds.test/oauth/token") {
            issued++
            respondJson(
                """{"access_token":"at$issued","token_type":"DPoP","refresh_token":"rt$issued","expires_in":3600,
                   "scope":"atproto","sub":"$sub"}"""
            )
        }

    private fun tokenEndpointAnswers(status: HttpStatusCode, body: String) =
        network.on("pds.test/oauth/token") { respondJson(body, status) }

    @Test
    fun serverErrorKeepsTheSessionAndRefreshesAgainNextTime() = runTest {
        signedIn()
        tokenEndpointAnswers(HttpStatusCode.ServiceUnavailable, "<html>down</html>")

        val failure = assertFailsWith<OAuthServerException> { manager.freshSession(forceRefresh = true) }

        assertEquals(503, failure.status)
        assertEquals("at1", manager.session.value?.accessToken)
        assertEquals("at1", store.session?.accessToken)
        assertNull(manager.expired.value)
        assertFailsWith<OAuthServerException> { manager.freshSession(forceRefresh = true) }
        assertEquals(2, tokenRequests.size)
    }

    @Test
    fun aRefreshByAnotherTabIsTakenRatherThanRepeated() = runTest {
        signedIn()
        tokensIssued()
        // Another tab, sharing the store, refreshed first: rt1 is spent.
        store.session = store.session!!.copy(accessToken = "at9", refreshToken = "rt9")

        assertEquals("at9", manager.freshSession(forceRefresh = true).accessToken)
        assertEquals(0, tokenRequests.size, "no refresh with the spent token")
        assertNull(manager.expired.value)
    }

    @Test
    fun rateLimitKeepsTheSession() = runTest {
        signedIn()
        tokenEndpointAnswers(HttpStatusCode.TooManyRequests, """{"error":"rate_limited"}""")

        assertEquals(429, assertFailsWith<OAuthServerException> { manager.freshSession(forceRefresh = true) }.status)

        assertEquals("at1", manager.session.value?.accessToken)
        assertNull(manager.expired.value)
    }

    @Test
    fun offlineKeepsTheSession() = runTest {
        signedIn()
        network.on("pds.test/oauth/token") { throw IOException("Connection refused") }

        assertFailsWith<IOException> { manager.freshSession(forceRefresh = true) }

        assertEquals("at1", manager.session.value?.accessToken)
        assertNull(manager.expired.value)
    }

    @Test
    fun refusedGrantMarksTheSessionExpiredOnce() = runTest {
        signedIn()
        tokenEndpointAnswers(HttpStatusCode.BadRequest, """{"error":"invalid_grant","error_description":"revoked"}""")

        val results = List(3) { async { runCatching { manager.freshSession(forceRefresh = true) } } }.awaitAll()

        results.forEach { assertIs<SessionExpiredException>(it.exceptionOrNull()) }
        assertEquals(1, tokenRequests.size)
        assertEquals("at1", manager.expired.value?.accessToken)
        // Kept until AuthRepository ends it.
        assertSame(manager.expired.value, manager.session.value)
        assertEquals("at1", store.session?.accessToken)
        logs.assertCount(1)
        logs.assertLast { severity == Severity.Warn && tag == "OAuthSessionManager" && throwable is SessionExpiredException }

        assertFailsWith<SessionExpiredException> { manager.freshSession() }
        assertEquals(1, tokenRequests.size)
    }

    @Test
    fun missingRefreshTokenOrAnotherAccountExpires() = runTest {
        signedIn(refreshToken = null)
        assertFailsWith<SessionExpiredException> { manager.freshSession(forceRefresh = true) }
        assertEquals(0, tokenRequests.size)

        manager.signOut(revoke = false)
        signedIn()
        tokensIssued(sub = "did:plc:mallory")
        assertFailsWith<SessionExpiredException> { manager.freshSession(forceRefresh = true) }
        assertEquals("at1", manager.expired.value?.accessToken)
    }

    @Test
    fun signOutOfAnExpiredSessionClearsItWithoutRevoking() = runTest {
        signedIn()
        tokenEndpointAnswers(HttpStatusCode.BadRequest, """{"error":"invalid_grant"}""")
        assertFailsWith<SessionExpiredException> { manager.freshSession(forceRefresh = true) }

        manager.signOut(revoke = false)

        assertNull(manager.session.value)
        assertNull(manager.expired.value)
        assertNull(store.session)
        assertEquals(0, network.requests.count { it.url.encodedPath == "/oauth/revoke" })
    }

    @Test
    fun signOutWaitsForARefreshAndRevokesItsToken() = runTest {
        signedIn()
        val requested = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        network.on("pds.test/oauth/token") {
            requested.complete(Unit)
            release.await()
            respondJson("""{"access_token":"at2","token_type":"DPoP","refresh_token":"rt2","scope":"atproto","sub":"$did"}""")
        }
        network.on("pds.test/oauth/revoke") { respondJson("{}") }

        val refresh = async { manager.freshSession(forceRefresh = true) }
        requested.await()
        val signOut = launch { manager.signOut() }
        release.complete(Unit)
        refresh.await()
        signOut.join()

        assertNull(manager.session.value)
        assertNull(store.session)
        val revoked = network.requests.single { it.url.encodedPath == "/oauth/revoke" }
        assertEquals("rt2", revoked.form()["token"])
    }

    @Test
    fun requestsRejectedWithTheSameTokenShareOneRefresh() = runTest {
        signedIn()
        tokensIssued()
        val bothSent = CompletableDeferred<Unit>()
        var rejected = 0
        network.on("pds.test/xrpc/com.atproto.repo.describeRepo") { request ->
            if (request.headers["Authorization"] == "DPoP at1") {
                if (++rejected == 2) bothSent.complete(Unit)
                bothSent.await()
                respondJson(
                    """{"error":"invalid_token"}""",
                    HttpStatusCode.Unauthorized,
                    "WWW-Authenticate" to "DPoP error=\"invalid_token\"",
                )
            } else {
                respondJson("""{"handle":"alice.test","did":"$did"}""")
            }
        }
        val pds = XrpcClient(network.client, "http://pds.test", manager.pdsAuthenticator)

        List(2) { async { pds.query(DescribeRepo.method, DescribeRepo.Params(did)) } }.awaitAll()

        assertEquals(1, tokenRequests.size)
        val retries = network.requests.filter { it.url.encodedPath.endsWith("describeRepo") }.drop(2)
        assertEquals(listOf("DPoP at2", "DPoP at2"), retries.map { it.headers["Authorization"] })
    }

    @Test
    fun aRequestRejectedAfterARefreshRetriesWithTheNewToken() = runTest {
        signedIn()
        tokensIssued()
        val sent = CompletableDeferred<Unit>()
        val reject = CompletableDeferred<Unit>()
        network.on("pds.test/xrpc/com.atproto.repo.describeRepo") { request ->
            if (request.headers["Authorization"] == "DPoP at1") {
                sent.complete(Unit)
                reject.await()
                respondJson(
                    """{"error":"invalid_token"}""",
                    HttpStatusCode.Unauthorized,
                    "WWW-Authenticate" to "DPoP error=\"invalid_token\"",
                )
            } else {
                respondJson("""{"handle":"alice.test","did":"$did"}""")
            }
        }
        val pds = XrpcClient(network.client, "http://pds.test", manager.pdsAuthenticator)

        val request = async { pds.query(DescribeRepo.method, DescribeRepo.Params(did)) }
        sent.await()
        manager.freshSession(forceRefresh = true)
        reject.complete(Unit)
        request.await()

        assertEquals(1, tokenRequests.size)
        assertEquals("DPoP at2", network.requests.last().headers["Authorization"])
    }

    private fun HttpRequestData.form(): Map<String, String> {
        val text = (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
        return parseQueryString(text).entries().associate { it.key to it.value.single() }
    }
}
