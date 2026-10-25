package io.github.solcott.activitytracker.data.auth

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.location.ActivityRecorder
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.pluginOrNull
import kotlinx.coroutines.flow.StateFlow

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

    suspend fun restore(): OAuthSession? = sessionManager.restore()

    /** Returns the authorization URL to open in a browser. */
    suspend fun beginSignIn(handleOrDid: String): String = sessionManager.beginSignIn(handleOrDid)

    /** [callback] are the query parameters of the OAuth redirect. */
    suspend fun completeSignIn(callback: Map<String, String>): OAuthSession = sessionManager.completeSignIn(callback)

    suspend fun signOut() {
        // A recording in progress stops, kept on the device for this account's next sign-in (record/gps-recording).
        recorder.suspendForSignOut()
        sessionManager.signOut()
        db.feed().clearAll()
        // HttpCache files authenticated responses under public storage unless they're `Cache-Control: private`.
        http.pluginOrNull(HttpCache)?.clearAllCaches()
    }
}
