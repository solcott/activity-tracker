package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.oauth.OAuthStore
import io.github.solcott.activitytracker.atproto.oauth.PendingAuthorization

class InMemoryOAuthStore : OAuthStore {
    var session: OAuthSession? = null
    val pending = mutableMapOf<String, PendingAuthorization>()

    override suspend fun loadSession() = session

    override suspend fun saveSession(session: OAuthSession) {
        this.session = session
    }

    override suspend fun clearSession() {
        session = null
    }

    override suspend fun savePending(pending: PendingAuthorization) {
        this.pending[pending.state] = pending
    }

    override suspend fun loadPending(state: String) = pending[state]

    override suspend fun clearPending(state: String) {
        pending.remove(state)
    }
}
