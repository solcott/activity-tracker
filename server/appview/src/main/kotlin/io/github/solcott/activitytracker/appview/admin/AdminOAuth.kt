package io.github.solcott.activitytracker.appview.admin

import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.oauth.AuthorizationRequest
import io.github.solcott.activitytracker.atproto.oauth.OAuthClient
import io.github.solcott.activitytracker.atproto.oauth.PendingAuthorization
import io.github.solcott.activitytracker.lexicon.Did
import io.ktor.client.HttpClient

/**
 * Proves an admin's identity through atproto OAuth (admin/admin-site). [start] begins a sign-in at the account's authorization server;
 * [complete] finishes it and returns the DID the server vouches for, having given up the tokens it issued, so the AppView never holds any.
 */
interface AdminOAuth {
    suspend fun start(identifier: String, client: OAuthClientConfig): AuthorizationRequest

    suspend fun complete(client: OAuthClientConfig, pending: PendingAuthorization, callback: Map<String, String>): Did

    companion object {
        /** Identity only: a token with this scope grants nothing on the account. */
        const val SCOPE = "atproto"
    }
}

/**
 * [AdminOAuth] over `core/atproto`'s [OAuthClient], which does PAR, PKCE, DPoP, the issuer check and the check that the authorization
 * server is authoritative for the account. [http] and [identity] must be the guarded ones: the identifier and every host it leads to are
 * untrusted.
 */
class AtprotoAdminOAuth(private val http: HttpClient, private val identity: IdentityResolver) : AdminOAuth {
    override suspend fun start(identifier: String, client: OAuthClientConfig): AuthorizationRequest =
        OAuthClient(http, identity, client).startAuthorization(identifier)

    override suspend fun complete(client: OAuthClientConfig, pending: PendingAuthorization, callback: Map<String, String>): Did {
        val oauth = OAuthClient(http, identity, client)
        val session = oauth.completeAuthorization(pending, callback)
        // Best effort, and the tokens were only ever in this call's memory.
        oauth.revoke(session)
        return session.did
    }
}
