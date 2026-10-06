package io.github.solcott.activitytracker.data.profile

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestLogWriter
import co.touchlab.kermit.loggerConfigInit
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ProfileSetupTest {
    private val did = Did("did:plc:alice")
    private val logger = Logger(loggerConfigInit(TestLogWriter(Severity.Verbose)))

    /** The profile record in Alice's repo, or null for none. */
    private var profile: String? = null
    private var failCreate = false
    private val createBodies = mutableListOf<String>()

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.repo.getRecord" -> {
                        val record = profile
                        if (record == null) {
                            respond("""{"error":"RecordNotFound"}""", HttpStatusCode.BadRequest, json)
                        } else {
                            respond("""{"uri":"at://$did/${Nsid.ACTOR_PROFILE}/self","value":$record}""", headers = json)
                        }
                    }

                    "/xrpc/com.atproto.repo.createRecord" -> {
                        if (failCreate) {
                            respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        } else {
                            createBodies += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                            respond("""{"uri":"at://$did/${Nsid.ACTOR_PROFILE}/self","cid":"bafyrec"}""", headers = json)
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

    private suspend fun setup(): ProfileSetup {
        val clients = signedInTestSession(http, did = did, logger = logger).clients
        return ProfileSetup(clients, ProfileRepository(clients, Clock.System), Clock.System, logger)
    }

    @Test
    fun createsAnEmptyProfileRecordWhenThereIsNone() = runTest {
        setup().ensureProfile()
        val input = AtJson.parseToJsonElement(createBodies.single()).jsonObject
        assertEquals(Nsid.ACTOR_PROFILE, input.getValue("collection").jsonPrimitive.content)
        assertEquals("self", input.getValue("rkey").jsonPrimitive.content)
        val record = input.getValue("record").jsonObject
        assertEquals(Nsid.ACTOR_PROFILE, record.getValue("\$type").jsonPrimitive.content)
        assertNotNull(record["createdAt"])
        // Nothing the athlete hasn't chosen to publish.
        assertEquals(emptySet(), record.keys - setOf("\$type", "createdAt", "sports"))
    }

    @Test
    fun leavesAnExistingProfileAlone() = runTest {
        profile = """{"${'$'}type":"${Nsid.ACTOR_PROFILE}","displayName":"Alice","createdAt":"2026-09-30T00:00:00Z"}"""
        setup().ensureProfile()
        assertEquals(emptyList(), createBodies)
    }

    @Test
    fun failureIsLoggedNotThrown() = runTest {
        failCreate = true
        setup().ensureProfile()
        assertEquals(emptyList(), createBodies)
    }
}
