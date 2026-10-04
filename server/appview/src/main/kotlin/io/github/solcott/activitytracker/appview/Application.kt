package io.github.solcott.activitytracker.appview

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.appview.api.Views
import io.github.solcott.activitytracker.appview.api.XrpcError
import io.github.solcott.activitytracker.appview.api.respondXrpcError
import io.github.solcott.activitytracker.appview.api.wellKnownRoutes
import io.github.solcott.activitytracker.appview.api.xrpcRoutes
import io.github.solcott.activitytracker.appview.auth.AuthException
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamConsumer
import io.github.solcott.activitytracker.appview.index.ResolvingHandleLookup
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
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

    val db = DatabaseFactory.connect(config.databaseUrl, config.databaseUser, config.databasePassword)
    val http =
        HttpClient(CIO) {
            expectSuccess = false
            install(WebSockets)
        }
    val identity = IdentityResolver(http, config.plcUrl, config.handleResolverUrl)
    val verifier = ServiceAuthVerifier(config.serviceDid, identity, config.extraAudiences)

    embeddedServer(Netty, port = config.port) {
            appView(config, db, verifier)
            config.jetstreamUrl?.let { url ->
                val indexer = Indexer(db, ResolvingHandleLookup(identity))
                launch { JetstreamConsumer(http, url, db, indexer).run() }
            }
        }
        .start(wait = true)
}

fun Application.appView(config: AppViewConfig, db: Database, verifier: ServiceAuthVerifier) {
    install(CallLogging)
    install(CORS) {
        // The web client calls the AppView directly from the browser.
        anyHost()
        allowMethod(HttpMethod.Get)
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
        wellKnownRoutes(config)
        xrpcRoutes(Views(db), verifier)
    }
}
