package io.github.solcott.activitytracker.atproto.oauth

import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.xrpc.RequestAuthenticator
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the signed-in [OAuthSession]: persists it, refreshes tokens before expiry, and authenticates PDS requests with DPoP
 * ([pdsAuthenticator]).
 */
class OAuthSessionManager(
    private val client: OAuthClient,
    private val store: OAuthStore,
    private val clock: Clock = Clock.System,
) {
    private val _session = MutableStateFlow<OAuthSession?>(null)
    val session: StateFlow<OAuthSession?> = _session.asStateFlow()

    private val mutex = Mutex()
    private val proofs = DpopProofs(clock)
    private var cachedKey: Pair<DpopKey.Serialized, DpopKey>? = null

    suspend fun restore(): OAuthSession? = store.loadSession().also { _session.value = it }

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
            store.saveSession(session)
            _session.value = session
            return session
        } finally {
            store.clearPending(state)
        }
    }

    suspend fun signOut() {
        val current = _session.value ?: return
        client.revoke(current)
        store.clearSession()
        _session.value = null
    }

    /** The current session, refreshed if it expires within a minute. */
    suspend fun freshSession(forceRefresh: Boolean = false): OAuthSession = mutex.withLock {
        val current = _session.value ?: throw OAuthException("Not signed in")
        val expiring = current.expiresAt?.let { it - 60.seconds < clock.now() } ?: false
        if (!forceRefresh && !expiring) return current
        val refreshed =
            try {
                client.refresh(current)
            } catch (e: OAuthException) {
                // Refresh token rejected: the session is dead.
                store.clearSession()
                _session.value = null
                throw e
            }
        store.saveSession(refreshed)
        _session.value = refreshed
        refreshed
    }

    private suspend fun updatePdsNonce(nonce: String) = mutex.withLock {
        val current = _session.value ?: return@withLock
        if (current.pdsNonce == nonce) return@withLock
        val updated = current.copy(pdsNonce = nonce)
        _session.value = updated
        store.saveSession(updated)
    }

    private suspend fun keyFor(session: OAuthSession): DpopKey {
        cachedKey?.let { (serialized, key) -> if (serialized == session.dpopKey) return key }
        return DpopKey.restore(session.dpopKey).also { cachedKey = session.dpopKey to it }
    }

    /** DPoP authentication for requests to the signed-in account's PDS. */
    val pdsAuthenticator: RequestAuthenticator =
        object : RequestAuthenticator {
            private var lastTokenRejected = false

            override suspend fun authenticate(request: HttpRequestBuilder, nsid: String) {
                val session = freshSession(forceRefresh = lastTokenRejected.also { lastTokenRejected = false })
                val url = request.url.buildString()
                request.header(HttpHeaders.Authorization, "DPoP ${session.accessToken}")
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
                if (response.status.value != 401) return false
                val challenge = response.headers[HttpHeaders.WWWAuthenticate].orEmpty()
                return when {
                    "use_dpop_nonce" in challenge -> {
                        true
                    }

                    "invalid_token" in challenge -> {
                        lastTokenRejected = true
                        true
                    }

                    else -> {
                        false
                    }
                }
            }
        }
}
