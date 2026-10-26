package io.github.solcott.activitytracker.data.auth

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.oauth.OAuthClient
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
import io.github.solcott.activitytracker.atproto.oauth.PendingAuthorization
import io.github.solcott.activitytracker.atproto.oauth.SessionExpiredException
import io.github.solcott.activitytracker.data.db.FeedItemEntity
import io.github.solcott.activitytracker.data.db.inMemoryDatabase
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.location.ActivityRecorder
import io.github.solcott.activitytracker.location.FakeLocationTracker
import io.github.solcott.activitytracker.location.RecordingState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class AuthRepositoryTest {
    private val did = "did:plc:alice"
    private val requests = mutableListOf<HttpRequestData>()
    private var tokenAnswer: Pair<HttpStatusCode, String> =
        HttpStatusCode.BadRequest to """{"error":"invalid_grant","error_description":"revoked"}"""
    private val routes =
        mapOf(
            "/oauth/token" to { tokenAnswer },
            "/oauth/revoke" to { HttpStatusCode.OK to "{}" },
            "/$did" to
                {
                    HttpStatusCode.OK to
                        """{"id":"$did","alsoKnownAs":["at://alice.test"],
                   "service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"http://pds.test"}]}"""
                },
            "/xrpc/com.atproto.identity.resolveHandle" to { HttpStatusCode.OK to """{"did":"$did"}""" },
            "/.well-known/oauth-protected-resource" to
                {
                    HttpStatusCode.OK to """{"resource":"http://pds.test","authorization_servers":["http://pds.test"]}"""
                },
            "/.well-known/oauth-authorization-server" to
                {
                    HttpStatusCode.OK to
                        """{"issuer":"http://pds.test","authorization_endpoint":"http://pds.test/oauth/authorize",
                   "token_endpoint":"http://pds.test/oauth/token","pushed_authorization_request_endpoint":"http://pds.test/oauth/par"}"""
                },
        )
    private val http =
        HttpClient(
            MockEngine { request ->
                requests += request
                val (status, body) = routes[request.url.encodedPath]?.invoke() ?: (HttpStatusCode.NotFound to "")
                respond(body, status, headersOf("Content-Type", "application/json"))
            }
        ) {
            expectSuccess = false
        }
    private val db = inMemoryDatabase()
    private val store = RoomOAuthStore(db, Clock.System, InMemorySecretCipher(), Logger)
    private val manager =
        OAuthSessionManager(
            OAuthClient(
                http,
                IdentityResolver(http, "http://plc.test", "http://pds.test"),
                OAuthClientConfig.loopback("http://127.0.0.1:8080/"),
            ),
            store,
        )

    private fun TestScope.recorder() =
        ActivityRecorder(FakeLocationTracker(), Clock.System, dispatcher = StandardTestDispatcher(testScheduler))

    private suspend fun signedIn(): OAuthSession {
        val session =
            OAuthSession(
                did = Did(did),
                handle = Handle("alice.test"),
                pdsUrl = "http://pds.test",
                issuer = "http://pds.test",
                tokenEndpoint = "http://pds.test/oauth/token",
                revocationEndpoint = "http://pds.test/oauth/revoke",
                accessToken = "at1",
                refreshToken = "rt1",
                expiresAt = null,
                scope = "atproto",
                dpopKey = DpopKey.generate().serialize(),
            )
        store.saveSession(session)
        manager.restore()
        db.feed().upsert(listOf(FeedItemEntity("timeline", "at://did:plc:bob/app.activitytracker.feed.activity/1", 0, "{}")))
        return session
    }

    private val revocations
        get() = requests.count { it.url.encodedPath == "/oauth/revoke" }

    @Test
    fun anExpiredSessionIsClearedLikeASignOutButKeepsRecording() = runTest {
        signedIn()
        val recorder = recorder()
        val auth = AuthRepository(manager, db, http, recorder)
        backgroundScope.launch { auth.endExpiredSessions() }
        recorder.start(did)
        advanceUntilIdle()

        assertFailsWith<SessionExpiredException> { manager.freshSession(forceRefresh = true) }
        auth.session.first { it == null }

        assertNull(store.loadSession())
        assertEquals(emptyList(), db.feed().byUri("at://did:plc:bob/app.activitytracker.feed.activity/1"))
        assertEquals(ExpiredSession(Did(did), Handle("alice.test")), auth.lastExpiry.value)
        assertEquals(0, revocations)
        assertNull(manager.expired.value)
        advanceUntilIdle()
        assertEquals(RecordingState.Status.Recording, recorder.state.value.status)
    }

    @Test
    fun aChosenSignOutStopsTheRecordingAndRevokes() = runTest {
        signedIn()
        val recorder = recorder()
        val auth = AuthRepository(manager, db, http, recorder)
        recorder.start(did)
        advanceUntilIdle()

        auth.signOut()

        assertNull(auth.session.value)
        assertEquals(1, revocations)
        assertNull(auth.lastExpiry.value)
        assertEquals(RecordingState.Status.Idle, recorder.state.value.status)
        assertEquals(emptyList(), db.feed().byUri("at://did:plc:bob/app.activitytracker.feed.activity/1"))
    }

    @Test
    fun aCompletedSignInClearsTheExpiry() = runTest {
        signedIn()
        val auth = AuthRepository(manager, db, http, recorder())
        backgroundScope.launch { auth.endExpiredSessions() }
        assertFailsWith<SessionExpiredException> { manager.freshSession(forceRefresh = true) }
        auth.lastExpiry.first { it != null }
        auth.session.first { it == null }

        val key = DpopKey.generate()
        store.savePending(
            PendingAuthorization(
                state = "s1",
                pkceVerifier = "v",
                dpopKey = key.serialize(),
                issuer = "http://pds.test",
                tokenEndpoint = "http://pds.test/oauth/token",
                expectedDid = Did(did),
            )
        )
        tokenAnswer =
            HttpStatusCode.OK to
                """{"access_token":"at2","token_type":"DPoP","refresh_token":"rt2","expires_in":3600,"scope":"atproto","sub":"$did"}"""
        auth.completeSignIn(mapOf("code" to "c", "state" to "s1", "iss" to "http://pds.test"))

        assertNull(auth.lastExpiry.value)
        assertEquals("at2", auth.session.value?.accessToken)
    }
}
