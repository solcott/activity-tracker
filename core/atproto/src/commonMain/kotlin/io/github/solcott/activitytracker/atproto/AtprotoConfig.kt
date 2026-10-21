package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid

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
    /** The app's labeler, which reports go to (`atproto-proxy: <labelerDid>#atproto_labeler`); null where none is configured. */
    val labelerDid: Did? = null,
)

data class OAuthClientConfig(
    /** Either a loopback client id (`http://localhost?...`) or an HTTPS URL to client metadata. */
    val clientId: String,
    val redirectUri: String,
    val scope: String,
) {
    companion object {
        /**
         * Scopes: identity, generic repo/blob access (narrow to `repo:<nsid>` scopes once PDSes widely support them), and the athlete space
         * type: reading any athlete's spaces (`authority=*`, still gated by each space's policy), reading their own, writing records and
         * creating and updating their own spaces. The PDS adds the space type's declared collections at sign-in, so it must resolve the
         * declaration (`lexicons/.../space/athlete.json`).
         */
        const val DEFAULT_SCOPE =
            "atproto transition:generic space:${Nsid.SPACE_ATHLETE}?authority=*&action=read&action=read_self&action=create" +
                "&action=update&action=delete&manage=create&manage=update"

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

private const val HEX_RADIX = 16
private const val NIBBLE_BITS = 4
private const val NIBBLE_MASK = 0xF

internal fun String.encodeUriComponent(): String = buildString {
    for (byte in this@encodeUriComponent.encodeToByteArray()) {
        val c = byte.toInt().toChar()
        if ((c.isLetterOrDigit() && byte >= 0) || c in "-_.~") {
            append(c)
        } else {
            append('%')
            append(((byte.toInt() shr NIBBLE_BITS) and NIBBLE_MASK).toString(HEX_RADIX).uppercase())
            append((byte.toInt() and NIBBLE_MASK).toString(HEX_RADIX).uppercase())
        }
    }
}
