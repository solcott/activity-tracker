package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestLogWriter
import co.touchlab.kermit.loggerConfigInit
import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.oauth.OAuthClient
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.auth.InMemorySecretCipher
import io.github.solcott.activitytracker.data.auth.RoomOAuthStore
import io.github.solcott.activitytracker.data.db.inMemoryDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ActivityRepositoryTest {
    private val did = Did("did:plc:alice")
    private var failWrites = false
    private val putBodies = mutableListOf<String>()
    private val logs = TestLogWriter(Severity.Verbose)
    private val logger = Logger(loggerConfigInit(logs))

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.repo.uploadBlob" -> {
                        respond(
                            """{"blob":{"${'$'}type":"blob","ref":{"${'$'}link":"bafyblob"},"mimeType":"application/gpx+xml","size":10}}""",
                            headers = json,
                        )
                    }

                    "/xrpc/com.atproto.repo.putRecord" -> {
                        if (failWrites) {
                            respond(
                                """{"error":"InternalServerError"}""",
                                HttpStatusCode.InternalServerError,
                                json,
                            )
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

    @Test
    fun publishUploadsTrackThenWritesRecordAndRetriesAfterFailure() = runTest {
        val db = inMemoryDatabase()
        val store = RoomOAuthStore(db, Clock.System, InMemorySecretCipher(), logger)
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
        val manager =
            OAuthSessionManager(
                    OAuthClient(http, IdentityResolver(http, config), config.oauth),
                    store,
                )
                .apply { restore() }
        val clients = SessionClients(session, http, manager, config, Clock.System)
        val outbox = OutboxProcessor(db, clients, logger)
        val repo = ActivityRepository(db, clients, outbox, FeedRepository(db, clients, logger), Clock.System)

        val draft =
            ActivityDraft(
                sport = "run",
                title = "Morning run",
                description = null,
                startedAt = Instant.parse("2026-09-30T07:00:00Z"),
                elapsed = 30.minutes,
                distanceMeters = 5012.4,
                gpx = "<gpx/>".encodeToByteArray(),
            )

        failWrites = true
        val uri = repo.publish(draft)
        assertEquals(1, repo.pendingWrites.first())
        logs.assertCount(1)
        logs.assertLast { severity == Severity.Warn && tag == "OutboxProcessor" && "1 pending" in message }

        failWrites = false
        assertEquals(0, repo.sync())
        assertEquals(0, repo.pendingWrites.first())

        val put = AtJson.parseToJsonElement(putBodies.single()).jsonObject
        assertEquals(uri.rkey, put["rkey"]?.jsonPrimitive?.content)
        val record = put["record"]!!.jsonObject
        assertEquals(Nsid.FEED_ACTIVITY, record["\$type"]?.jsonPrimitive?.content)
        assertEquals("5012", record["distance"]?.jsonPrimitive?.content)
        assertTrue("bafyblob" in record["track"].toString())
    }
}
