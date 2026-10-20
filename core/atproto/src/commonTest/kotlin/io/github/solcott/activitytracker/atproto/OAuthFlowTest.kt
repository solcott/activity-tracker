package io.github.solcott.activitytracker.atproto

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestLogWriter
import co.touchlab.kermit.loggerConfigInit
import io.github.solcott.activitytracker.atproto.crypto.Base64Url
import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.oauth.OAuthClient
import io.github.solcott.activitytracker.atproto.oauth.OAuthException
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
import io.github.solcott.activitytracker.atproto.oauth.OAuthStore
import io.github.solcott.activitytracker.atproto.oauth.PendingAuthorization
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.lexicon.AtJson
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OAuthFlowTest {
    private val did = "did:plc:alice"
    private val network =
        MockNetwork().apply {
            on("pds.test/xrpc/com.atproto.identity.resolveHandle") { respondJson("""{"did":"$did"}""") }
            on("plc.test/$did") {
                respondJson(
                    """{"id":"$did","alsoKnownAs":["at://alice.test"],
                   "service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"http://pds.test"}]}"""
                )
            }
            on("pds.test/.well-known/oauth-protected-resource") {
                respondJson("""{"resource":"http://pds.test","authorization_servers":["http://pds.test"]}""")
            }
            on("pds.test/.well-known/oauth-authorization-server") {
                respondJson(
                    """{"issuer":"http://pds.test","authorization_endpoint":"http://pds.test/oauth/authorize",
                   "token_endpoint":"http://pds.test/oauth/token","pushed_authorization_request_endpoint":"http://pds.test/oauth/par"}"""
                )
            }
            // First PAR demands a nonce, second succeeds.
            var parCalls = 0
            on("pds.test/oauth/par") {
                if (parCalls++ == 0) {
                    respondJson(
                        """{"error":"use_dpop_nonce"}""",
                        HttpStatusCode.BadRequest,
                        "DPoP-Nonce" to "n1",
                    )
                } else {
                    respondJson(
                        """{"request_uri":"urn:req:1","expires_in":60}""",
                        HttpStatusCode.Created,
                        "DPoP-Nonce" to "n2",
                    )
                }
            }
            on("pds.test/oauth/token") {
                respondJson(
                    """{"access_token":"at1","token_type":"DPoP","refresh_token":"rt1","expires_in":3600,
                   "scope":"${OAuthClientConfig.DEFAULT_SCOPE}&collection=app.activitytracker.actor.privateDetails","sub":"$did"}"""
                )
            }
            // PDS demands a nonce once, then serves the request.
            var pdsCalls = 0
            on("pds.test/xrpc/com.atproto.repo.describeRepo") {
                if (pdsCalls++ == 0) {
                    respondJson(
                        """{"error":"use_dpop_nonce"}""",
                        HttpStatusCode.Unauthorized,
                        "WWW-Authenticate" to "DPoP error=\"use_dpop_nonce\"",
                        "DPoP-Nonce" to "p1",
                    )
                } else {
                    respondJson("""{"handle":"alice.test","did":"$did"}""")
                }
            }
        }

    private val store = InMemoryStore()
    private val config = OAuthClientConfig.loopback("http://127.0.0.1:8080/")
    private val oauth =
        OAuthClient(
            network.client,
            IdentityResolver(network.client, "http://plc.test", "http://pds.test"),
            config,
        )
    @OptIn(ExperimentalKermitApi::class) private val logs = TestLogWriter(Severity.Verbose)
    private val manager = OAuthSessionManager(oauth, store, logger = Logger(loggerConfigInit(logs)))

    @Test
    fun fullSignInThenAuthenticatedPdsCall() = runTest {
        val authorizeUrl = manager.beginSignIn("alice.test")
        assertTrue(authorizeUrl.startsWith("http://pds.test/oauth/authorize?client_id=http%3A%2F%2Flocalhost"))
        val pending = store.pending.values.single()

        val par = network.requests.filter { it.url.encodedPath == "/oauth/par" }
        assertEquals(2, par.size)
        assertEquals("n1", par[1].dpopClaim("nonce"))
        val form = par[1].form()
        assertEquals("S256", form["code_challenge_method"])
        assertEquals(pending.state, form["state"])

        val session = manager.completeSignIn(mapOf("code" to "c1", "state" to pending.state, "iss" to "http://pds.test"))
        assertEquals(Did(did), session.did)
        assertEquals("alice.test", session.handle.value)
        assertEquals(
            "n2",
            network.requests.last { it.url.encodedPath == "/oauth/token" }.dpopClaim("nonce"),
        )
        assertTrue(store.pending.isEmpty())
        assertNotNull(store.session)

        val pds = XrpcClient(network.client, session.pdsUrl, manager.pdsAuthenticator)
        val repo = pds.query(DescribeRepo.method, DescribeRepo.Params(did))
        assertEquals("alice.test", repo.handle.value)
        val retried = network.requests.last()
        assertEquals("DPoP at1", retried.headers["Authorization"])
        assertEquals("p1", retried.dpopClaim("nonce"))
        assertNotNull(retried.dpopClaim("ath"))
    }

    @Test
    fun rejectedRefreshSignsOutAndLogs() = runTest {
        store.session =
            OAuthSession(
                did = Did(did),
                handle = Handle("alice.test"),
                pdsUrl = "http://pds.test",
                issuer = "http://pds.test",
                tokenEndpoint = "http://pds.test/oauth/token",
                accessToken = "at",
                refreshToken = null,
                expiresAt = null,
                scope = "atproto",
                dpopKey = DpopKey.generate().serialize(),
            )
        manager.restore()

        assertFailsWith<OAuthException> { manager.freshSession(forceRefresh = true) }

        assertNull(store.session)
        assertNull(manager.session.value)
        logs.assertCount(1)
        logs.assertLast { severity == Severity.Warn && tag == "OAuthSessionManager" && throwable is OAuthException }
    }

    private fun HttpRequestData.dpopClaim(name: String): String? {
        val payload = headers["DPoP"]!!.split('.')[1]
        return AtJson.parseToJsonElement(Base64Url.decode(payload).decodeToString()).jsonObject[name]?.jsonPrimitive?.content
    }

    private fun HttpRequestData.form(): Map<String, String> {
        val text = (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
        return parseQueryString(text).entries().associate { it.key to it.value.single() }
    }

    private class InMemoryStore : OAuthStore {
        var session: OAuthSession? = null
        val pending = mutableMapOf<String, PendingAuthorization>()

        override suspend fun loadSession() = session

        override suspend fun saveSession(session: OAuthSession) {
            this.session = session
        }

        override suspend fun clearSession() {
            session = null
        }

        override suspend fun savePending(pending: PendingAuthorization) {
            this.pending[pending.state] = pending
        }

        override suspend fun loadPending(state: String) = pending[state]

        override suspend fun clearPending(state: String) {
            pending.remove(state)
        }
    }
}
