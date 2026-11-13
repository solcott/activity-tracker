package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.lexicon.Did

/**
 * Endpoints for the local docker stack (`docker/compose.yaml`), generated from `local.properties` and the Gradle user home's
 * `gradle.properties` (`NativeOAuthConventionPlugin`). With `activitytracker.deviceHost` set (device mode), every client uses the public
 * hosts `scripts/dev.sh` tunnels to, so the Mac and a physical phone see the same PLC, PDS and AppView; without it, the local stack's
 * `localhost` ports. Point these at production services (e.g. `https://plc.directory`, a public handle resolver, the deployed AppView) for
 * real use.
 */
object NetworkConfigs {
    const val PLC_URL = GeneratedNetworkConfig.PLC_URL
    const val HANDLE_RESOLVER_URL = GeneratedNetworkConfig.HANDLE_RESOLVER_URL

    /** The AppView web and desktop call: its public host in device mode, else `http://localhost:8081`. */
    const val APPVIEW_URL = GeneratedNetworkConfig.APPVIEW_URL

    /**
     * Native OAuth needs publicly fetchable HTTPS client metadata, so native builds call the AppView through a tunnel: the device host's
     * AppView sibling in device mode, else `activitytracker.appviewPublicUrl` in `local.properties` (defaults to [APPVIEW_URL], which the
     * PDS rejects). The redirect scheme is the reverse of that host; the Android manifest and the AppView's `run` task derive theirs from
     * the same value.
     */
    const val NATIVE_APPVIEW_PUBLIC_URL = GeneratedNetworkConfig.APPVIEW_PUBLIC_URL
    const val NATIVE_REDIRECT_SCHEME = GeneratedNetworkConfig.REDIRECT_SCHEME

    /** The labeler reports go to, `activitytracker.labelerDid` in `local.properties` (`scripts/dev.sh` writes the local Ozone's). */
    val LABELER_DID: Did? = GeneratedNetworkConfig.LABELER_DID.takeIf(String::isNotEmpty)?.let(::Did)

    /**
     * [origin] is the page origin. The OAuth loopback client only accepts 127.0.0.1 redirects, so the dev server binds there (see
     * `webpack.config.d/devserver.js`).
     */
    fun web(origin: String): AtprotoConfig = config(APPVIEW_URL, OAuthClientConfig.loopback("$origin/"))

    /** Port of the desktop app's OAuth loopback listener (the web dev server has 8080, the AppView 8081). */
    const val DESKTOP_OAUTH_PORT = 8091

    /**
     * Desktop uses the loopback client like web, with the redirect caught by a listener on [port], so its OAuth needs no tunnel (its calls
     * still go through one in device mode).
     */
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
            labelerDid = LABELER_DID,
        )
}
