package io.github.solcott.activitytracker.atproto.oauth

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.xrpc.RequestAuthenticator
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the signed-in [OAuthSession]: persists it, refreshes tokens before expiry, and authenticates PDS requests with DPoP
 * ([pdsAuthenticator]). Every write of the session (a refresh, a nonce, signing in and signing out) runs under one lock, so none undoes
 * another.
 */
class OAuthSessionManager(
    private val client: OAuthClient,
    private val store: OAuthStore,
    private val clock: Clock = Clock.System,
    logger: Logger = Logger,
) {
    val session: StateFlow<OAuthSession?>
        field = MutableStateFlow<OAuthSession?>(null)

    /**
     * The session a refresh found dead ([SessionExpiredException]), until [signOut] ends it. It stays in [session] meanwhile, and every
     * request fails at once without asking the authorization server again.
     */
    val expired: StateFlow<OAuthSession?>
        field = MutableStateFlow<OAuthSession?>(null)

    private val log = logger.withTag("OAuthSessionManager")
    private val mutex = Mutex()
    private val proofs = DpopProofs(clock)
    private var cachedKey: Pair<DpopKey.Serialized, DpopKey>? = null

    suspend fun restore(): OAuthSession? = store.loadSession().also { session.value = it }

    /** Returns the URL to open in a browser. */
    suspend fun beginSignIn(identifier: String): String {
        val request = client.startAuthorization(identifier)
        store.savePending(request.pending)
        return request.authorizeUrl
    }

    /** Completes sign-in from the redirect URI's query parameters. */
    suspend fun completeSignIn(callback: Map<String, String>): OAuthSession {
        val state = callback["state"] ?: throw OAuthException("Missing state")
        val pending = store.loadPending(state) ?: throw OAuthException("No sign-in in progress for this state")
        try {
            val session = client.completeAuthorization(pending, callback)
            mutex.withLock {
                store.saveSession(session)
                expired.value = null
                this.session.value = session
            }
            return session
        } finally {
            store.clearPending(state)
        }
    }

    /**
     * Forgets the session, revoking its newest refresh token unless [revoke] is false (an [expired] session's is already dead). Waits for a
     * refresh in flight, so that refresh can't save the session again afterwards.
     */
    suspend fun signOut(revoke: Boolean = true) = mutex.withLock {
        val current = session.value
        expired.value = null
        if (current == null) return@withLock
        if (revoke) client.revoke(current)
        store.clearSession()
        session.value = null
    }

    /**
     * The current session, refreshed if it expires within a minute. Throws [SessionExpiredException] once a refresh found it dead, and
     * passes on any other refresh failure, keeping the session for the next request to try again.
     */
    suspend fun freshSession(forceRefresh: Boolean = false): OAuthSession = mutex.withLock {
        val current = liveSession()
        val expiring = current.expiresAt?.let { it - 60.seconds < clock.now() } ?: false
        if (!forceRefresh && !expiring) return current
        refresh(current)
    }

    /** Refreshes the session if [accessToken] is still its access token: a token another request already replaced needs no refresh. */
    private suspend fun refreshIfCurrent(accessToken: String) {
        mutex.withLock {
            val current = liveSession()
            if (current.accessToken == accessToken) refresh(current)
        }
    }

    /** The session, unless there's none or it's [expired]. Call under [mutex]. */
    private fun liveSession(): OAuthSession {
        val current = session.value ?: throw OAuthException("Not signed in")
        if (expired.value != null) throw SessionExpiredException("Session expired")
        return current
    }

    /**
     * Refreshes [current], unless the store already holds a newer session for the same account: another tab sharing the store (the web's
     * OPFS) refreshed it, which rotated the refresh token [current] holds, so refreshing with it would fail as expired. Call under [mutex].
     */
    private suspend fun refresh(current: OAuthSession): OAuthSession =
        rotatedElsewhere(current)
            ?: try {
                client.refresh(current).also {
                    store.saveSession(it)
                    session.value = it
                }
            } catch (e: SessionExpiredException) {
                // Another tab may have rotated the token while this refresh was on its way.
                rotatedElsewhere(current)
                    ?: run {
                        log.w(e) { "Session expired; signing out" }
                        expired.value = current
                        throw e
                    }
            }

    /** The stored session, taken as current, when it's [current]'s account with another refresh token; else null. Call under [mutex]. */
    private suspend fun rotatedElsewhere(current: OAuthSession): OAuthSession? =
        store.loadSession()?.takeIf { it.did == current.did && it.refreshToken != current.refreshToken }?.also { session.value = it }

    private suspend fun updatePdsNonce(nonce: String) = mutex.withLock {
        val current = session.value ?: return@withLock
        if (current.pdsNonce == nonce) return@withLock
        val updated = current.copy(pdsNonce = nonce)
        session.value = updated
        store.saveSession(updated)
    }

    private suspend fun keyFor(session: OAuthSession): DpopKey {
        cachedKey?.let { (serialized, key) -> if (serialized == session.dpopKey) return key }
        return DpopKey.restore(session.dpopKey).also { cachedKey = session.dpopKey to it }
    }

    /** DPoP authentication for requests to the signed-in account's PDS. */
    val pdsAuthenticator: RequestAuthenticator =
        object : RequestAuthenticator {
            override suspend fun authenticate(request: HttpRequestBuilder, nsid: String) {
                val session = freshSession()
                val url = request.url.buildString()
                request.header(HttpHeaders.Authorization, "$DPOP_SCHEME${session.accessToken}")
                request.header(
                    "DPoP",
                    proofs.create(
                        keyFor(session),
                        request.method.value,
                        url,
                        session.pdsNonce,
                        session.accessToken,
                    ),
                )
            }

            override suspend fun shouldRetry(response: HttpResponse): Boolean {
                response.dpopNonce()?.let { updatePdsNonce(it) }
                if (response.status.value != HttpStatusCode.Unauthorized.value) return false
                val challenge = response.headers[HttpHeaders.WWWAuthenticate].orEmpty()
                return when {
                    "use_dpop_nonce" in challenge -> {
                        true
                    }

                    "invalid_token" in challenge -> {
                        // The token this request carried, as authenticate wrote it.
                        response.request.headers[HttpHeaders.Authorization]?.removePrefix(DPOP_SCHEME)?.let { refreshIfCurrent(it) }
                        true
                    }

                    else -> {
                        false
                    }
                }
            }
        }
}

private const val DPOP_SCHEME = "DPoP "
