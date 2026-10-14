package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.atproto.SimpleSpace
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpaceAppAccess
import io.github.solcott.activitytracker.lexicon.atproto.SpacePolicy
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SpaceSerializationTest {
    private val alice = Did("did:plc:alice")
    private val space = SpaceUri.of(alice, Nsid.SPACE_ATHLETE, "mutuals")

    @Test
    fun spaceUriFormatsAndParses() {
        assertEquals("at://did:plc:alice/space/app.activitytracker.space.athlete/mutuals", space.value)
        val parsed = SpaceUri(space.value)
        assertEquals(alice, parsed.authority)
        assertEquals(Nsid.SPACE_ATHLETE, parsed.type)
        assertEquals("mutuals", parsed.skey)
    }

    @Test
    fun spaceUriRejectsOtherUris() {
        listOf(
                "at://did:plc:alice/app.activitytracker.feed.activity/3k",
                "at://did:plc:alice/space/app.activitytracker.space.athlete",
                "at://did:plc:alice/space/app.activitytracker.space.athlete/mutuals/extra",
                "at://alice.test/space/app.activitytracker.space.athlete/mutuals",
                "at://did:plc:alice/space//mutuals",
                "https://did:plc:alice/space/app.activitytracker.space.athlete/mutuals",
            )
            .forEach { assertFailsWith<IllegalArgumentException>(it) { SpaceUri(it) } }
    }

    @Test
    fun spaceUriIsAPlainStringParam() {
        val params = XrpcParams.encode(Space.GetRecord.Params.serializer(), Space.GetRecord.Params(space, alice, "c", "self"))
        assertEquals(listOf("space" to space.value, "repo" to "did:plc:alice", "collection" to "c", "rkey" to "self"), params)
    }

    @Test
    fun createSpaceWritesUnionTypes() {
        val input =
            SimpleSpace.CreateSpace.Input(
                spaceType = Nsid.SPACE_ATHLETE,
                skey = "mutuals",
                readPolicy = SpacePolicy.ManagingApp("did:web:appview.test#activitytracker_space"),
                writePolicy = SpacePolicy.MemberList(),
                appAccess = SpaceAppAccess.Open(),
            )
        val json = AtJson.encodeToJsonElement(SimpleSpace.CreateSpace.Input.serializer(), input).jsonObject
        val read = json["readPolicy"]!!.jsonObject
        assertEquals("com.atproto.simplespace.defs#managingAppPolicy", read["\$type"]?.jsonPrimitive?.content)
        assertEquals("did:web:appview.test#activitytracker_space", read["managingApp"]?.jsonPrimitive?.content)
        assertEquals("com.atproto.simplespace.defs#memberListPolicy", json["writePolicy"]!!.jsonObject["\$type"]?.jsonPrimitive?.content)
        assertEquals("com.atproto.simplespace.defs#open", json["appAccess"]!!.jsonObject["\$type"]?.jsonPrimitive?.content)
        assertEquals(input, AtJson.decodeFromJsonElement(SimpleSpace.CreateSpace.Input.serializer(), json))
    }

    @Test
    fun updateSpaceOmitsUnchangedPolicies() {
        val space = SpaceUri.of(Did("did:plc:alice"), Nsid.SPACE_ATHLETE, "mutuals")
        val input = SimpleSpace.UpdateSpace.Input(space, readPolicy = SpacePolicy.ManagingApp("did:web:appview.test#activitytracker_space"))
        val json = AtJson.encodeToJsonElement(SimpleSpace.UpdateSpace.Input.serializer(), input).jsonObject
        assertEquals(space.value, json["space"]?.jsonPrimitive?.content)
        assertEquals("com.atproto.simplespace.defs#managingAppPolicy", json["readPolicy"]!!.jsonObject["\$type"]?.jsonPrimitive?.content)
        assertEquals(setOf("space", "readPolicy"), json.keys)
        assertEquals(input, AtJson.decodeFromJsonElement(SimpleSpace.UpdateSpace.Input.serializer(), json))
    }

    @Test
    fun unknownPolicyRoundTrips() {
        val policy = buildJsonObject {
            put("\$type", "com.atproto.simplespace.defs#futurePolicy")
            put("setting", 3)
        }
        val decoded = AtJson.decodeFromJsonElement(SpacePolicy.serializer(), policy)
        assertIs<SpacePolicy.Unknown>(decoded)
        assertEquals(policy, AtJson.encodeToJsonElement(SpacePolicy.serializer(), decoded))
    }

    @Test
    fun recordInputsRoundTrip() {
        val put =
            Space.PutRecord.Input(
                space,
                alice,
                "app.activitytracker.actor.privateDetails",
                "self",
                buildJsonObject { put("weight", 70_000) },
            )
        val putJson = AtJson.encodeToJsonElement(Space.PutRecord.Input.serializer(), put).jsonObject
        assertEquals(space.value, putJson["space"]?.jsonPrimitive?.content)
        assertEquals("false", putJson["validate"]?.jsonPrimitive?.content)
        assertEquals(put, AtJson.decodeFromJsonElement(Space.PutRecord.Input.serializer(), putJson))

        val credential = Space.GetSpaceCredential.Input(space)
        val credentialJson = AtJson.encodeToJsonElement(Space.GetSpaceCredential.Input.serializer(), credential).jsonObject
        assertEquals(setOf("space"), credentialJson.keys)
        assertEquals(credential, AtJson.decodeFromJsonElement(Space.GetSpaceCredential.Input.serializer(), credentialJson))
    }

    @Test
    fun listRecordsDecodesWithoutValues() {
        val output =
            AtJson.decodeFromString(
                Space.ListRecords.Output.serializer(),
                """{"records":[{"collection":"c","rkey":"self","cid":"bafy"}],"cursor":"next"}""",
            )
        assertEquals(null, output.records.single().value)
        assertEquals("next", output.cursor)
    }

    @Test
    fun checkUserAccessParamsDecodeFromQuery() {
        val query = mapOf("space" to listOf(space.value), "user" to listOf("did:plc:bob"), "access" to listOf("read"))
        val params = XrpcParams.decode(SimpleSpace.CheckUserAccess.method.params, query)
        assertEquals(SimpleSpace.CheckUserAccess.Params(space, Did("did:plc:bob"), SimpleSpace.CheckUserAccess.READ), params)

        val withClient = XrpcParams.decode(SimpleSpace.CheckUserAccess.method.params, query + ("clientId" to listOf("https://app.test/c")))
        assertEquals("https://app.test/c", withClient.clientId)
    }

    @Test
    fun checkUserAccessRejectsMalformedParams() {
        val valid = mapOf("space" to listOf(space.value), "user" to listOf("did:plc:bob"), "access" to listOf("read"))
        listOf(
                valid + ("space" to listOf("at://did:plc:alice/app.activitytracker.feed.activity/3k")),
                valid + ("user" to listOf("bob.test")),
                valid - "access",
            )
            .forEach { query ->
                assertFailsWith<IllegalArgumentException>(query.toString()) {
                    XrpcParams.decode(SimpleSpace.CheckUserAccess.method.params, query)
                }
            }
    }

    @Test
    fun checkUserAccessOutputRoundTrips() {
        val json = AtJson.encodeToString(SimpleSpace.CheckUserAccess.method.output, SimpleSpace.CheckUserAccess.Output(authorized = false))
        assertEquals("""{"authorized":false}""", json)
        assertEquals(true, AtJson.decodeFromString(SimpleSpace.CheckUserAccess.method.output, """{"authorized":true}""").authorized)
    }
}
