package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.segments.SegmentMatcher

/** Environment-driven config. Defaults match `docker/compose.yaml` so `./gradlew :server-appview:run` just works. */
data class AppViewConfig(
    val port: Int,
    /** Public base URL (what PDSes and native OAuth clients reach). */
    val publicUrl: String,
    /** `did:web` of this service; the `aud` of accepted service-auth tokens. */
    val serviceDid: String,
    /**
     * Other DIDs accepted as service-auth `aud`, from comma-separated `APPVIEW_EXTRA_AUDIENCES`. Local dev only: with a tunnel, native apps
     * address [serviceDid] while the web app on the same AppView still addresses `did:web:localhost%3A8081`.
     */
    val extraAudiences: Set<String>,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    /** Jetstream `subscribeEvents` websocket URL; null disables indexing (API-only mode). */
    val jetstreamUrl: String?,
    val plcUrl: String,
    val handleResolverUrl: String?,
    /** Custom URL scheme native apps use for the OAuth redirect (reverse-domain of [publicUrl]'s host). */
    val oauthRedirectScheme: String,
    /**
     * The AppView's P-256 private key as 64 hex chars (`APPVIEW_SIGNING_KEY`), published as `#atproto` and used to sync athletes' tier
     * spaces. Null disables syncing: only public activities are served.
     */
    val signingKey: String? = null,
    /**
     * The app's own labeler (`LABELER_DID`, `LABELER_URL`), whose labels apply to any subject. Null: no labels from it, and the moderator
     * route refuses.
     */
    val labeler: Labeler? = null,
    /** Bluesky's moderation labeler (`BSKY_LABELER_DID`, `BSKY_LABELER_URL`), whose labels apply only to athletes' accounts. */
    val bskyLabeler: Labeler? = null,
    /**
     * The AppView's own account on [labeler]'s team, with the read-only triage role (`APPVIEW_TRIAGE_DID`, and an app password in
     * `APPVIEW_TRIAGE_PASSWORD`): it signs in to check moderators' roles and reported subjects' review status. Null: the moderator routes
     * refuse.
     */
    val triage: Triage? = null,
    /**
     * How many distinct athletes' efforts make a proposed segment active (`SEGMENT_ACTIVE_ATHLETES`, at least 1), the creator included
     * (segments/segment-records).
     */
    val segmentActiveAthletes: Int = SegmentMatcher.DEFAULT_ACTIVE_ATHLETES,
) {
    /** An account the AppView signs in to with an app password. */
    data class Triage(val did: String, val appPassword: String) {
        override fun toString() = "Triage($did)"
    }

    /**
     * A labeler the AppView subscribes to. [url] is its `#atproto_labeler` endpoint; null resolves it from [did]'s DID document. Labels are
     * verified against [did]'s `#atproto_label` key either way.
     */
    data class Labeler(val did: String, val url: String? = null)

    /** Service id athletes' spaces name as their `managingApp`; PDSes call `checkUserAccess` on it. */
    val managingAppService: String
        get() = "$serviceDid#$MANAGING_APP_FRAGMENT"

    /** Service id the AppView registers for athletes' spaces' write notifications; PDSes call `notifyWrite` on it. */
    val syncerService: String
        get() = "$serviceDid#$SYNCER_FRAGMENT"

    companion object {
        /** Fragment of the `AtprotoSpaceService` entry in the DID document. */
        const val MANAGING_APP_FRAGMENT = "activitytracker_space"

        /** Fragment of the space-syncer entry in the DID document. */
        const val SYNCER_FRAGMENT = "activitytracker_space_syncer"

        fun fromEnv(env: Map<String, String> = System.getenv()): AppViewConfig {
            val port = env["APPVIEW_PORT"]?.toInt() ?: 8081
            val publicUrl = env["APPVIEW_PUBLIC_URL"] ?: "http://localhost:$port"
            val host = publicUrl.substringAfter("://").substringBefore('/')
            return AppViewConfig(
                port = port,
                publicUrl = publicUrl.trimEnd('/'),
                serviceDid = env["APPVIEW_DID"] ?: "did:web:${host.replace(":", "%3A")}",
                extraAudiences = env["APPVIEW_EXTRA_AUDIENCES"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                databaseUrl = env["DATABASE_URL"] ?: "jdbc:postgresql://localhost:5433/appview",
                databaseUser = env["DATABASE_USER"] ?: "appview",
                databasePassword = env["DATABASE_PASSWORD"] ?: "appview",
                jetstreamUrl =
                    env["JETSTREAM_URL"]?.takeIf { it.isNotBlank() } ?: "ws://localhost:6008/xrpc/network.bsky.jetstream.subscribeEvents",
                plcUrl = env["PLC_URL"] ?: "http://localhost:2582",
                handleResolverUrl = env["HANDLE_RESOLVER_URL"] ?: "http://localhost:2583",
                oauthRedirectScheme = env["OAUTH_REDIRECT_SCHEME"] ?: host.substringBefore(':').split('.').reversed().joinToString("."),
                signingKey = env["APPVIEW_SIGNING_KEY"]?.trim()?.takeIf { it.isNotEmpty() },
                labeler = labeler(env, "LABELER"),
                bskyLabeler = labeler(env, "BSKY_LABELER"),
                triage =
                    env["APPVIEW_TRIAGE_DID"]
                        ?.trim()
                        ?.takeIf { it.startsWith("did:") }
                        ?.let { did -> env["APPVIEW_TRIAGE_PASSWORD"]?.trim()?.takeIf { it.isNotEmpty() }?.let { Triage(did, it) } },
                segmentActiveAthletes =
                    env["SEGMENT_ACTIVE_ATHLETES"]?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: SegmentMatcher.DEFAULT_ACTIVE_ATHLETES,
            )
        }

        /** `<prefix>_DID` and the optional `<prefix>_URL`; null without a DID. */
        private fun labeler(env: Map<String, String>, prefix: String): Labeler? {
            val did = env["${prefix}_DID"]?.trim()?.takeIf { it.startsWith("did:") } ?: return null
            return Labeler(did, env["${prefix}_URL"]?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() })
        }
    }
}
