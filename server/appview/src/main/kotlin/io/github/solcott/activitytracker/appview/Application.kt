package io.github.solcott.activitytracker.appview

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.appview.api.ActivityStreamsViews
import io.github.solcott.activitytracker.appview.api.ActorSearch
import io.github.solcott.activitytracker.appview.api.AvatarUrls
import io.github.solcott.activitytracker.appview.api.GraphViews
import io.github.solcott.activitytracker.appview.api.LabelSources
import io.github.solcott.activitytracker.appview.api.MediaUrls
import io.github.solcott.activitytracker.appview.api.MemberSpaceBlobs
import io.github.solcott.activitytracker.appview.api.ModerationAccessLog
import io.github.solcott.activitytracker.appview.api.PdsBlobStreamSource
import io.github.solcott.activitytracker.appview.api.PdsLookup
import io.github.solcott.activitytracker.appview.api.PrivateMediaTokens
import io.github.solcott.activitytracker.appview.api.ResolvingPdsLookup
import io.github.solcott.activitytracker.appview.api.SpaceAccess
import io.github.solcott.activitytracker.appview.api.SpaceBlobs
import io.github.solcott.activitytracker.appview.api.StreamSource
import io.github.solcott.activitytracker.appview.api.Views
import io.github.solcott.activitytracker.appview.api.XrpcError
import io.github.solcott.activitytracker.appview.api.avatarRoutes
import io.github.solcott.activitytracker.appview.api.mediaRoutes
import io.github.solcott.activitytracker.appview.api.moderationRoutes
import io.github.solcott.activitytracker.appview.api.respondXrpcError
import io.github.solcott.activitytracker.appview.api.spaceSyncRoutes
import io.github.solcott.activitytracker.appview.api.wellKnownRoutes
import io.github.solcott.activitytracker.appview.api.xrpcRoutes
import io.github.solcott.activitytracker.appview.auth.AppViewSigningKey
import io.github.solcott.activitytracker.appview.auth.AuthException
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamConsumer
import io.github.solcott.activitytracker.appview.index.ResolvingHandleLookup
import io.github.solcott.activitytracker.appview.labels.LabelStore
import io.github.solcott.activitytracker.appview.labels.LabelSubscriber
import io.github.solcott.activitytracker.appview.labels.LabelVerifier
import io.github.solcott.activitytracker.appview.moderation.ModeratorSubjects
import io.github.solcott.activitytracker.appview.moderation.OzoneClient
import io.github.solcott.activitytracker.appview.sync.BskyBlockBackfill
import io.github.solcott.activitytracker.appview.sync.SpaceSyncer
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.space.SignedDelegationTokens
import io.github.solcott.activitytracker.atproto.space.SpaceCredentials
import io.github.solcott.activitytracker.atproto.space.SpaceMember
import io.github.solcott.activitytracker.lexicon.Did
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
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
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory

fun main() {
    // Shared KMP modules (core-atproto) log through Kermit; send that to Logback with everything else.
    Logger.setLogWriters(Slf4jLogWriter())
    val config = AppViewConfig.fromEnv()
    val log = LoggerFactory.getLogger("AppView")
    log.info("Starting AppView {} at {}", config.serviceDid, config.publicUrl)
    if (config.extraAudiences.isNotEmpty()) log.info("Also accepting service auth for {}", config.extraAudiences)
    if (config.signingKey == null)
        log.warn("APPVIEW_SIGNING_KEY isn't set: space syncing is disabled, so only public activities are served")

    val db = DatabaseFactory.connect(config.databaseUrl, config.databaseUser, config.databasePassword)
    val http =
        HttpClient(CIO) {
            expectSuccess = false
            install(WebSockets)
        }
    val identity = IdentityResolver(http, config.plcUrl, config.handleResolverUrl)
    val verifier = ServiceAuthVerifier(config.serviceDid, identity, config.extraAudiences)

    val indexer = Indexer(db, ResolvingHandleLookup(identity))
    val signingKey = config.signingKey?.let(::AppViewSigningKey)
    val member = signingKey?.let { key ->
        val tokens = SignedDelegationTokens(Did(config.serviceDid), key.jwtAlg, key::sign)
        SpaceMember(SpaceCredentials(tokens, http, identity), http)
    }
    val syncer = member?.let { SpaceSyncer(db, indexer, it, config.syncerService) }
    val spaceBlobs = member?.let(::MemberSpaceBlobs)

    embeddedServer(Netty, port = config.port) {
            val pds = ResolvingPdsLookup(identity)
            val ozone =
                config.labeler?.let { labeler ->
                    config.triage?.let { triage ->
                        OzoneClient(http, labeler, triage) { did, service -> identity.resolveDid(Did(did)).serviceEndpoint(service) }
                    }
                }
            appView(
                config,
                db,
                verifier,
                pds,
                PdsBlobStreamSource(http, pds, spaceBlobs),
                signingKey,
                syncer,
                spaceBlobs,
                ozone = ozone,
            )
            config.jetstreamUrl?.let { url -> launch { JetstreamConsumer(http, url, db, indexer).run() } }
            BskyBlockBackfill(db, indexer, http, pds).start(this)
            syncer?.start(this)
            startLabelSubscribers(config, db, http, identity)
            // The moderator audit log keeps entries a little over a year.
            ModerationAccessLog(db).start(this)
        }
        .start(wait = true)
}

/**
 * One [LabelSubscriber] per configured labeler, each in its own coroutine so a failing stream stops nothing else, and an hourly sweep of
 * expired labels. Nothing without a labeler: no labels then apply.
 */
private fun CoroutineScope.startLabelSubscribers(config: AppViewConfig, db: Database, http: HttpClient, identity: IdentityResolver) {
    val labelers = listOfNotNull(config.labeler, config.bskyLabeler)
    if (labelers.isEmpty()) return
    val store = LabelStore(db, config.labeler?.did, config.bskyLabeler?.did)
    val verifier = LabelVerifier { did, _ -> identity.resolveDid(Did(did)).verificationKey("#atproto_label") }
    val endpoint: suspend (String) -> String? = { did -> identity.resolveDid(Did(did)).serviceEndpoint("#atproto_labeler") }
    labelers.forEach { labeler -> launch { LabelSubscriber(http, labeler, store, verifier, endpoint).run() } }
    launch {
        while (true) {
            runSuspendCatching { store.sweepExpired() }
            delay(LabelSubscriber.SWEEP_INTERVAL)
        }
    }
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
) {
    // Labels from these sources apply to what's served; with none configured, none do.
    LabelSources.trusted = listOfNotNull(config.labeler?.did, config.bskyLabeler?.did)
    install(CallLogging)
    install(CORS) {
        // The web client calls the AppView directly from the browser.
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("atproto-proxy")
    }
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
    routing {
        wellKnownRoutes(config, signingKey)
        val avatars = AvatarUrls(config.publicUrl)
        val views = Views(db, avatars, MediaUrls(config.publicUrl, mediaTokens))
        xrpcRoutes(
            views,
            GraphViews(db, avatars),
            ActorSearch(db, avatars),
            ActivityStreamsViews(db, streamSource),
            SpaceAccess(db, Did(config.serviceDid)),
            verifier,
            verifier.forAudiences(setOf(config.managingAppService)),
        )
        moderationRoutes(ModeratorSubjects(db, views, avatars, ozone, ModerationAccessLog(db)), verifier)
        spaceSyncRoutes(syncer, verifier, verifier.forAudiences(setOf(config.syncerService)))
        avatarRoutes(db, pds)
        mediaRoutes(db, pds, spaceBlobs, mediaTokens)
    }
}
