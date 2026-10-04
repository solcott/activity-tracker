package io.github.solcott.activitytracker.data.auth

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.oauth.PendingAuthorization
import io.github.solcott.activitytracker.data.db.inMemoryDatabase
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.runTest

class RoomOAuthStoreTest {
    private val db = inMemoryDatabase()
    private val store = RoomOAuthStore(db, Clock.System, InMemorySecretCipher(), Logger)

    private suspend fun session() =
        OAuthSession(
            did = Did("did:plc:alice"),
            handle = Handle("alice.test"),
            pdsUrl = "http://pds.test",
            issuer = "http://pds.test",
            tokenEndpoint = "http://pds.test/oauth/token",
            accessToken = "secret-access-token",
            refreshToken = "secret-refresh-token",
            expiresAt = Clock.System.now() + 1.hours,
            scope = "atproto",
            dpopKey = DpopKey.generate().serialize(),
        )

    private suspend fun pending() =
        PendingAuthorization(
            state = "state-1",
            pkceVerifier = "secret-pkce-verifier",
            dpopKey = DpopKey.generate().serialize(),
            issuer = "http://pds.test",
            tokenEndpoint = "http://pds.test/oauth/token",
            expectedDid = null,
        )

    @Test
    fun sessionRoundTripsAndIsEncryptedAtRest() = runTest {
        val session = session()
        store.saveSession(session)

        assertEquals(session, store.loadSession())
        val raw = db.keyValue().get("oauth_session")!!
        listOf(session.accessToken, session.refreshToken!!, session.dpopKey.privateKeyDer, session.did.value).forEach {
            assertFalse(it in raw, "stored session contains plaintext")
        }
    }

    @Test
    fun pendingRoundTripsAndIsEncryptedAtRest() = runTest {
        val pending = pending()
        store.savePending(pending)

        assertEquals(pending, store.loadPending(pending.state))
        val raw = db.pendingAuth().get(pending.state)!!.json
        listOf(pending.pkceVerifier, pending.dpopKey.privateKeyDer).forEach { assertFalse(it in raw, "stored pending contains plaintext") }
    }

    @Test
    fun lostKeyReadsAsSignedOut() = runTest {
        store.saveSession(session())
        store.savePending(pending())

        val otherKey = RoomOAuthStore(db, Clock.System, InMemorySecretCipher(), Logger)
        assertNull(otherKey.loadSession())
        assertNull(otherKey.loadPending("state-1"))
    }
}
