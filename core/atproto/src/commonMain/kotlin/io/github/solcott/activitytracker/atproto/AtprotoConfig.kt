package io.github.solcott.activitytracker.atproto

/**
 * Network endpoints and OAuth client identity. The app ships a local-stack config (docker PLC/PDS/AppView) and a production config; see
 * `composeApp`'s `NetworkConfigs`.
 */
data class AtprotoConfig(
    /** PLC directory used to resolve `did:plc`. */
    val plcUrl: String,
    /**
     * Optional service answering `com.atproto.identity.resolveHandle`, tried before DNS / well-known. Required for `.test` handles on the
     * local PDS, which have no DNS.
     */
    val handleResolverUrl: String?,
    val appViewUrl: String,
    /** The AppView's `did:web`, used as the `aud` of service-auth tokens. */
    val appViewDid: String,
    val oauth: OAuthClientConfig,
)

data class OAuthClientConfig(
    /** Either a loopback client id (`http://localhost?...`) or an HTTPS URL to client metadata. */
    val clientId: String,
    val redirectUri: String,
    val scope: String,
) {
    companion object {
        /** Scopes: identity + generic repo/blob access. Narrow to `repo:<nsid>` scopes once PDSes widely support them. */
        const val DEFAULT_SCOPE = "atproto transition:generic"

        /**
         * ATProto "loopback" development client: no hosted metadata required. Only valid for redirect URIs on 127.0.0.1 / [::1], so this is
         * used by the web target in development.
         */
        fun loopback(redirectUri: String, scope: String = DEFAULT_SCOPE): OAuthClientConfig {
            val clientId = "http://localhost?redirect_uri=${redirectUri.encodeUriComponent()}&scope=${scope.encodeUriComponent()}"
            return OAuthClientConfig(clientId, redirectUri, scope)
        }
    }
}

internal fun String.encodeUriComponent(): String = buildString {
    for (byte in this@encodeUriComponent.encodeToByteArray()) {
        val c = byte.toInt().toChar()
        if ((c.isLetterOrDigit() && byte >= 0) || c in "-_.~") {
            append(c)
        } else {
            append('%')
            append(((byte.toInt() shr 4) and 0xF).toString(16).uppercase())
            append((byte.toInt() and 0xF).toString(16).uppercase())
        }
    }
}
