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
     * Native OAuth needs publicly fetchable client metadata, so native builds must use an HTTPS tunnel to the AppView (set
     * `APPVIEW_PUBLIC_URL` on the AppView to the same value). The redirect scheme is the reverse of that host (see
     * `OAUTH_REDIRECT_SCHEME`), mirrored in the Android manifest and iOS Info.plist.
     */
    const val NATIVE_APPVIEW_PUBLIC_URL = APPVIEW_URL
    const val NATIVE_REDIRECT_SCHEME = "app.activitytracker"

    /**
     * [origin] is the page origin. The OAuth loopback client only accepts 127.0.0.1 redirects, so the dev server binds there (see
     * `webpack.config.d/devserver.js`).
     */
    fun web(origin: String): AtprotoConfig = config(APPVIEW_URL, OAuthClientConfig.loopback("$origin/"))

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
