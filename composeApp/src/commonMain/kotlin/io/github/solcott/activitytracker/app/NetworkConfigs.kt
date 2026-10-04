package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.atproto.OAuthClientConfig

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
     * [origin] is the page origin. The OAuth loopback client only accepts 127.0.0.1 redirects, so the dev server binds there (see
     * `webpack.config.d/devserver.js`).
     */
    fun web(origin: String): AtprotoConfig = config(APPVIEW_URL, OAuthClientConfig.loopback("$origin/"))

    /** Port of the desktop app's OAuth loopback listener (the web dev server has 8080, the AppView 8081). */
    const val DESKTOP_OAUTH_PORT = 8091

    /** Desktop uses the loopback client like web, with the redirect caught by a listener on [port], so it needs no tunnel. */
    fun desktop(port: Int = DESKTOP_OAUTH_PORT): AtprotoConfig =
        config(APPVIEW_URL, OAuthClientConfig.loopback("http://127.0.0.1:$port/callback"))

    fun native(): AtprotoConfig =
        config(
            NATIVE_APPVIEW_PUBLIC_URL,
            OAuthClientConfig(
                clientId = "$NATIVE_APPVIEW_PUBLIC_URL/oauth/client-metadata.json",
                redirectUri = "$NATIVE_REDIRECT_SCHEME:/oauth/callback",
                scope = OAuthClientConfig.DEFAULT_SCOPE,
            ),
        )

    private fun config(appViewUrl: String, oauth: OAuthClientConfig) =
        AtprotoConfig(
            plcUrl = PLC_URL,
            handleResolverUrl = HANDLE_RESOLVER_URL,
            appViewUrl = appViewUrl,
            appViewDid = "did:web:" + appViewUrl.substringAfter("://").substringBefore('/').replace(":", "%3A"),
            oauth = oauth,
        )
}
