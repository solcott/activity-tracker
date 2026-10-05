package io.github.solcott.activitytracker.data.di

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.oauth.OAuthClient
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
import io.github.solcott.activitytracker.data.auth.InMemorySecretCipher
import io.github.solcott.activitytracker.data.auth.RoomOAuthStore
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.inMemoryDatabase
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.ktor.client.HttpClient
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/** For tests: [did] signed in to `pds.test` and `appview.test`, both served by a mock [HttpClient]. The desktop app never uses it. */
data class TestSession(val db: AppDatabase, val manager: OAuthSessionManager, val clients: SessionClients)

/** Saves a fresh session for [did] in an in-memory database and restores it, so every request goes through [http]. */
suspend fun signedInTestSession(http: HttpClient, did: Did = Did("did:plc:alice"), logger: Logger = Logger): TestSession {
    val db = inMemoryDatabase()
    val store = RoomOAuthStore(db, Clock.System, InMemorySecretCipher(), logger)
    val session =
        OAuthSession(
            did = did,
            handle = Handle("alice.test"),
            pdsUrl = "http://pds.test",
            issuer = "http://pds.test",
            tokenEndpoint = "http://pds.test/oauth/token",
            accessToken = "at",
            refreshToken = "rt",
            expiresAt = Clock.System.now() + 1.hours,
            scope = "atproto",
            dpopKey = DpopKey.generate().serialize(),
        )
    store.saveSession(session)
    val config =
        AtprotoConfig(
            "http://plc.test",
            null,
            "http://appview.test",
            "did:web:appview.test",
            OAuthClientConfig.loopback("http://127.0.0.1/"),
        )
    val manager = OAuthSessionManager(OAuthClient(http, IdentityResolver(http, config), config.oauth), store).apply { restore() }
    return TestSession(db, manager, SessionClients(session, http, manager, config, Clock.System))
}
