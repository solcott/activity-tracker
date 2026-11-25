package io.github.solcott.activitytracker.appview

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.appview.admin.AccountsPage
import io.github.solcott.activitytracker.appview.admin.AdminOAuth
import io.github.solcott.activitytracker.appview.admin.AdminPage
import io.github.solcott.activitytracker.appview.admin.AdminSite
import io.github.solcott.activitytracker.appview.admin.AtprotoAdminOAuth
import io.github.solcott.activitytracker.appview.admin.DatabaseSizes
import io.github.solcott.activitytracker.appview.admin.HealthSources
import io.github.solcott.activitytracker.appview.api.ActivityStreamsViews
import io.github.solcott.activitytracker.appview.api.ActorSearch
import io.github.solcott.activitytracker.appview.api.AvatarUrls
import io.github.solcott.activitytracker.appview.api.DiveSiteViews
import io.github.solcott.activitytracker.appview.api.GraphViews
import io.github.solcott.activitytracker.appview.api.InviteViews
import io.github.solcott.activitytracker.appview.api.LabelSources
import io.github.solcott.activitytracker.appview.api.MediaUrls
import io.github.solcott.activitytracker.appview.api.MemberSpaceBlobs
import io.github.solcott.activitytracker.appview.api.ModerationAccessLog
import io.github.solcott.activitytracker.appview.api.PdsBlobStreamSource
import io.github.solcott.activitytracker.appview.api.PdsLookup
import io.github.solcott.activitytracker.appview.api.PrivateMediaTokens
import io.github.solcott.activitytracker.appview.api.ResolvingPdsLookup
import io.github.solcott.activitytracker.appview.api.SegmentCandidateViews
import io.github.solcott.activitytracker.appview.api.SegmentViews
import io.github.solcott.activitytracker.appview.api.SpaceAccess
import io.github.solcott.activitytracker.appview.api.SpaceBlobs
import io.github.solcott.activitytracker.appview.api.StreamSource
import io.github.solcott.activitytracker.appview.api.Views
import io.github.solcott.activitytracker.appview.api.XrpcError
import io.github.solcott.activitytracker.appview.api.avatarRoutes
import io.github.solcott.activitytracker.appview.api.diveSiteRoutes
import io.github.solcott.activitytracker.appview.api.mediaRoutes
import io.github.solcott.activitytracker.appview.api.moderationRoutes
import io.github.solcott.activitytracker.appview.api.readinessRoutes
import io.github.solcott.activitytracker.appview.api.respondXrpcError
import io.github.solcott.activitytracker.appview.api.segmentCandidateRoutes
import io.github.solcott.activitytracker.appview.api.segmentRoutes
import io.github.solcott.activitytracker.appview.api.spaceSyncRoutes
import io.github.solcott.activitytracker.appview.api.wellKnownRoutes
import io.github.solcott.activitytracker.appview.api.xrpcRoutes
import io.github.solcott.activitytracker.appview.auth.AppViewSigningKey
import io.github.solcott.activitytracker.appview.auth.AuthException
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.index.HandleSweep
import io.github.solcott.activitytracker.appview.index.HandleVerifier
import io.github.solcott.activitytracker.appview.index.IdentityChanges
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamConsumer
import io.github.solcott.activitytracker.appview.index.assignMissingGroups
import io.github.solcott.activitytracker.appview.labels.LabelStore
import io.github.solcott.activitytracker.appview.labels.LabelSubscriber
import io.github.solcott.activitytracker.appview.labels.LabelVerifier
import io.github.solcott.activitytracker.appview.moderation.ModeratorSubjects
import io.github.solcott.activitytracker.appview.moderation.OzoneClient
import io.github.solcott.activitytracker.appview.ops.AppViewMetrics
import io.github.solcott.activitytracker.appview.ops.Readiness
import io.github.solcott.activitytracker.appview.ops.WorkerKind
import io.github.solcott.activitytracker.appview.ops.WorkerNames
import io.github.solcott.activitytracker.appview.ops.WorkerRegistry
import io.github.solcott.activitytracker.appview.ops.WorkerSupervisor
import io.github.solcott.activitytracker.appview.ops.everyRound
import io.github.solcott.activitytracker.appview.ops.installRequestMetrics
import io.github.solcott.activitytracker.appview.ops.serve
import io.github.solcott.activitytracker.appview.reindex.RepoReindexer
import io.github.solcott.activitytracker.appview.segments.SegmentJobOutcomes
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.appview.segments.SegmentMatcher
import io.github.solcott.activitytracker.appview.sync.BskyBlockBackfill
import io.github.solcott.activitytracker.appview.sync.SpaceSyncer
import io.github.solcott.activitytracker.appview.sync.SyncOutcomes
import io.github.solcott.activitytracker.appview.sync.SyncRegistrations
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.space.SignedDelegationTokens
import io.github.solcott.activitytracker.atproto.space.SpaceCredentials
import io.github.solcott.activitytracker.atproto.space.SpaceMember
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.terrain.TerrainTiles
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.io.File
import kotlinx.coroutines.delay
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory

fun main() {
    // Shared KMP modules (core-atproto) log through Kermit; send that to Logback with everything else.
    Logger.setLogWriters(Slf4jLogWriter())
    val config = AppViewConfig.fromEnv()
    val log = LoggerFactory.getLogger("AppView")
    logStartup(config, log)

    // Prometheus metrics on the internal metrics port, when one is configured (dev/worker-telemetry); none are collected otherwise.
    val meterRegistry = config.metricsPort?.let { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }
    val db = DatabaseFactory.connect(config.databaseUrl, config.databaseUser, config.databasePassword, meterRegistry)
    val metrics = meterRegistry?.let { AppViewMetrics(db, it) }
    val outbound = Outbound.of(config)
    val guarded = outbound.guarded
    val identity = outbound.identity
    val verifier = ServiceAuthVerifier(config.serviceDid, identity, config.extraAudiences)

    val identityChanges = IdentityChanges()
    val indexer = indexerOf(config, db, outbound, identityChanges)
    val signingKey = config.signingKey?.let(::AppViewSigningKey)
    val registrations = signingKey?.let { SyncRegistrations(db, config.publicUrl) }
    val credentials = signingKey?.let { spaceCredentials(config, it, checkNotNull(registrations), guarded, identity) }
    val member = credentials?.let { SpaceMember(it, guarded) }
    val syncer = member?.let { SpaceSyncer(db, indexer, it, outcomes = metrics?.syncOutcomes ?: SyncOutcomes.None) }
    // An operator's reindex of one account (admin/repo-reindex): its DID document resolved afresh, its repo fetched through [guarded].
    val reindexer = RepoReindexer(db, indexer, guarded, identity::refreshDid, syncer?.let { it::resyncAccount }, config.reindex)
    val spaceBlobs = member?.let(::MemberSpaceBlobs)
    // Every background worker's status (dev/worker-telemetry), for the metrics and the admin pages.
    val workers = WorkerRegistry()

    metrics?.serve(config.metricsHost, checkNotNull(config.metricsPort), log)
    val pds = ResolvingPdsLookup(identity)
    // An `#identity` event: every cache of that account's identity forgets it, so the next use asks again (identity/outbound-requests).
    identityChanges.onChange { did -> forgetIdentity(did, outbound, verifier, pds, credentials) }
    val blockBackfill = BskyBlockBackfill(db, indexer, guarded, pds)
    val accounts = AccountsPage(db, indexer, blockBackfill, reindexer, syncer)
    embeddedServer(Netty, port = config.port) {
            val ozone = ozoneOf(config, outbound.http, outbound.configuredIdentity)
            // Started before the routes are, so the health page is given them (admin/ingest-health); nothing is served until `start`.
            val supervisor = WorkerSupervisor(this, workers)
            val started =
                startWorkers(
                    supervisor,
                    WorkerInputs(config, db, outbound, indexer, syncer, spaceBlobs, log, metrics, reindexer, blockBackfill),
                )
            val health = HealthSources(supervisor, started, db, config.jetstreamRewindMax, syncer != null, DatabaseSizes.of(db))
            appView(
                config,
                db,
                verifier,
                pds,
                PdsBlobStreamSource(guarded, pds, spaceBlobs),
                signingKey,
                syncer,
                spaceBlobs,
                ozone = ozone,
                segmentJobs = indexer.segmentJobs,
                registrations = registrations,
                adminOAuth = AtprotoAdminOAuth(guarded, identity),
                adminPages = adminPages(config, db, indexer, health, accounts),
                metrics = metrics,
            )
            metrics?.bind(workers, started)
        }
        .start(wait = true)
}

/** The labeler's team API, signed in as the AppView's triage account; null without both (moderation/private-subjects). */
private fun ozoneOf(config: AppViewConfig, http: HttpClient, identity: IdentityResolver): OzoneClient? =
    config.labeler?.let { labeler ->
        config.triage?.let { triage ->
            OzoneClient(http, labeler, triage) { did, service -> identity.resolveDid(Did(did)).serviceEndpoint(service) }
        }
    }

private fun logStartup(config: AppViewConfig, log: org.slf4j.Logger) {
    log.info("Starting AppView {} at {}", config.serviceDid, config.publicUrl)
    if (config.extraAudiences.isNotEmpty()) log.info("Also accepting service auth for {}", config.extraAudiences)
    if (config.signingKey == null)
        log.warn("APPVIEW_SIGNING_KEY isn't set: space syncing is disabled, so only public activities are served")
    if (config.allowPrivateAddresses)
        log.warn("APPVIEW_ALLOW_PRIVATE_ADDRESSES is set: hosts from DID documents and handles may be private addresses (local dev only)")
}

/** The metrics of every started worker, Jetstream's lag and each labeler's stream. */
private fun AppViewMetrics.bind(workers: WorkerRegistry, started: StartedWorkers) {
    bindWorkers(workers)
    started.jetstream?.let(::bindJetstream)
    bindLabelers(started.labelSubscribers)
}

/**
 * The AppView's membership of athletes' spaces, with delegation tokens it signs itself. Each authorization of a synced space also registers
 * the AppView for its updates ([registrations]), at its public URL.
 */
private fun spaceCredentials(
    config: AppViewConfig,
    key: AppViewSigningKey,
    registrations: SyncRegistrations,
    http: HttpClient,
    identity: IdentityResolver,
): SpaceCredentials {
    val tokens = SignedDelegationTokens(Did(config.serviceDid), key.jwtAlg, key::sign)
    return SpaceCredentials(tokens, http, identity, notify = registrations::notifyFor)
}

private fun segmentMatcher(config: AppViewConfig, db: Database, indexer: Indexer, blobs: SpaceBlobs, outcomes: SegmentJobOutcomes) =
    SegmentMatcher(
        db,
        indexer.segmentJobs,
        blobs,
        activeAthletes = config.segmentActiveAthletes,
        candidateRiders = config.segmentCandidateRiders,
        names = config.segmentNames,
        trailDetails = config.trailDetails,
        segmentImporter = config.segmentImporter,
        terrain = config.terrainTiles?.let { TerrainTiles(File(it.dir), it.zoom) },
        outcomes = outcomes,
    )

/** The indexer for [config]: the segment importer, the naming rules, and whether terrain tiles are configured. */
private fun indexerOf(config: AppViewConfig, db: Database, outbound: Outbound, identityChanges: IdentityChanges): Indexer =
    Indexer(
        db,
        HandleVerifier(outbound.identity),
        segmentImporter = config.segmentImporter,
        segmentNames = config.segmentNames,
        trailDetails = config.trailDetails,
        terrain = config.terrainTiles != null,
        segmentActiveAthletes = config.segmentActiveAthletes,
        identityChanges = identityChanges,
        plcPausedUntil = outbound.plcPacing::pausedUntil,
    )

/** What the background workers need. */
@Suppress("LongParameterList") // The workers' collaborators, built once in main.
private class WorkerInputs(
    val config: AppViewConfig,
    val db: Database,
    val outbound: Outbound,
    val indexer: Indexer,
    val syncer: SpaceSyncer?,
    val spaceBlobs: SpaceBlobs?,
    val log: org.slf4j.Logger,
    val metrics: AppViewMetrics?,
    val reindexer: RepoReindexer,
    val blockBackfill: BskyBlockBackfill,
)

/**
 * Starts every background worker under [supervisor] (dev/worker-telemetry), by the names its registry and the metrics know them by. A
 * worker configuration switches off is registered as disabled, with the reason.
 */
private fun startWorkers(supervisor: WorkerSupervisor, inputs: WorkerInputs): StartedWorkers =
    with(inputs) {
        val registry = supervisor.registry
        val jetstream = config.jetstreamUrl?.let { JetstreamConsumer(outbound.http, it, db, indexer) }
        if (jetstream != null) supervisor.stream(WorkerNames.JETSTREAM) { jetstream.run(it) }
        else registry.disabled(WorkerNames.JETSTREAM, WorkerKind.STREAM, "JETSTREAM_URL is not set")
        supervisor.loop(WorkerNames.BSKY_BLOCK_BACKFILL) { blockBackfill.run(it) }
        val handles = HandleSweep(db, indexer, plcPausedUntil = outbound.plcPacing::pausedUntil)
        supervisor.loop(WorkerNames.HANDLE_SWEEP) { handles.run(it) }
        // Segments indexed before segment groups get theirs (segments/segment-groups); indexing gives new ones theirs.
        supervisor.oneOff(WorkerNames.SEGMENT_GROUPS_BACKFILL) {
            val given = assignMissingGroups(db)
            if (given > 0) log.info("Gave {} segments their groups", given)
            it.success()
        }
        if (syncer != null) supervisor.loop(WorkerNames.SPACE_SYNCER) { syncer.run(supervisor.scope, it) }
        else registry.disabled(WorkerNames.SPACE_SYNCER, WorkerKind.LOOP, NO_SIGNING_KEY)
        // Segment tracks live in athletes' `self` spaces, so matching needs the AppView's member credential, as syncing does.
        val matcher = spaceBlobs?.let { segmentMatcher(config, db, indexer, it, metrics?.segmentJobOutcomes ?: SegmentJobOutcomes.None) }
        if (matcher != null) supervisor.loop(WorkerNames.SEGMENT_MATCHER) { matcher.run(it) }
        else registry.disabled(WorkerNames.SEGMENT_MATCHER, WorkerKind.LOOP, NO_SIGNING_KEY)
        val labels = startLabelSubscribers(supervisor, config, db, outbound.http, outbound.configuredIdentity)
        // The moderator audit log keeps entries a little over a year.
        val accessLog = ModerationAccessLog(db)
        supervisor.loop(WorkerNames.MODERATION_ACCESS_LOG) { accessLog.run(it) }
        // Operators' reindexes (admin/repo-reindex); each run is launched in the supervisor's scope and outlives the round that started it.
        supervisor.loop(WorkerNames.REPO_REINDEXER) { reindexer.run(supervisor.scope, it) }
        StartedWorkers(jetstream, labels)
    }

/** The workers the metrics read state from beyond the registry: Jetstream's last event, each labeler's stream. */
class StartedWorkers(val jetstream: JetstreamConsumer?, val labelSubscribers: List<LabelSubscriber>)

private const val NO_SIGNING_KEY = "APPVIEW_SIGNING_KEY is not set"

/**
 * One [LabelSubscriber] per configured labeler, each a worker of its own so a failing stream stops nothing else, and an hourly sweep of
 * expired labels. Nothing without a labeler: no labels then apply.
 */
private fun startLabelSubscribers(
    supervisor: WorkerSupervisor,
    config: AppViewConfig,
    db: Database,
    http: HttpClient,
    identity: IdentityResolver,
): List<LabelSubscriber> {
    val labelers = listOfNotNull(config.labeler, config.bskyLabeler)
    if (labelers.isEmpty()) {
        supervisor.registry.disabled(WorkerNames.LABEL_SWEEP, WorkerKind.LOOP, "No labeler is configured (LABELER_DID, BSKY_LABELER_DID)")
        return emptyList()
    }
    val store = LabelStore(db, config.labeler?.did, config.bskyLabeler?.did)
    val verifier = LabelVerifier { did, fresh ->
        (if (fresh) identity.refreshDid(Did(did)) else identity.resolveDid(Did(did))).verificationKey("#atproto_label")
    }
    val endpoint: suspend (String) -> String? = { did -> identity.resolveDid(Did(did)).serviceEndpoint("#atproto_labeler") }
    val subscribers = labelers.map { labeler -> LabelSubscriber(http, labeler, store, verifier, endpoint) }
    subscribers.forEach { subscriber -> supervisor.stream(WorkerNames.labelSubscriber(subscriber.did)) { subscriber.run(it) } }
    supervisor.loop(WorkerNames.LABEL_SWEEP) { worker ->
        worker.everyRound(LoggerFactory.getLogger("AppView"), "Sweeping expired labels", { delay(LabelSubscriber.SWEEP_INTERVAL) }) {
            store.sweepExpired()
        }
    }
    return subscribers
}

@Suppress("LongParameterList") // The AppView's collaborators, each swappable in tests.
fun Application.appView(
    config: AppViewConfig,
    db: Database,
    verifier: ServiceAuthVerifier,
    pds: PdsLookup = PdsLookup { null },
    streamSource: StreamSource = StreamSource { _, _, _ -> null },
    signingKey: AppViewSigningKey? = config.signingKey?.let(::AppViewSigningKey),
    syncer: SpaceSyncer? = null,
    spaceBlobs: SpaceBlobs? = null,
    mediaTokens: PrivateMediaTokens? = config.signingKey?.let(::PrivateMediaTokens),
    ozone: OzoneClient? = null,
    /** The matcher's queue, which staging a candidate fills; the indexer's in production, so each enqueue wakes the matcher. */
    segmentJobs: SegmentJobs = SegmentJobs(),
    /** The syncer's notification tokens, which `confirmRegistration` and `notifySpaceUpdate` are checked against; null with no syncer. */
    registrations: SyncRegistrations? = null,
    /** How admins prove who they are; the admin site is mounted only with this and at least one configured admin (admin/admin-site). */
    adminOAuth: AdminOAuth? = null,
    /** The operator pages the admin site shows besides its home and audit log. */
    adminPages: List<AdminPage> = emptyList(),
    /** The readiness check `/xrpc/_ready` answers; a ping of [db] in production. */
    readiness: Readiness = Readiness(db),
    /** Times requests into the metrics, when the metrics port is configured. */
    metrics: AppViewMetrics? = null,
) {
    // Labels from these sources apply to what's served; with none configured, none do.
    LabelSources.trusted = listOfNotNull(config.labeler?.did, config.bskyLabeler?.did)
    install(CallLogging)
    metrics?.let { installRequestMetrics(it) }
    install(StatusPages) {
        exception<XrpcError> { call, e -> call.respondXrpcError(e.status, e.error, e.message) }
        // The moderator routes refuse rather than serve when the labeler can't be asked.
        exception<OzoneClient.LabelerUnavailable> { call, e ->
            LoggerFactory.getLogger("AppView").warn("Labeler unavailable: {}", e.message)
            call.respondXrpcError(HttpStatusCode.ServiceUnavailable.value, "LabelerUnavailable", "The labeler can't be reached")
        }
        exception<AuthException> { call, e ->
            call.respondXrpcError(HttpStatusCode.Unauthorized.value, "AuthenticationRequired", e.message)
        }
        exception<Throwable> { call, e ->
            LoggerFactory.getLogger("AppView").error("Unhandled error", e)
            call.respondXrpcError(HttpStatusCode.InternalServerError.value, "InternalServerError", null)
        }
    }
    val admin = adminOAuth?.takeIf { config.admins.isNotEmpty() }?.let { AdminSite(config, db, it, adminPages) }
    admin?.startMaintenance(this)
    routing {
        // Mounted beside the rest, outside its any-host CORS: no other origin may read an admin page (admin/admin-site).
        admin?.install(this)
        createChild(NonAdminSelector).apply {
            install(CORS) {
                // The web client calls the AppView directly from the browser.
                anyHost()
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.ContentType)
                allowHeader("atproto-proxy")
            }
            readinessRoutes(readiness)
            publicRoutes(
                config,
                db,
                verifier,
                pds,
                streamSource,
                signingKey,
                syncer,
                spaceBlobs,
                mediaTokens,
                ozone,
                segmentJobs,
                registrations,
            )
        }
    }
}

/** Every path but `/admin` and below, consuming nothing, so the routes inside keep their full paths. */
private object NonAdminSelector : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
        if (context.segments.firstOrNull() == "admin") RouteSelectorEvaluation.FailedPath else RouteSelectorEvaluation.Transparent

    override fun toString() = "(not /admin)"
}

/** The XRPC API, well-known documents and image routes: everything clients call. */
@Suppress("LongParameterList")
private fun Route.publicRoutes(
    config: AppViewConfig,
    db: Database,
    verifier: ServiceAuthVerifier,
    pds: PdsLookup,
    streamSource: StreamSource,
    signingKey: AppViewSigningKey?,
    syncer: SpaceSyncer?,
    spaceBlobs: SpaceBlobs?,
    mediaTokens: PrivateMediaTokens?,
    ozone: OzoneClient?,
    segmentJobs: SegmentJobs,
    registrations: SyncRegistrations?,
) {
    wellKnownRoutes(config, signingKey)
    val avatars = AvatarUrls(config.publicUrl)
    val views = Views(db, avatars, MediaUrls(config.publicUrl, mediaTokens))
    val streams = ActivityStreamsViews(db, streamSource)
    xrpcRoutes(
        views,
        GraphViews(db, avatars),
        ActorSearch(db, avatars),
        streams,
        InviteViews(db, avatars, streams),
        SpaceAccess(db, Did(config.serviceDid)),
        verifier,
        verifier.forAudiences(setOf(config.managingAppService)),
    )
    segmentRoutes(
        SegmentViews(
            db,
            avatars,
            segmentImporter = config.segmentImporter,
            names = config.segmentNames,
            trailDetails = config.trailDetails,
            activeAthletes = config.segmentActiveAthletes,
            media = MediaUrls(config.publicUrl),
        ),
        verifier,
    )
    segmentCandidateRoutes(SegmentCandidateViews(db, config.segmentImporter, segmentJobs), verifier)
    diveSiteRoutes(DiveSiteViews(db, avatars, segmentImporter = config.segmentImporter, names = config.segmentNames), verifier)
    moderationRoutes(ModeratorSubjects(db, views, avatars, ozone, ModerationAccessLog(db)), verifier)
    spaceSyncRoutes(syncer, registrations, verifier)
    avatarRoutes(db, pds)
    mediaRoutes(db, pds, spaceBlobs, mediaTokens)
}
