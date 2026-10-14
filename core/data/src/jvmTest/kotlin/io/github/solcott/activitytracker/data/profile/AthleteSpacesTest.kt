package io.github.solcott.activitytracker.data.profile

import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.space.SpaceClient
import io.github.solcott.activitytracker.atproto.space.SpaceCredentials
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Alice's PDS answering `createSpace`/`getSpace`/`updateSpace`, with the space's current read policy in [readPolicy]. */
class AthleteSpacesTest {
    private val alice = Did("did:plc:alice")
    private val current = "did:web:appview.test#activitytracker_space"
    private var exists = false
    private var readPolicy = current
    private var failCreate = false

    /** The `value` of the tier's `actor.privateDetails` record that `space.getRecord` returns. */
    private var storedRecord = "{}"

    /** Pages of `graph.followDecline` rkeys that `space.listRecords` returns, in order; none means the `self` space doesn't exist. */
    private var declinePages: List<List<String>>? = null

    /** Each call's method and JSON body, in order. */
    private val calls = mutableListOf<Pair<String, JsonObject?>>()

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                val method = request.url.encodedPath.removePrefix("/xrpc/")
                val body =
                    (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()?.let {
                        AtJson.parseToJsonElement(it).jsonObject
                    }
                calls += method to body
                val skey = body?.get("skey")?.jsonPrimitive?.content ?: AthleteSpace.MUTUALS
                val space = "at://${alice.value}/space/app.activitytracker.space.athlete/$skey"
                when (method) {
                    "com.atproto.simplespace.createSpace" ->
                        when {
                            failCreate -> respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                            exists -> respond("""{"error":"SpaceAlreadyExists"}""", HttpStatusCode.BadRequest, json)
                            else -> respond("""{"uri":"$space"}""", headers = json).also { exists = true }
                        }
                    "com.atproto.simplespace.getSpace" ->
                        respond(
                            """{"uri":"$space",
                               "readPolicy":{"${'$'}type":"com.atproto.simplespace.defs#managingAppPolicy","managingApp":"$readPolicy"},
                               "writePolicy":{"${'$'}type":"com.atproto.simplespace.defs#memberListPolicy"},
                               "appAccess":{"${'$'}type":"com.atproto.simplespace.defs#open"}}""",
                            headers = json,
                        )
                    "com.atproto.simplespace.updateSpace" -> respond("{}", headers = json)
                    "com.atproto.space.putRecord" -> respond("""{"uri":"$space/x","cid":"bafyrec"}""", headers = json)
                    "com.atproto.space.deleteRecord" -> respond("{}", headers = json)
                    "com.atproto.space.listRecords" -> {
                        val pages = declinePages
                        if (pages == null) {
                            respond("""{"error":"SpaceNotFound"}""", HttpStatusCode.BadRequest, json)
                        } else {
                            val index = request.url.parameters["cursor"]?.toInt() ?: 0
                            val records =
                                pages[index].joinToString(",") { rkey ->
                                    """{"collection":"app.activitytracker.graph.followDecline","rkey":"$rkey","cid":"bafy"}"""
                                }
                            val next = if (index + 1 < pages.size) ""","cursor":"${index + 1}"""" else ""
                            respond("""{"records":[$records]$next}""", headers = json)
                        }
                    }
                    "com.atproto.space.getRecord" ->
                        respond(
                            """{"uri":"$space/${alice.value}/app.activitytracker.actor.privateDetails/self",
                               "cid":"bafyreib2rxk3rh6kzwq","value":$storedRecord}""",
                            headers = json,
                        )
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        ) {
            expectSuccess = false
        }

    private fun spaces(): AthleteSpaces {
        val pds = XrpcClient(http, "http://pds.test")
        val client = SpaceClient(alice, pds, SpaceCredentials(pds, http, IdentityResolver(http, "http://plc.test", null)), http)
        return AthleteSpaces(client, managingApp = { current })
    }

    @Test
    fun createsTheTierSpaceWithTheAppViewReadingAndOnlyTheAthleteWriting() = runTest {
        val uri = spaces().ensure(AthleteSpace.MUTUALS)

        assertEquals("at://did:plc:alice/space/app.activitytracker.space.athlete/mutuals", uri.value)
        val (method, body) = calls.single()
        assertEquals("com.atproto.simplespace.createSpace", method)
        assertEquals(current, body!!["readPolicy"]!!.jsonObject["managingApp"]?.jsonPrimitive?.content)
        assertEquals("com.atproto.simplespace.defs#memberListPolicy", body["writePolicy"]!!.jsonObject["\$type"]?.jsonPrimitive?.content)
        assertEquals("com.atproto.simplespace.defs#open", body["appAccess"]!!.jsonObject["\$type"]?.jsonPrimitive?.content)
    }

    @Test
    fun anExistingSpaceWithTheCurrentManagingAppIsLeftAlone() = runTest {
        exists = true
        spaces().ensure(AthleteSpace.MUTUALS)
        assertEquals(listOf("com.atproto.simplespace.createSpace", "com.atproto.simplespace.getSpace"), calls.map { it.first })
    }

    @Test
    fun aStaleManagingAppIsReplaced() = runTest {
        exists = true
        readPolicy = "did:web:old-tunnel.test#activitytracker_space"
        spaces().ensure(AthleteSpace.MUTUALS)

        val update = calls.single { it.first == "com.atproto.simplespace.updateSpace" }.second!!
        assertEquals(current, update["readPolicy"]!!.jsonObject["managingApp"]?.jsonPrimitive?.content)
        assertEquals(setOf("space", "readPolicy"), update.keys)
    }

    @Test
    fun createsTheInteractionsSpaceAsATierIsCreated() = runTest {
        val uri = spaces().ensure(AthleteSpace.INTERACTIONS)

        assertEquals("at://did:plc:alice/space/app.activitytracker.space.athlete/interactions", uri.value)
        val body = calls.single { it.first == "com.atproto.simplespace.createSpace" }.second!!
        assertEquals(AthleteSpace.INTERACTIONS, body["skey"]?.jsonPrimitive?.content)
        assertEquals(current, body["readPolicy"]!!.jsonObject["managingApp"]?.jsonPrimitive?.content)
        assertEquals("com.atproto.simplespace.defs#memberListPolicy", body["writePolicy"]!!.jsonObject["\$type"]?.jsonPrimitive?.content)
    }

    @Test
    fun anExistingInteractionsSpaceIsLeftAloneOrRepointed() = runTest {
        exists = true
        spaces().ensure(AthleteSpace.INTERACTIONS)
        assertEquals(listOf("com.atproto.simplespace.createSpace", "com.atproto.simplespace.getSpace"), calls.map { it.first })

        calls.clear()
        readPolicy = "did:web:old-tunnel.test#activitytracker_space"
        spaces().ensure(AthleteSpace.INTERACTIONS)
        val update = calls.single { it.first == "com.atproto.simplespace.updateSpace" }.second!!
        assertEquals(current, update["readPolicy"]!!.jsonObject["managingApp"]?.jsonPrimitive?.content)
    }

    @Test fun theInteractionsSpaceIsNotATier() = runTest { assertEquals(false, AthleteSpace.INTERACTIONS in AthleteSpaces.TIERS) }

    @Test
    fun eachTierIsEnsuredOncePerSessionEvenConcurrently() = runTest {
        val spaces = spaces()
        List(3) { async { spaces.ensure(AthleteSpace.MUTUALS) } }.awaitAll()
        spaces.ensure(AthleteSpace.MUTUALS)
        assertEquals(1, calls.count { it.first == "com.atproto.simplespace.createSpace" })
    }

    @Test
    fun aFailedEnsureIsRetriedNextTime() = runTest {
        val spaces = spaces()
        failCreate = true
        assertFailsWith<XrpcException> { spaces.ensure(AthleteSpace.MUTUALS) }
        failCreate = false
        spaces.ensure(AthleteSpace.MUTUALS)
        assertEquals(2, calls.count { it.first == "com.atproto.simplespace.createSpace" })
    }

    @Test
    fun aDeclineGoesInTheSelfSpaceCreatingItFirst() = runTest {
        spaces().putDecline(Did("did:plc:bob"), Instant.parse("2026-10-13T09:00:00Z"))

        assertEquals(listOf("com.atproto.simplespace.createSpace", "com.atproto.space.putRecord"), calls.map { it.first })
        assertEquals(AthleteSpace.SELF, calls[0].second!!["skey"]?.jsonPrimitive?.content)
        val put = calls[1].second!!
        assertEquals("at://did:plc:alice/space/app.activitytracker.space.athlete/self", put["space"]?.jsonPrimitive?.content)
        assertEquals(Nsid.GRAPH_FOLLOW_DECLINE, put["collection"]?.jsonPrimitive?.content)
        assertEquals("did:plc:bob", put["rkey"]?.jsonPrimitive?.content)
        assertEquals("did:plc:bob", put["record"]!!.jsonObject["subject"]?.jsonPrimitive?.content)
    }

    @Test
    fun declinesAreListedAcrossPages() = runTest {
        declinePages = listOf(listOf("did:plc:bob", "not-a-did"), listOf("did:plc:carol"))
        assertEquals(setOf(Did("did:plc:bob"), Did("did:plc:carol")), spaces().declined())
        assertEquals(2, calls.count { it.first == "com.atproto.space.listRecords" })
    }

    @Test fun noSelfSpaceMeansNoDeclines() = runTest { assertEquals(emptySet(), spaces().declined()) }

    @Test
    fun aRecordThatDoesNotDecodeReadsAsNoDetails() = runTest {
        storedRecord = """{"${'$'}type":"app.activitytracker.actor.privateDetails","tier":"mutuals"}"""
        assertEquals(ProfileDetails(), spaces().details(alice, AthleteSpace.MUTUALS))
    }
}
