package io.github.solcott.activitytracker.data.auth

import androidx.compose.runtime.Immutable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.export.DataExports
import io.github.solcott.activitytracker.data.importsave.ImportSaves
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.location.ActivityRecorder
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.pluginOrNull
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext

/**
 * The account whose session last ended without the athlete leaving it, so the login screen can fill in its handle and say why
 * (auth/oauth-session).
 */
@Immutable
data class EndedSession(val did: Did, val handle: Handle, val reason: Reason) {
    enum class Reason {
        /** Its refresh was refused. */
        Expired,

        /** The athlete chose Sign in again, to renew the session's grant (auth/account-page). */
        SignInAgain,

        /** The athlete left Activity Tracker, here or on another device (auth/leaving); their atproto account is unchanged. */
        Left,
    }
}

@SingleIn(AppScope::class)
@Inject
class AuthRepository(
    private val sessionManager: OAuthSessionManager,
    private val db: AppDatabase,
    private val http: HttpClient,
    private val recorder: ActivityRecorder,
    private val saves: ImportSaves,
    private val exports: DataExports,
) {
    val session: StateFlow<OAuthSession?>
        get() = sessionManager.session

    /**
     * Set when a session ends because it expired or the athlete chose Sign in again; cleared by the next completed sign-in or chosen
     * sign-out, so a sign-in cancelled in the browser keeps it.
     */
    val lastEnd: StateFlow<EndedSession?>
        field = MutableStateFlow<EndedSession?>(null)

    suspend fun restore(): OAuthSession? = sessionManager.restore()

    /** Returns the authorization URL to open in a browser. */
    suspend fun beginSignIn(handleOrDid: String): String = sessionManager.beginSignIn(handleOrDid)

    /** [callback] are the query parameters of the OAuth redirect. */
    suspend fun completeSignIn(callback: Map<String, String>): OAuthSession =
        sessionManager.completeSignIn(callback).also { lastEnd.value = null }

    suspend fun signOut() = endSession(expired = null)

    /**
     * Ends the session as a chosen sign-out does, then keeps its account for the login screen, which fills in the handle and says signing
     * in again renews the app's permissions (auth/account-page).
     */
    suspend fun signInAgain() {
        val ending = sessionManager.session.value ?: return
        endSession(expired = null, signingInAgain = ending)
    }

    /**
     * Ends the session after the athlete left Activity Tracker (auth/leaving), or discarded what this device held after they left on
     * another one: as a chosen sign-out, with the login screen saying they left. What the leave discarded is gone already, so nothing is
     * kept for a next sign-in.
     */
    suspend fun left() {
        val ending = sessionManager.session.value ?: return
        withContext(NonCancellable) {
            saves.signedOut(ending.did)
            exports.signedOut(ending.did)
            lastEnd.value = EndedSession(ending.did, ending.handle, EndedSession.Reason.Left)
            sessionManager.signOut(revoke = true)
            db.feed().clearAll()
            http.pluginOrNull(HttpCache)?.clearAllCaches()
        }
    }

    /** Ends each session the session manager finds expired, the way a sign-out does. Runs for as long as the app does. */
    suspend fun endExpiredSessions() {
        sessionManager.expired.filterNotNull().collect { endSession(expired = it) }
    }

    /**
     * Ends the session: the athlete's chosen sign-out when [expired] is null, else [expired], whose refresh was refused. It runs to the end
     * even if its caller is cancelled: dropping the session drops the session's screens, and with them the scope that called this, while
     * the caches it clears still hold other athletes' non-public data.
     */
    private suspend fun endSession(expired: OAuthSession?, signingInAgain: OAuthSession? = null) {
        withContext(NonCancellable) { endSessionNow(expired, signingInAgain) }
    }

    private suspend fun endSessionNow(expired: OAuthSession?, signingInAgain: OAuthSession?) {
        if (expired == null) {
            // A recording in progress stops, kept on the device for this account's next sign-in. An expiry leaves it running, for the
            // athlete to sign back in to (record/gps-recording).
            recorder.suspendForSignOut()
            // A batch save stops and its unsaved activities go (feed/batch-import); an expiry leaves it to resume after signing back in.
            sessionManager.session.value?.did?.let { saves.signedOut(it) }
            // A data export stops and is deleted, staged files and all (auth/data-export).
            sessionManager.session.value?.did?.let { exports.signedOut(it) }
            lastEnd.value = signingInAgain?.let { EndedSession(it.did, it.handle, EndedSession.Reason.SignInAgain) }
        } else {
            lastEnd.value = EndedSession(expired.did, expired.handle, EndedSession.Reason.Expired)
        }
        // An expired session's refresh token is already dead, so there's nothing to revoke.
        sessionManager.signOut(revoke = expired == null)
        db.feed().clearAll()
        // HttpCache files authenticated responses under public storage unless they're `Cache-Control: private`.
        http.pluginOrNull(HttpCache)?.clearAllCaches()
    }
}
