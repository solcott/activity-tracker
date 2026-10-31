package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.lexicon.Did

/**
 * Endpoints for the local docker stack (`docker/compose.yaml`). Point these at production services (e.g. `https://plc.directory`, a public
 * handle resolver, the deployed AppView) for real use.
 */
object NetworkConfigs {
    const val PLC_URL = "http://localhost:2582"
    const val HANDLE_RESOLVER_URL = "http://localhost:2583"
    const val APPVIEW_URL = "http://localhost:8081"

    /**
     * Native OAuth needs publicly fetchable HTTPS client metadata, so native builds use a tunnel to the AppView, set once as
     * `activitytracker.appviewPublicUrl` in `local.properties` (defaults to [APPVIEW_URL], which the PDS rejects). The redirect scheme is
     * the reverse of that host; the Android manifest and the AppView's `run` task derive theirs from the same property.
     */
    const val NATIVE_APPVIEW_PUBLIC_URL = GeneratedNativeNetworkConfig.APPVIEW_PUBLIC_URL
    const val NATIVE_REDIRECT_SCHEME = GeneratedNativeNetworkConfig.REDIRECT_SCHEME

    /**
     * Identity endpoints for native builds: the local stack's unless `activitytracker.deviceHost` is set in `local.properties`, which moves
     * them to the public hosts `scripts/dev.sh` tunnels to in device mode, so a physical phone can sign in. Web and desktop run on the Mac
     * and keep [PLC_URL] and [HANDLE_RESOLVER_URL]: they resolve the same DIDs and then follow each DID document to the PDS.
     */
    const val NATIVE_PLC_URL = GeneratedNativeNetworkConfig.PLC_URL
    const val NATIVE_HANDLE_RESOLVER_URL = GeneratedNativeNetworkConfig.HANDLE_RESOLVER_URL

    /** The labeler reports go to, `activitytracker.labelerDid` in `local.properties` (`scripts/dev.sh` writes the local Ozone's). */
    val LABELER_DID: Did? = GeneratedNativeNetworkConfig.LABELER_DID.takeIf(String::isNotEmpty)?.let(::Did)

    /**
     * [origin] is the page origin. The OAuth loopback client only accepts 127.0.0.1 redirects, so the dev server binds there (see
     * `webpack.config.d/devserver.js`).
     */
    fun web(origin: String): AtprotoConfig = config(APPVIEW_URL, OAuthClientConfig.loopback("$origin/"), PLC_URL, HANDLE_RESOLVER_URL)

    /** Port of the desktop app's OAuth loopback listener (the web dev server has 8080, the AppView 8081). */
    const val DESKTOP_OAUTH_PORT = 8091

    /** Desktop uses the loopback client like web, with the redirect caught by a listener on [port], so it needs no tunnel. */
    fun desktop(port: Int = DESKTOP_OAUTH_PORT): AtprotoConfig =
        config(APPVIEW_URL, OAuthClientConfig.loopback("http://127.0.0.1:$port/callback"), PLC_URL, HANDLE_RESOLVER_URL)

    fun native(): AtprotoConfig =
        config(
            NATIVE_APPVIEW_PUBLIC_URL,
            OAuthClientConfig(
                clientId = "$NATIVE_APPVIEW_PUBLIC_URL/oauth/client-metadata.json",
                redirectUri = "$NATIVE_REDIRECT_SCHEME:/oauth/callback",
                scope = OAuthClientConfig.DEFAULT_SCOPE,
            ),
            NATIVE_PLC_URL,
            NATIVE_HANDLE_RESOLVER_URL,
        )

    private fun config(appViewUrl: String, oauth: OAuthClientConfig, plcUrl: String, handleResolverUrl: String) =
        AtprotoConfig(
            plcUrl = plcUrl,
            handleResolverUrl = handleResolverUrl,
            appViewUrl = appViewUrl,
            appViewDid = "did:web:" + appViewUrl.substringAfter("://").substringBefore('/').replace(":", "%3A"),
            oauth = oauth,
            labelerDid = LABELER_DID,
        )
}
