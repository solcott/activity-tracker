package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.admin.AdminRole
import io.github.solcott.activitytracker.appview.segments.SegmentMatcher
import io.github.solcott.activitytracker.appview.segments.SegmentNameRules
import io.github.solcott.activitytracker.appview.segments.TrailDetailRules
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.terrain.TerrainTiles
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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
    /**
     * The one account allowed to stage candidate segments and read which are ridden (`SEGMENT_IMPORTER_DID`; segments/segment-candidates).
     * Null: every candidate call is refused.
     */
    val segmentImporter: Did? = null,
    /** How many distinct athletes' passes make a staged candidate ridden (`SEGMENT_CANDIDATE_RIDERS`, at least 1). */
    val segmentCandidateRiders: Int = SegmentMatcher.DEFAULT_ACTIVE_ATHLETES,
    /** Who may propose segment names and when a proposal is adopted (`SEGMENT_NAME_*`; segments/segment-names). */
    val segmentNames: SegmentNameRules = SegmentNameRules(),
    /** Trail detail suggestion thresholds (`TRAIL_DETAIL_*`, segments/trail-detail-suggestions). */
    val trailDetails: TrailDetailRules = TrailDetailRules(),
    /**
     * Terrain elevation tiles (`TERRAIN_TILES_DIR`, Terrarium PNGs at `{z}/{x}/{y}.png`, fetched by `scripts/terrain-fetch.sh`) and their
     * zoom (`TERRAIN_TILES_ZOOM`, default 12; segments/segment-elevation). Null: segments get no terrain elevation, only their efforts'.
     */
    val terrainTiles: TerrainTilesConfig? = null,
    /**
     * Whether hosts learned from DID documents and handles may be private, loopback or link-local addresses
     * (`APPVIEW_ALLOW_PRIVATE_ADDRESSES=true`; identity/outbound-requests). Local dev only, where every PDS and `did:web` is `localhost`.
     */
    val allowPrivateAddresses: Boolean = false,
    /** The app the Android App Links in `/.well-known/assetlinks.json` name (`ANDROID_APP_PACKAGE`; ui/deep-links). */
    val androidPackage: String = DEFAULT_ANDROID_PACKAGE,
    /**
     * SHA-256 fingerprints of the certificates the Android app is signed with (`ANDROID_CERT_SHA256`, comma-separated, `AB:CD:…`). Empty:
     * `/.well-known/assetlinks.json` answers 404, so Android verifies no links here.
     */
    val androidCertSha256: List<String> = emptyList(),
    /**
     * The iOS app IDs (`<team id>.<bundle id>`) the Universal Links in `/.well-known/apple-app-site-association` name (`APPLE_APP_IDS`,
     * comma-separated). Empty: that file answers 404.
     */
    val appleAppIds: List<String> = emptyList(),
    /**
     * Who may use the admin site and with which role (`APPVIEW_ADMINS`, comma-separated `did=viewer|operator`; admin/admin-site). Empty:
     * the site isn't mounted and every `/admin` path is `404`.
     */
    val admins: Map<Did, AdminRole> = emptyMap(),
    /**
     * The internal port Prometheus metrics are served on at `/metrics` (`METRICS_PORT`; dev/worker-telemetry). Null: no metrics are
     * collected or served. Never the public [port].
     */
    val metricsPort: Int? = null,
    /** The address the metrics port binds to (`METRICS_HOST`), loopback unless set. */
    val metricsHost: String = DEFAULT_METRICS_HOST,
    /** Limits on an operator's reindex of one account (`REINDEX_*`; admin/repo-reindex). */
    val reindex: ReindexLimits = ReindexLimits(),
    /** How far back an operator may rewind Jetstream (`JETSTREAM_REWIND_MAX`, a Kotlin duration; admin/ingest-health). */
    val jetstreamRewindMax: Duration = DEFAULT_JETSTREAM_REWIND_MAX,
) {
    /**
     * How much a reindex reads and how many run: the largest repo it fetches ([maxCarBytes], `REINDEX_MAX_CAR_BYTES`) and its most blocks
     * ([maxBlocks], `REINDEX_MAX_BLOCKS`), the longest one run takes ([timeout], `REINDEX_TIMEOUT`, a Kotlin duration such as `30m`), how
     * many run at once ([parallelism], `REINDEX_PARALLELISM`), and how long after a run Jetstream commits its snapshot covers are still
     * dropped ([watchGrace], `REINDEX_WATCH_GRACE`).
     */
    data class ReindexLimits(
        val maxCarBytes: Long = DEFAULT_MAX_CAR_BYTES,
        val maxBlocks: Int = DEFAULT_MAX_BLOCKS,
        val timeout: Duration = 30.minutes,
        val parallelism: Int = 2,
        val watchGrace: Duration = 15.minutes,
    ) {
        companion object {
            fun fromEnv(env: Map<String, String>): ReindexLimits {
                val defaults = ReindexLimits()
                fun positiveLong(key: String) = env[key]?.trim()?.toLongOrNull()?.takeIf { it > 0 }
                fun duration(key: String) = env[key]?.trim()?.let(Duration::parseOrNull)?.takeIf { it.isPositive() }
                return ReindexLimits(
                    maxCarBytes = positiveLong("REINDEX_MAX_CAR_BYTES") ?: defaults.maxCarBytes,
                    maxBlocks = positiveLong("REINDEX_MAX_BLOCKS")?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: defaults.maxBlocks,
                    timeout = duration("REINDEX_TIMEOUT") ?: defaults.timeout,
                    parallelism = positiveLong("REINDEX_PARALLELISM")?.coerceAtMost(MAX_PARALLELISM)?.toInt() ?: defaults.parallelism,
                    watchGrace = duration("REINDEX_WATCH_GRACE") ?: defaults.watchGrace,
                )
            }

            private const val DEFAULT_MAX_CAR_BYTES = 512L * 1024 * 1024
            private const val DEFAULT_MAX_BLOCKS = 2_000_000

            /** More at once would crowd out Jetstream's indexing. */
            private const val MAX_PARALLELISM = 8L
        }
    }

    init {
        require(metricsPort != port) { "METRICS_PORT must not be APPVIEW_PORT ($port): metrics are never served on the public port" }
    }

    /** Where terrain tiles live and at which zoom they were fetched. */
    data class TerrainTilesConfig(val dir: String, val zoom: Int)

    /** An account the AppView signs in to with an app password. */
    data class Triage(val did: String, val appPassword: String) {
        override fun toString() = "Triage($did)"
    }

    /**
     * A labeler the AppView subscribes to. [url] is its `#atproto_labeler` endpoint; null resolves it from [did]'s DID document. Labels are
     * verified against [did]'s `#atproto_label` key either way.
     */
    data class Labeler(val did: String, val url: String? = null)

    /** Service id athletes' spaces name as their `managingApp`; PDSes call `checkAccess` on it. */
    val managingAppService: String
        get() = "$serviceDid#$MANAGING_APP_FRAGMENT"

    companion object {
        /** Fragment of the `AtprotoSpaceService` entry in the DID document. */
        const val MANAGING_APP_FRAGMENT = "activitytracker_space"

        /** Terrarium tiles' default zoom: about 28 m a pixel at 45 degrees. */
        const val DEFAULT_TERRAIN_ZOOM = TerrainTiles.DEFAULT_ZOOM

        /** Where the metrics port binds unless `METRICS_HOST` says otherwise. */
        const val DEFAULT_METRICS_HOST = "127.0.0.1"

        /** The Android app's application id. */
        const val DEFAULT_ANDROID_PACKAGE = "io.github.solcott.activitytracker"

        /** `METRICS_PORT`; null when unset. */
        private fun metricsPort(env: Map<String, String>): Int? = nonBlank(env, "METRICS_PORT")?.toInt()

        /** `METRICS_HOST`, or [DEFAULT_METRICS_HOST]. */
        private fun metricsHost(env: Map<String, String>): String = nonBlank(env, "METRICS_HOST") ?: DEFAULT_METRICS_HOST

        /** [key]'s value, trimmed; null when unset or blank. */
        private fun nonBlank(env: Map<String, String>, key: String): String? = env[key]?.trim()?.takeIf { it.isNotEmpty() }

        private fun list(value: String?): List<String> = value.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

        /** A day: past it, a Jetstream instance may no longer hold the events, and reindexing the account is the tool. */
        val DEFAULT_JETSTREAM_REWIND_MAX = 24.hours

        /** Terrarium tiles' deepest zoom. */
        const val MAX_TERRAIN_ZOOM = TerrainTiles.MAX_ZOOM

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
                segmentActiveAthletes = athletes(env, "SEGMENT_ACTIVE_ATHLETES"),
                segmentImporter = env["SEGMENT_IMPORTER_DID"]?.trim()?.takeIf { it.startsWith("did:") }?.let(::Did),
                segmentCandidateRiders = athletes(env, "SEGMENT_CANDIDATE_RIDERS"),
                segmentNames = SegmentNameRules.fromEnv(env),
                trailDetails = TrailDetailRules.fromEnv(env),
                terrainTiles = terrainTiles(env),
                allowPrivateAddresses = env["APPVIEW_ALLOW_PRIVATE_ADDRESSES"]?.trim().equals("true", ignoreCase = true),
                androidPackage = env["ANDROID_APP_PACKAGE"]?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_ANDROID_PACKAGE,
                androidCertSha256 = list(env["ANDROID_CERT_SHA256"]),
                appleAppIds = list(env["APPLE_APP_IDS"]),
                admins = AdminRole.parseAdmins(env["APPVIEW_ADMINS"]),
                metricsPort = metricsPort(env),
                metricsHost = metricsHost(env),
                reindex = ReindexLimits.fromEnv(env),
                jetstreamRewindMax = jetstreamRewindMax(env),
            )
        }

        /** `JETSTREAM_REWIND_MAX`, a positive Kotlin duration, else [DEFAULT_JETSTREAM_REWIND_MAX]. */
        private fun jetstreamRewindMax(env: Map<String, String>): Duration =
            env["JETSTREAM_REWIND_MAX"]?.trim()?.let(Duration::parseOrNull)?.takeIf { it.isPositive() } ?: DEFAULT_JETSTREAM_REWIND_MAX

        /**
         * `TERRAIN_TILES_DIR` and `TERRAIN_TILES_ZOOM` (1 to [MAX_TERRAIN_ZOOM], default [DEFAULT_TERRAIN_ZOOM]); null without a folder.
         */
        private fun terrainTiles(env: Map<String, String>): TerrainTilesConfig? {
            val dir = env["TERRAIN_TILES_DIR"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val zoom = env["TERRAIN_TILES_ZOOM"]?.trim()?.toIntOrNull()?.coerceIn(1, MAX_TERRAIN_ZOOM) ?: DEFAULT_TERRAIN_ZOOM
            return TerrainTilesConfig(dir, zoom)
        }

        /** `<prefix>_DID` and the optional `<prefix>_URL`; null without a DID. */
        /** A count of athletes at [key], at least 1; [SegmentMatcher.DEFAULT_ACTIVE_ATHLETES] when unset or not a number. */
        private fun athletes(env: Map<String, String>, key: String): Int =
            env[key]?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: SegmentMatcher.DEFAULT_ACTIVE_ATHLETES

        private fun labeler(env: Map<String, String>, prefix: String): Labeler? {
            val did = env["${prefix}_DID"]?.trim()?.takeIf { it.startsWith("did:") } ?: return null
            return Labeler(did, env["${prefix}_URL"]?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() })
        }
    }
}
