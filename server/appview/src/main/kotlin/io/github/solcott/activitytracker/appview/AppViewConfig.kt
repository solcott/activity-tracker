package io.github.solcott.activitytracker.appview

/** Environment-driven config. Defaults match `docker/compose.yaml` so `./gradlew :server-appview:run` just works. */
data class AppViewConfig(
    val port: Int,
    /** Public base URL (what PDSes and native OAuth clients reach). */
    val publicUrl: String,
    /** `did:web` of this service; the `aud` of accepted service-auth tokens. */
    val serviceDid: String,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    /** Jetstream `subscribeEvents` websocket URL; null disables indexing (API-only mode). */
    val jetstreamUrl: String?,
    val plcUrl: String,
    val handleResolverUrl: String?,
    /** Custom URL scheme native apps use for the OAuth redirect (reverse-domain of [publicUrl]'s host). */
    val oauthRedirectScheme: String,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): AppViewConfig {
            val port = env["APPVIEW_PORT"]?.toInt() ?: 8081
            val publicUrl = env["APPVIEW_PUBLIC_URL"] ?: "http://localhost:$port"
            val host = publicUrl.substringAfter("://").substringBefore('/')
            return AppViewConfig(
                port = port,
                publicUrl = publicUrl.trimEnd('/'),
                serviceDid = env["APPVIEW_DID"] ?: "did:web:${host.replace(":", "%3A")}",
                databaseUrl = env["DATABASE_URL"] ?: "jdbc:postgresql://localhost:5433/appview",
                databaseUser = env["DATABASE_USER"] ?: "appview",
                databasePassword = env["DATABASE_PASSWORD"] ?: "appview",
                jetstreamUrl =
                    env["JETSTREAM_URL"]?.takeIf { it.isNotBlank() } ?: "ws://localhost:6008/xrpc/network.bsky.jetstream.subscribeEvents",
                plcUrl = env["PLC_URL"] ?: "http://localhost:2582",
                handleResolverUrl = env["HANDLE_RESOLVER_URL"] ?: "http://localhost:2583",
                oauthRedirectScheme = env["OAUTH_REDIRECT_SCHEME"] ?: host.substringBefore(':').split('.').reversed().joinToString("."),
            )
        }
    }
}
