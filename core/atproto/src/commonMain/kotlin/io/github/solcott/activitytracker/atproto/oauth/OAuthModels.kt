package io.github.solcott.activitytracker.atproto.oauth

import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ProtectedResourceMetadata(
    val resource: String,
    @SerialName("authorization_servers") val authorizationServers: List<String>,
)

@Serializable
internal data class AuthorizationServerMetadata(
    val issuer: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("pushed_authorization_request_endpoint") val parEndpoint: String,
    @SerialName("revocation_endpoint") val revocationEndpoint: String? = null,
    @SerialName("dpop_signing_alg_values_supported") val dpopAlgs: List<String> = emptyList(),
)

@Serializable
internal data class ParResponse(
    @SerialName("request_uri") val requestUri: String,
    @SerialName("expires_in") val expiresIn: Long,
)

@Serializable
internal data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val scope: String,
    val sub: String,
)

@Serializable
internal data class OAuthErrorResponse(
    val error: String,
    @SerialName("error_description") val description: String? = null,
)

/**
 * Everything needed to finish an authorization after the browser round-trip. Persisted before the browser opens because on web the page is
 * unloaded by the redirect.
 */
@Serializable
data class PendingAuthorization(
    val state: String,
    val pkceVerifier: String,
    val dpopKey: DpopKey.Serialized,
    val issuer: String,
    val tokenEndpoint: String,
    val revocationEndpoint: String? = null,
    /** Known up front when the user signed in with a handle/DID. */
    val expectedDid: Did?,
    val dpopNonce: String? = null,
) {
    fun encode(): String = AtJson.encodeToString(serializer(), this)

    companion object {
        fun decode(value: String): PendingAuthorization = AtJson.decodeFromString(serializer(), value)
    }
}

data class AuthorizationRequest(val authorizeUrl: String, val pending: PendingAuthorization)

/** An authenticated account. Tokens are DPoP-bound to [dpopKey]. */
@Serializable
data class OAuthSession(
    val did: Did,
    val handle: Handle,
    val pdsUrl: String,
    val issuer: String,
    val tokenEndpoint: String,
    val revocationEndpoint: String? = null,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Instant?,
    val scope: String,
    val dpopKey: DpopKey.Serialized,
    val authServerNonce: String? = null,
    val pdsNonce: String? = null,
) {
    fun encode(): String = AtJson.encodeToString(serializer(), this)

    override fun toString(): String = "OAuthSession(did=$did, handle=$handle, pds=$pdsUrl, expiresAt=$expiresAt)"

    companion object {
        fun decode(value: String): OAuthSession = AtJson.decodeFromString(serializer(), value)
    }
}

class OAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Persistence for the signed-in session and in-flight authorizations (implemented by `core:data`). */
interface OAuthStore {
    suspend fun loadSession(): OAuthSession?

    suspend fun saveSession(session: OAuthSession)

    suspend fun clearSession()

    suspend fun savePending(pending: PendingAuthorization)

    suspend fun loadPending(state: String): PendingAuthorization?

    suspend fun clearPending(state: String)
}
