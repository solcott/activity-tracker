package io.github.solcott.activitytracker.appview

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.appview.api.ActivityStreamsViews
import io.github.solcott.activitytracker.appview.api.ActorSearch
import io.github.solcott.activitytracker.appview.api.AvatarUrls
import io.github.solcott.activitytracker.appview.api.GraphViews
import io.github.solcott.activitytracker.appview.api.MediaUrls
import io.github.solcott.activitytracker.appview.api.MemberSpaceBlobs
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
import io.github.solcott.activitytracker.appview.sync.SpaceSyncer
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
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
            appView(config, db, verifier, pds, PdsBlobStreamSource(http, pds, spaceBlobs), signingKey, syncer, spaceBlobs)
            config.jetstreamUrl?.let { url -> launch { JetstreamConsumer(http, url, db, indexer).run() } }
            syncer?.start(this)
        }
        .start(wait = true)
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
) {
    val mediaTokens = config.signingKey?.let(::PrivateMediaTokens)
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
        xrpcRoutes(
            Views(db, avatars, MediaUrls(config.publicUrl, mediaTokens)),
            GraphViews(db, avatars),
            ActorSearch(db, avatars),
            ActivityStreamsViews(db, streamSource),
            SpaceAccess(db, Did(config.serviceDid)),
            verifier,
            verifier.forAudiences(setOf(config.managingAppService)),
        )
        spaceSyncRoutes(syncer, verifier, verifier.forAudiences(setOf(config.syncerService)))
        avatarRoutes(db, pds)
        mediaRoutes(db, pds, spaceBlobs, mediaTokens)
    }
}
