package io.github.solcott.activitytracker.data.auth

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.oauth.OAuthStore
import io.github.solcott.activitytracker.atproto.oauth.PendingAuthorization
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.KeyValueEntity
import io.github.solcott.activitytracker.data.db.PendingAuthEntity
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Persists OAuth state in Room.
 *
 * TODO: wrap tokens + DPoP key with platform secure storage (Android Keystore / iOS Keychain).
 */
@ContributesBinding(AppScope::class)
@Inject
class RoomOAuthStore(private val db: AppDatabase, private val clock: Clock) : OAuthStore {
    override suspend fun loadSession(): OAuthSession? =
        db.keyValue().get(SESSION_KEY)?.let { runCatching { OAuthSession.decode(it) }.getOrNull() }

    override suspend fun saveSession(session: OAuthSession) = db.keyValue().put(KeyValueEntity(SESSION_KEY, session.encode()))

    override suspend fun clearSession() = db.keyValue().delete(SESSION_KEY)

    override suspend fun savePending(pending: PendingAuthorization) =
        db.pendingAuth()
            .put(
                PendingAuthEntity(
                    pending.state,
                    pending.encode(),
                    clock.now().toEpochMilliseconds(),
                )
            )

    override suspend fun loadPending(state: String): PendingAuthorization? =
        db.pendingAuth().get(state)?.let { PendingAuthorization.decode(it.json) }

    override suspend fun clearPending(state: String) =
        db.pendingAuth().delete(state, olderThan = (clock.now() - 30.minutes).toEpochMilliseconds())

    private companion object {
        const val SESSION_KEY = "oauth_session"
    }
}
