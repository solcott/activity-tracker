package io.github.solcott.activitytracker.atproto.oauth

import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.crypto.base64Url
import io.github.solcott.activitytracker.atproto.crypto.randomToken
import io.github.solcott.activitytracker.atproto.crypto.sha256
import io.github.solcott.activitytracker.atproto.encodeUriComponent
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.KSerializer

/**
 * ATProto OAuth public client: identity → PDS → authorization server discovery, PAR with PKCE (S256), DPoP-bound tokens, and refresh.
 * Stateless; persistence lives in [OAuthSessionManager].
 */
class OAuthClient(
    private val http: HttpClient,
    private val identity: IdentityResolver,
    private val config: OAuthClientConfig,
    private val clock: Clock = Clock.System,
) {
    private val proofs = DpopProofs(clock)

    /** Starts sign-in for a handle or DID. Open [AuthorizationRequest.authorizeUrl] in a browser. */
    suspend fun startAuthorization(identifier: String): AuthorizationRequest {
        val account = identity.resolve(identifier)
        val server = authorizationServerFor(account.pdsUrl)

        val verifier = randomToken(32)
        val challenge = sha256(verifier.encodeToByteArray()).base64Url()
        val state = randomToken(16)
        val key = DpopKey.generate()

        val params = Parameters.build {
            append("client_id", config.clientId)
            append("response_type", "code")
            append("redirect_uri", config.redirectUri)
            append("scope", config.scope)
            append("state", state)
            append("code_challenge", challenge)
            append("code_challenge_method", "S256")
            append("login_hint", identifier.trim().removePrefix("@"))
        }
        val (par, nonce) = postWithDpop(server.parEndpoint, params, key, null, ParResponse.serializer())

        val authorizeUrl =
            server.authorizationEndpoint +
                "?client_id=${config.clientId.encodeUriComponent()}&request_uri=${par.requestUri.encodeUriComponent()}"
        return AuthorizationRequest(
            authorizeUrl = authorizeUrl,
            pending =
                PendingAuthorization(
                    state = state,
                    pkceVerifier = verifier,
                    dpopKey = key.serialize(),
                    issuer = server.issuer,
                    tokenEndpoint = server.tokenEndpoint,
                    revocationEndpoint = server.revocationEndpoint,
                    expectedDid = account.did,
                    dpopNonce = nonce,
                ),
        )
    }

    /** Exchanges the redirect's `code` for tokens. [callback] are the redirect URI's query params. */
    suspend fun completeAuthorization(
        pending: PendingAuthorization,
        callback: Map<String, String>,
    ): OAuthSession {
        callback["error"]?.let { throw OAuthException("Authorization failed: $it ${callback["error_description"].orEmpty()}") }
        if (callback["state"] != pending.state) throw OAuthException("State mismatch")
        if (callback["iss"] != null && callback["iss"] != pending.issuer) throw OAuthException("Issuer mismatch")
        val code = callback["code"] ?: throw OAuthException("Missing authorization code")

        val key = DpopKey.restore(pending.dpopKey)
        val params = Parameters.build {
            append("grant_type", "authorization_code")
            append("client_id", config.clientId)
            append("redirect_uri", config.redirectUri)
            append("code", code)
            append("code_verifier", pending.pkceVerifier)
        }
        val (token, nonce) =
            postWithDpop(
                pending.tokenEndpoint,
                params,
                key,
                pending.dpopNonce,
                TokenResponse.serializer(),
            )

        // The token's `sub` must be an account whose PDS delegates to this same authorization
        // server;
        // otherwise a malicious server could mint tokens for someone else's DID.
        val sub = Did(token.sub)
        if (pending.expectedDid != null && pending.expectedDid != sub) {
            throw OAuthException("Signed in as unexpected account $sub")
        }
        val account = identity.resolve(sub.value)
        if (authorizationServerFor(account.pdsUrl).issuer != pending.issuer) {
            throw OAuthException("Authorization server is not authoritative for $sub")
        }
        if ("atproto" !in token.scope.split(' ')) throw OAuthException("Token is missing the atproto scope")

        return OAuthSession(
            did = sub,
            handle = account.handle,
            pdsUrl = account.pdsUrl,
            issuer = pending.issuer,
            tokenEndpoint = pending.tokenEndpoint,
            revocationEndpoint = pending.revocationEndpoint,
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            expiresAt = token.expiresIn?.let { clock.now() + it.seconds },
            scope = token.scope,
            dpopKey = pending.dpopKey,
            authServerNonce = nonce,
        )
    }

    suspend fun refresh(session: OAuthSession): OAuthSession {
        val refreshToken = session.refreshToken ?: throw OAuthException("Session has no refresh token")
        val key = DpopKey.restore(session.dpopKey)
        val params = Parameters.build {
            append("grant_type", "refresh_token")
            append("client_id", config.clientId)
            append("refresh_token", refreshToken)
        }
        val (token, nonce) =
            postWithDpop(
                session.tokenEndpoint,
                params,
                key,
                session.authServerNonce,
                TokenResponse.serializer(),
            )
        if (token.sub != session.did.value) throw OAuthException("Refresh returned a different account")
        return session.copy(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken ?: refreshToken,
            expiresAt = token.expiresIn?.let { clock.now() + it.seconds },
            scope = token.scope,
            authServerNonce = nonce,
        )
    }

    /** Best-effort token revocation on sign-out. */
    suspend fun revoke(session: OAuthSession) {
        val endpoint = session.revocationEndpoint ?: return
        runSuspendCatching {
            val key = DpopKey.restore(session.dpopKey)
            val params = Parameters.build {
                append("client_id", config.clientId)
                append("token", session.refreshToken ?: session.accessToken)
            }
            val proof = proofs.create(key, "POST", endpoint, session.authServerNonce)
            http.submitForm(endpoint, params) { header("DPoP", proof) }
        }
    }

    private suspend fun authorizationServerFor(pdsUrl: String): AuthorizationServerMetadata {
        val resource =
            getJson(
                "$pdsUrl/.well-known/oauth-protected-resource",
                ProtectedResourceMetadata.serializer(),
            )
        val issuer = resource.authorizationServers.firstOrNull() ?: throw OAuthException("PDS $pdsUrl lists no authorization server")
        val metadata =
            getJson(
                "$issuer/.well-known/oauth-authorization-server",
                AuthorizationServerMetadata.serializer(),
            )
        if (metadata.issuer != issuer) throw OAuthException("Authorization server issuer mismatch")
        return metadata
    }

    private suspend fun <T> getJson(url: String, serializer: KSerializer<T>): T {
        val response = http.get(url)
        if (!response.status.isSuccess()) throw OAuthException("GET $url failed: ${response.status}")
        return AtJson.decodeFromString(serializer, response.bodyAsText())
    }

    /** POSTs a form with a DPoP proof, retrying once when the server demands a fresh nonce. */
    private suspend fun <T> postWithDpop(
        url: String,
        params: Parameters,
        key: DpopKey,
        initialNonce: String?,
        serializer: KSerializer<T>,
    ): Pair<T, String?> {
        var nonce = initialNonce
        repeat(2) { attempt ->
            val proof = proofs.create(key, "POST", url, nonce)
            val response = http.submitForm(url, params) { header("DPoP", proof) }
            response.dpopNonce()?.let { nonce = it }
            val body = response.bodyAsText()
            if (response.status.isSuccess()) return AtJson.decodeFromString(serializer, body) to nonce
            val error = runCatching {
                AtJson.decodeFromString(OAuthErrorResponse.serializer(), body)
            }
                .getOrNull()
            if (error?.error == "use_dpop_nonce" && attempt == 0) return@repeat
            throw OAuthException(
                "POST $url failed: ${response.status} ${error?.error ?: body.take(
                    300
                )} ${error?.description.orEmpty()}"
            )
        }
        throw OAuthException("POST $url failed: DPoP nonce not accepted")
    }
}

internal fun HttpResponse.dpopNonce(): String? = headers["DPoP-Nonce"]
