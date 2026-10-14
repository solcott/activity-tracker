package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.space.CreateSpaceResult
import io.github.solcott.activitytracker.atproto.space.SpaceClient
import io.github.solcott.activitytracker.atproto.space.SpaceCredentials
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.atproto.xrpc.XrpcBytes
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpacePolicy
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SpaceClientTest {
    private val f = SpaceFixtures()
    private val collection = "app.activitytracker.actor.privateDetails"
    private val getRecord = "alicepds.test/xrpc/com.atproto.space.getRecord"
    private val record = """{"uri":"at://did:plc:alice/$collection/self","cid":"bafyrec","value":{"weight":70000}}"""

    /** bob, reading alice's spaces as a member. */
    private suspend fun viewer(key: DpopKey? = null): SpaceClient {
        val credentials = SpaceCredentials(f.viewerPds, f.network.client, f.identity, { key ?: DpopKey.generate() }, f.clock)
        return SpaceClient(f.bob, f.viewerPds, credentials, f.network.client)
    }

    /** alice, in her own spaces. */
    private fun owner(): SpaceClient {
        val pds = XrpcClient(f.network.client, "http://alicepds.test")
        return SpaceClient(f.alice, pds, SpaceCredentials(pds, f.network.client, f.identity, clock = f.clock), f.network.client)
    }

    @Test
    fun memberReadSendsSignedCredentialToTheRepoOwnersPds() = runTest {
        f.network.on(getRecord) { respondJson(record) }
        val key = DpopKey.generate()

        val read = viewer(key).getRecord(f.aliceSpace, f.alice, collection, "self")

        assertEquals("70000", assertIs<SpaceRead.Found<Space.GetRecord.Output>>(read).value.value["weight"]?.jsonPrimitive?.content)
        val request = f.requestsTo(getRecord).single()
        assertEquals("Atproto-Space ${SpaceFixtures.credential(f.clock.now.epochSeconds + 600)}", request.headers["Authorization"])
        assertEquals("did:plc:alice", request.headers["Atproto-Space-Audience"])
        assertEquals("""atproto-space=("authorization" "atproto-space-audience")""", request.headers["Signature-Input"])
        assertTrue(request.headers["Signature"].orEmpty().startsWith("atproto-space=:"))
        assertEquals(f.aliceSpace.value, request.url.parameters["space"])
        assertEquals("self", request.url.parameters["rkey"])
    }

    @Test
    fun repeatedMemberReadsReuseTheCredential() = runTest {
        f.network.on(getRecord) { respondJson(record) }
        val client = viewer()
        repeat(3) { client.getRecord(f.aliceSpace, f.alice, collection, "self") }
        assertEquals(1, f.exchanges)
        assertEquals(3, f.requestsTo(getRecord).size)
    }

    @Test
    fun missingRecordIsNotFound() = runTest {
        f.network.on(getRecord) { respondJson("""{"error":"RecordNotFound","message":"none"}""", HttpStatusCode.BadRequest) }
        assertEquals(SpaceRead.NotFound, viewer().getRecord(f.aliceSpace, f.alice, collection, "self"))
    }

    @Test
    fun refusedCredentialIsNoAccess() = runTest {
        f.network.on("alicepds.test/xrpc/com.atproto.space.getSpaceCredential") {
            respondJson("""{"error":"UserNotAuthorized","message":"not a mutual"}""", HttpStatusCode.BadRequest)
        }
        assertEquals(SpaceRead.NoAccess, viewer().getRecord(f.aliceSpace, f.alice, collection, "self"))
        assertTrue(f.requestsTo(getRecord).isEmpty())
    }

    @Test
    fun undisclosedRefusalIsNoAccess() = runTest {
        f.network.on("alicepds.test/xrpc/com.atproto.space.getSpaceCredential") {
            respondJson("""{"error":"NotAuthorized","message":"no"}""", HttpStatusCode.BadRequest)
        }
        assertEquals(SpaceRead.NoAccess, viewer().getRecord(f.aliceSpace, f.alice, collection, "self"))
    }

    @Test
    fun expiredCredentialIsReplacedAndTheReadRetriedOnce() = runTest {
        var calls = 0
        f.network.on(getRecord) {
            if (calls++ == 0) respondJson("""{"error":"JwtExpired","message":"exp"}""", HttpStatusCode.Unauthorized)
            else respondJson(record)
        }
        assertIs<SpaceRead.Found<*>>(viewer().getRecord(f.aliceSpace, f.alice, collection, "self"))
        assertEquals(2, f.exchanges)
        assertEquals(2, calls)
    }

    @Test
    fun revokedCredentialIsReplacedButOnlyOnce() = runTest {
        f.network.on(getRecord) { respondJson("""{"error":"CredentialRevoked","message":"revoked"}""", HttpStatusCode.Unauthorized) }
        val e = assertFailsWith<XrpcException> { viewer().getRecord(f.aliceSpace, f.alice, collection, "self") }
        assertEquals("CredentialRevoked", e.error)
        assertEquals(2, f.exchanges)
        assertEquals(2, f.requestsTo(getRecord).size)
    }

    @Test
    fun unreachablePdsIsAnErrorNotNoAccess() = runTest {
        f.network.on(getRecord) { throw IllegalStateException("connection refused") }
        assertFails { viewer().getRecord(f.aliceSpace, f.alice, collection, "self") }
    }

    @Test
    fun ownerReadsTheirOwnRecordWithoutACredential() = runTest {
        f.network.on(getRecord) { respondJson(record) }
        assertIs<SpaceRead.Found<*>>(owner().getRecord(f.aliceSpace, f.alice, collection, "self"))
        assertEquals(0, f.delegations)
        assertEquals(0, f.exchanges)
        assertNull(f.requestsTo(getRecord).single().headers["Atproto-Space-Audience"])
    }

    @Test
    fun listRecordsPassesPagingAndReturnsTheCursor() = runTest {
        f.network.on("alicepds.test/xrpc/com.atproto.space.listRecords") {
            respondJson("""{"records":[{"collection":"$collection","rkey":"self","cid":"bafyrec","value":{}}],"cursor":"next"}""")
        }
        val read = viewer().listRecords(f.aliceSpace, f.alice, collection, limit = 1, cursor = "start")
        val output = assertIs<SpaceRead.Found<Space.ListRecords.Output>>(read).value
        assertEquals("next", output.cursor)
        assertEquals(1, output.records.size)
        val params = f.requestsTo("alicepds.test/xrpc/com.atproto.space.listRecords").single().url.parameters
        assertEquals("1", params["limit"])
        assertEquals("start", params["cursor"])
    }

    @Test
    fun getBlobReturnsBytes() = runTest {
        f.network.on("alicepds.test/xrpc/com.atproto.space.getBlob") {
            respond(byteArrayOf(1, 2, 3), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/octet-stream"))
        }
        val read = viewer().getBlob(f.aliceSpace, f.alice, Cid("bafkblob"))
        val blob = assertIs<SpaceRead.Found<XrpcBytes>>(read).value
        assertEquals(listOf<Byte>(1, 2, 3), blob.bytes.toList())
    }

    @Test
    fun createSpaceSendsPoliciesAndOpenAppAccess() = runTest {
        f.network.on("alicepds.test/xrpc/com.atproto.simplespace.createSpace") { respondJson("""{"uri":"${f.aliceSpace.value}"}""") }
        val result =
            owner()
                .createSpace(
                    Nsid.SPACE_ATHLETE,
                    "mutuals",
                    SpacePolicy.ManagingApp("did:web:appview.test#activitytracker_space"),
                    SpacePolicy.ManagingApp("did:web:appview.test#activitytracker_space"),
                )
        assertEquals(CreateSpaceResult.Created(f.aliceSpace), result)
        val body = f.requestsTo("alicepds.test/xrpc/com.atproto.simplespace.createSpace").single().jsonBody()
        assertEquals(Nsid.SPACE_ATHLETE, body["spaceType"]?.jsonPrimitive?.content)
        assertEquals("mutuals", body["skey"]?.jsonPrimitive?.content)
        assertEquals("com.atproto.simplespace.defs#managingAppPolicy", body["readPolicy"]!!.jsonObject["\$type"]?.jsonPrimitive?.content)
        assertEquals("com.atproto.simplespace.defs#open", body["appAccess"]!!.jsonObject["\$type"]?.jsonPrimitive?.content)
    }

    @Test
    fun createExistingSpaceReportsItExists() = runTest {
        f.network.on("alicepds.test/xrpc/com.atproto.simplespace.createSpace") {
            respondJson("""{"error":"SpaceAlreadyExists","message":"exists"}""", HttpStatusCode.BadRequest)
        }
        val result = owner().createSpace(Nsid.SPACE_ATHLETE, "mutuals", SpacePolicy.MemberList(), SpacePolicy.MemberList())
        assertEquals(CreateSpaceResult.AlreadyExists(f.aliceSpace), result)
    }

    @Test
    fun getSpaceIsNullWhenThereIsNone() = runTest {
        f.network.on("alicepds.test/xrpc/com.atproto.simplespace.getSpace") {
            respondJson("""{"error":"SpaceNotFound","message":"none"}""", HttpStatusCode.BadRequest)
        }
        assertNull(owner().getSpace(f.aliceSpace))
    }

    @Test
    fun updateSpaceSendsOnlyTheGivenPolicy() = runTest {
        f.network.on("alicepds.test/xrpc/com.atproto.simplespace.updateSpace") { respondJson("{}") }
        owner().updateSpace(f.aliceSpace, readPolicy = SpacePolicy.ManagingApp("did:web:new.test#activitytracker_space"))

        val body = f.requestsTo("alicepds.test/xrpc/com.atproto.simplespace.updateSpace").single().jsonBody()
        assertEquals(setOf("space", "readPolicy"), body.keys)
        assertEquals(f.aliceSpace.value, body["space"]?.jsonPrimitive?.content)
        assertEquals("did:web:new.test#activitytracker_space", body["readPolicy"]!!.jsonObject["managingApp"]?.jsonPrimitive?.content)
    }

    @Test
    fun updateMissingSpaceIsAnError() = runTest {
        f.network.on("alicepds.test/xrpc/com.atproto.simplespace.updateSpace") {
            respondJson("""{"error":"SpaceNotFound","message":"none"}""", HttpStatusCode.BadRequest)
        }
        val e = assertFailsWith<XrpcException> { owner().updateSpace(f.aliceSpace, readPolicy = SpacePolicy.MemberList()) }
        assertEquals("SpaceNotFound", e.error)
    }

    @Test
    fun putAndDeleteWriteToTheAthletesOwnRepo() = runTest {
        f.network.on("alicepds.test/xrpc/com.atproto.space.putRecord") {
            respondJson("""{"uri":"at://did:plc:alice/$collection/self","cid":"bafyrec"}""")
        }
        f.network.on("alicepds.test/xrpc/com.atproto.space.deleteRecord") { respondJson("{}") }
        val client = owner()
        client.putRecord(f.aliceSpace, collection, "self", buildJsonObject { put("weight", 70000) })
        client.deleteRecord(f.aliceSpace, collection, "self")

        val put = f.requestsTo("alicepds.test/xrpc/com.atproto.space.putRecord").single().jsonBody()
        assertEquals("did:plc:alice", put["repo"]?.jsonPrimitive?.content)
        assertEquals(f.aliceSpace.value, put["space"]?.jsonPrimitive?.content)
        assertEquals("70000", put["record"]!!.jsonObject["weight"]?.jsonPrimitive?.content)
        val delete = f.requestsTo("alicepds.test/xrpc/com.atproto.space.deleteRecord").single().jsonBody()
        assertEquals("self", delete["rkey"]?.jsonPrimitive?.content)
    }

    private fun HttpRequestData.jsonBody(): JsonObject =
        AtJson.decodeFromString(JsonObject.serializer(), (body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
}
