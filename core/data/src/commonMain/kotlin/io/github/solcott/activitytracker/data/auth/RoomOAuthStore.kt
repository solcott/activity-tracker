package io.github.solcott.activitytracker.data.auth

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.oauth.OAuthStore
import io.github.solcott.activitytracker.atproto.oauth.PendingAuthorization
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.KeyValueEntity
import io.github.solcott.activitytracker.data.db.PendingAuthEntity
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Persists OAuth state in Room, encrypted with [cipher] because it holds tokens, the DPoP private key, and the PKCE verifier.
 *
 * A value that can't be decrypted (the platform key was lost, e.g. after a backup restore or cleared site data) reads as absent, so the
 * user is simply signed out.
 */
@ContributesBinding(AppScope::class)
@Inject
class RoomOAuthStore(
    private val db: AppDatabase,
    private val clock: Clock,
    private val cipher: SecretCipher,
    logger: Logger,
) : OAuthStore {
    private val log = logger.withTag("RoomOAuthStore")

    override suspend fun loadSession(): OAuthSession? = db.keyValue().get(SESSION_KEY)?.let { decryptOrNull(it, OAuthSession::decode) }

    override suspend fun saveSession(session: OAuthSession) =
        db.keyValue().put(KeyValueEntity(SESSION_KEY, cipher.encrypt(session.encode())))

    override suspend fun clearSession() = db.keyValue().delete(SESSION_KEY)

    override suspend fun savePending(pending: PendingAuthorization) =
        db.pendingAuth()
            .put(
                PendingAuthEntity(
                    pending.state,
                    cipher.encrypt(pending.encode()),
                    clock.now().toEpochMilliseconds(),
                )
            )

    override suspend fun loadPending(state: String): PendingAuthorization? =
        db.pendingAuth().get(state)?.let { decryptOrNull(it.json, PendingAuthorization::decode) }

    override suspend fun clearPending(state: String) =
        db.pendingAuth().delete(state, olderThan = (clock.now() - 30.minutes).toEpochMilliseconds())

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> decryptOrNull(value: String, decode: (String) -> T): T? =
        try {
            decode(cipher.decrypt(value))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Discarding unreadable stored OAuth state" }
            null
        }

    private companion object {
        const val SESSION_KEY = "oauth_session"
    }
}
