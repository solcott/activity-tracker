package io.github.solcott.activitytracker.data.auth

import androidx.compose.runtime.Immutable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.location.ActivityRecorder
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.pluginOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull

/** The account whose session last expired, so the login screen can say so and fill in its handle. */
@Immutable data class ExpiredSession(val did: Did, val handle: Handle)

@SingleIn(AppScope::class)
@Inject
class AuthRepository(
    private val sessionManager: OAuthSessionManager,
    private val db: AppDatabase,
    private val http: HttpClient,
    private val recorder: ActivityRecorder,
) {
    val session: StateFlow<OAuthSession?>
        get() = sessionManager.session

    /** Set when a session ends because it expired; cleared by the next completed sign-in or chosen sign-out. */
    val lastExpiry: StateFlow<ExpiredSession?>
        field = MutableStateFlow<ExpiredSession?>(null)

    suspend fun restore(): OAuthSession? = sessionManager.restore()

    /** Returns the authorization URL to open in a browser. */
    suspend fun beginSignIn(handleOrDid: String): String = sessionManager.beginSignIn(handleOrDid)

    /** [callback] are the query parameters of the OAuth redirect. */
    suspend fun completeSignIn(callback: Map<String, String>): OAuthSession =
        sessionManager.completeSignIn(callback).also { lastExpiry.value = null }

    suspend fun signOut() = endSession(expired = null)

    /** Ends each session the session manager finds expired, the way a sign-out does. Runs for as long as the app does. */
    suspend fun endExpiredSessions() {
        sessionManager.expired.filterNotNull().collect { endSession(expired = it) }
    }

    /** Ends the session: the athlete's chosen sign-out when [expired] is null, else [expired], whose refresh was refused. */
    private suspend fun endSession(expired: OAuthSession?) {
        if (expired == null) {
            // A recording in progress stops, kept on the device for this account's next sign-in. An expiry leaves it running, for the
            // athlete to sign back in to (record/gps-recording).
            recorder.suspendForSignOut()
            lastExpiry.value = null
        } else {
            lastExpiry.value = ExpiredSession(expired.did, expired.handle)
        }
        // An expired session's refresh token is already dead, so there's nothing to revoke.
        sessionManager.signOut(revoke = expired == null)
        db.feed().clearAll()
        // HttpCache files authenticated responses under public storage unless they're `Cache-Control: private`.
        http.pluginOrNull(HttpCache)?.clearAllCaches()
    }
}
