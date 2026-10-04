package io.github.solcott.activitytracker.data.units

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
import io.github.solcott.activitytracker.data.auth.InMemorySecretCipher
import io.github.solcott.activitytracker.data.auth.RoomOAuthStore
import io.github.solcott.activitytracker.data.db.inMemoryDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class UnitsRepositoryTest {
    private val did = Did("did:plc:alice")
    private val logger = Logger(loggerConfigInit(TestLogWriter(Severity.Verbose)))

    /** The profile record in Alice's repo, or null for none. */
    private var profile: String? = null
    private var failWrites = false
    private val putBodies = mutableListOf<String>()

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.repo.getRecord" -> {
                        val record = profile
                        if (record == null) {
                            respond("""{"error":"RecordNotFound","message":"no record"}""", HttpStatusCode.BadRequest, json)
                        } else {
                            respond("""{"uri":"at://$did/app.activitytracker.actor.profile/self","value":$record}""", headers = json)
                        }
                    }

                    "/xrpc/com.atproto.repo.putRecord" -> {
                        if (failWrites) {
                            respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        } else {
                            putBodies += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                            respond("""{"uri":"at://$did/x/self","cid":"bafyrec"}""", headers = json)
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

    private suspend fun repository(default: UnitSystem = UnitSystem.Metric): UnitsRepository {
        val store = RoomOAuthStore(inMemoryDatabase(), Clock.System, InMemorySecretCipher(), logger)
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
        return UnitsRepository(ProfileRepository(clients, Clock.System), Clock.System, logger, default)
    }

    private fun putRecord(index: Int = putBodies.lastIndex) =
        AtJson.parseToJsonElement(putBodies[index]).jsonObject.getValue("record").jsonObject

    @Test
    fun refreshReadsPreferenceAndKeepsDefaultWithoutOne() = runTest {
        val repo = repository(default = UnitSystem.Metric)
        repo.refresh()
        assertEquals(UnitSystem.Metric, repo.units.value)

        profile = """{"${'$'}type":"app.activitytracker.actor.profile","units":"imperial"}"""
        repo.refresh()
        assertEquals(UnitSystem.Imperial, repo.units.value)

        // Unknown tokens leave the current value alone.
        profile = """{"${'$'}type":"app.activitytracker.actor.profile","units":"cubits"}"""
        repo.refresh()
        assertEquals(UnitSystem.Imperial, repo.units.value)
    }

    @Test
    fun setKeepsOtherProfileFields() = runTest {
        profile = """{"${'$'}type":"app.activitytracker.actor.profile","displayName":"Alice","sports":["run"]}"""
        val repo = repository()

        repo.set(UnitSystem.Imperial)

        assertEquals(UnitSystem.Imperial, repo.units.value)
        val record = putRecord()
        assertEquals("imperial", record["units"]?.jsonPrimitive?.content)
        assertEquals("Alice", record["displayName"]?.jsonPrimitive?.content)
        assertEquals("run", record["sports"].toString().trim('[', ']', '"'))
    }

    @Test
    fun setCreatesProfileWhenMissing() = runTest {
        val repo = repository()
        repo.set(UnitSystem.Imperial)
        val record = putRecord()
        assertEquals("imperial", record["units"]?.jsonPrimitive?.content)
        assertNull(record["displayName"])
        assertEquals(true, "createdAt" in record)
    }

    @Test
    fun setRevertsWhenWriteFails() = runTest {
        failWrites = true
        val repo = repository(default = UnitSystem.Metric)
        assertFailsWith<Exception> { repo.set(UnitSystem.Imperial) }
        assertEquals(UnitSystem.Metric, repo.units.value)
    }
}
