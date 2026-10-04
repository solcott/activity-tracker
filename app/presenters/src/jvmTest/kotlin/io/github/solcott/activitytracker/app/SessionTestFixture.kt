package io.github.solcott.activitytracker.app

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.oauth.OAuthClient
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.auth.InMemorySecretCipher
import io.github.solcott.activitytracker.data.auth.RoomOAuthStore
import io.github.solcott.activitytracker.data.db.inMemoryDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalKudos
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** A signed-in session for Alice against a fake PDS that has no profile record and records every `putRecord`. */
class SessionTestFixture {
    val did = Did("did:plc:alice")
    var failWrites = false
    private val putBodies = mutableListOf<String>()

    /** The `record` of each successful `putRecord`, in order. */
    val putRecords: List<JsonObject>
        get() = putBodies.map { AtJson.parseToJsonElement(it).jsonObject.getValue("record").jsonObject }

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.repo.getRecord" -> {
                        respond("""{"error":"RecordNotFound"}""", HttpStatusCode.BadRequest, json)
                    }

                    "/xrpc/com.atproto.repo.putRecord" -> {
                        if (failWrites) {
                            respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        } else {
                            putBodies += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                            respond("""{"uri":"at://$did/x/y","cid":"bafyrec"}""", headers = json)
                        }
                    }

                    else -> {
                        respond("", HttpStatusCode.NotFound)
                    }
                }
            }
        ) {
            expectSuccess = false
        }

    class Repos(val activities: ActivityRepository, val units: UnitsRepository, val auth: AuthRepository)

    suspend fun repos(defaultUnits: UnitSystem = UnitSystem.Metric): Repos {
        val db = inMemoryDatabase()
        val store = RoomOAuthStore(db, Clock.System, InMemorySecretCipher(), Logger)
        val session =
            OAuthSession(
                did = did,
                handle = Handle("alice.test"),
                pdsUrl = "http://pds.test",
                issuer = "http://pds.test",
                tokenEndpoint = "http://pds.test/oauth/token",
                accessToken = "at",
                refreshToken = "rt",
                expiresAt = Clock.System.now() + 1.hours,
                scope = "atproto",
                dpopKey = DpopKey.generate().serialize(),
            )
        store.saveSession(session)
        val config =
            AtprotoConfig(
                "http://plc.test",
                null,
                "http://appview.test",
                "did:web:appview.test",
                OAuthClientConfig.loopback("http://127.0.0.1/"),
            )
        val manager = OAuthSessionManager(OAuthClient(http, IdentityResolver(http, config), config.oauth), store).apply { restore() }
        val clients = SessionClients(session, http, manager, config, Clock.System)
        val localKudos = LocalKudos()
        val feed = FeedRepository(db, clients, localKudos, Logger)
        val activities = ActivityRepository(db, clients, OutboxProcessor(db, clients, Logger), feed, localKudos, Clock.System)
        val units = UnitsRepository(ProfileRepository(clients, Clock.System), Clock.System, Logger, defaultUnits)
        return Repos(activities, units, AuthRepository(manager, db, http))
    }
}
