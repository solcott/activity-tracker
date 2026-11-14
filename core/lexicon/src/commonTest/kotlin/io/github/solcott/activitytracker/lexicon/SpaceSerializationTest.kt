package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.atproto.DescribeServer
import io.github.solcott.activitytracker.lexicon.atproto.ManagedSpace
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpacePolicy
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUpdate
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
    fun describeServerReadsTheSpacesFlag() {
        // As the local Spaces PDS answers, and as one without the flag.
        val spaces =
            AtJson.decodeFromString(
                DescribeServer.Output.serializer(),
                """{"did":"did:web:localhost","availableUserDomains":[".test"],"inviteCodeRequired":false,""" +
                    """"blobUploadLimit":10485760,"links":{},"contact":{},"spacesAlpha":true}""",
            )
        assertEquals(Did("did:web:localhost"), spaces.did)
        assertEquals(true, spaces.spacesAlpha)
        val plain =
            AtJson.decodeFromString(
                DescribeServer.Output.serializer(),
                """{"did":"did:web:bsky.social","availableUserDomains":[".bsky.social"],"inviteCodeRequired":false}""",
            )
        assertEquals(null, plain.spacesAlpha)
    }

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
        assertEquals(
            listOf(
                QueryParam("space", space.value),
                QueryParam("repo", "did:plc:alice"),
                QueryParam("collection", "c"),
                QueryParam("rkey", "self"),
            ),
            params,
        )
    }

    @Test
    fun syncerPayloadsDecode() {
        // Shapes as the Nov 12 lexicons in the pinned PDS image define them.
        val ops =
            AtJson.decodeFromString(
                Space.ListRepoOps.Output.serializer(),
                """{"ops":[{"rev":"3mxtoijnsfd2r","collection":"c","rkey":"n1","cid":"bafyput","prev":null,"value":{"text":"x"}},""" +
                    """{"rev":"3mxtoijsskd2r","collection":"c","rkey":"n1","cid":null,"prev":"bafyput"}],""" +
                    """"commit":{"ver":1,"repo":"did:plc:alice","space":"${space.value}","rev":"3mxtoijsskd2r",""" +
                    """"data":{"${'$'}bytes":"AAECAwQ"}}}""",
            )
        assertEquals(listOf("bafyput", null), ops.ops.map { it.cid?.value })
        assertEquals(null, ops.ops[1].value)
        val commit = ops.commit
        assertEquals("3mxtoijsskd2r", commit.rev)
        assertEquals(1, commit.ver)
        assertEquals(space, commit.space)
        assertEquals("did:plc:alice", commit.repo.value)
        assertContentEquals(byteArrayOf(0, 1, 2, 3, 4), commit.data.decode())
        assertEquals(
            commit,
            AtJson.decodeFromJsonElement(
                Space.ListRepoOps.Commit.serializer(),
                AtJson.encodeToJsonElement(Space.ListRepoOps.Commit.serializer(), commit),
            ),
        )
        assertEquals(null, ops.cursor)
    }

    @Test
    fun notifySpaceUpdateDecodesEveryUpdate() {
        fun update(json: String) =
            AtJson.decodeFromString(
                    Space.NotifySpaceUpdate.Input.serializer(),
                    """{"space":"${space.value}","token":"t0k","update":$json}""",
                )
                .also { assertEquals("t0k", it.token) }
                .update

        val write =
            assertIs<SpaceUpdate.RepoWrite>(
                update(
                    """{"${'$'}type":"com.atproto.space.defs#repoWrite","repo":"did:plc:alice","repoRev":"3mxtoijnsfd2r",""" +
                        """"data":{"${'$'}bytes":"7DDo6UqL"},"spaceRev":"3mxtoijoa2t2r"}""",
                ),
            )
        assertEquals("3mxtoijnsfd2r", write.repoRev)
        assertEquals(null, write.prevSpaceRev)
        assertEquals("7DDo6UqL", write.data?.base64)
        val removed =
            assertIs<SpaceUpdate.RepoRemoved>(
                update("""{"${'$'}type":"com.atproto.space.defs#repoRemoved","repo":"did:plc:bob","spaceRev":"3m","prevSpaceRev":"3l"}"""),
            )
        assertEquals(Did("did:plc:bob"), removed.repo)
        assertEquals("3l", removed.prevSpaceRev)
        assertIs<SpaceUpdate.SpaceDeleted>(update("""{"${'$'}type":"com.atproto.space.notifySpaceUpdate#spaceDeleted"}"""))
        assertEquals(
            "3n",
            assertIs<SpaceUpdate.RegistrationExpired>(
                    update("""{"${'$'}type":"com.atproto.space.notifySpaceUpdate#registrationExpired","spaceRev":"3n"}"""),
                )
                .spaceRev,
        )
        // Only a full $type matches: the bare fragment, or a newer kind, is unknown and round-trips.
        val unknown = assertIs<SpaceUpdate.Unknown>(update("""{"${'$'}type":"#spaceDeleted"}"""))
        assertEquals(unknown.json, AtJson.encodeToJsonElement(SpaceUpdate.serializer(), unknown))
        assertEquals(
            """{"repo":"did:plc:alice","spaceRev":"3m","${'$'}type":"com.atproto.space.defs#repoRemoved"}""",
            AtJson.encodeToString(SpaceUpdate.serializer(), SpaceUpdate.RepoRemoved(alice, "3m")),
        )
    }

    @Test
    fun confirmRegistrationRoundTrips() {
        val input =
            AtJson.decodeFromString(
                Space.ConfirmRegistration.method.input,
                """{"space":"${space.value}","token":"t0k","challenge":"c4a11"}""",
            )
        assertEquals(Space.ConfirmRegistration.Input(space, "t0k", "c4a11"), input)
        assertEquals(
            """{"challenge":"c4a11"}""",
            AtJson.encodeToString(Space.ConfirmRegistration.method.output, Space.ConfirmRegistration.Output("c4a11")),
        )
    }

    @Test
    fun createSpaceWritesThePolicyUnion() {
        val input =
            Space.CreateSpace.Input(
                spaceType = Nsid.SPACE_ATHLETE,
                skey = "mutuals",
                policy = SpacePolicy.Managed("did:web:appview.test#activitytracker_space"),
            )
        val json = AtJson.encodeToJsonElement(Space.CreateSpace.Input.serializer(), input).jsonObject
        val policy = json["policy"]!!.jsonObject
        assertEquals("com.atproto.managedspace.policy", policy["\$type"]?.jsonPrimitive?.content)
        assertEquals("did:web:appview.test#activitytracker_space", policy["managingApp"]?.jsonPrimitive?.content)
        assertEquals(setOf("spaceType", "skey", "policy"), json.keys)
        assertEquals(input, AtJson.decodeFromJsonElement(Space.CreateSpace.Input.serializer(), json))
    }

    @Test
    fun everyPolicyRoundTrips() {
        val policies =
            listOf(
                SpacePolicy.Personal(),
                SpacePolicy.Member(publicRead = true, allowedApps = listOf("https://app.test/client-metadata.json"), publishMembers = true),
                SpacePolicy.Managed("did:web:appview.test#activitytracker_space", publishPolicy = true),
            )
        for (policy in policies) {
            val json = AtJson.encodeToJsonElement(SpacePolicy.serializer(), policy)
            assertEquals(policy, AtJson.decodeFromJsonElement(SpacePolicy.serializer(), json))
        }
        val personal = AtJson.encodeToJsonElement(SpacePolicy.serializer(), SpacePolicy.Personal()).jsonObject
        assertEquals("com.atproto.personalspace.policy", personal["\$type"]?.jsonPrimitive?.content)
        val member = AtJson.encodeToJsonElement(SpacePolicy.serializer(), SpacePolicy.Member()).jsonObject
        assertEquals("com.atproto.memberspace.policy", member["\$type"]?.jsonPrimitive?.content)
        assertEquals(null, member["allowedApps"])
    }

    @Test
    fun policyWithDefaultsLeftOutEqualsOneBuiltWithThem() {
        val managed =
            AtJson.decodeFromString(
                SpacePolicy.serializer(),
                """{"${'$'}type":"com.atproto.managedspace.policy","managingApp":"did:web:appview.test#activitytracker_space"}""",
            )
        assertEquals(SpacePolicy.Managed("did:web:appview.test#activitytracker_space"), managed)
        val member = AtJson.decodeFromString(SpacePolicy.serializer(), """{"${'$'}type":"com.atproto.memberspace.policy"}""")
        assertEquals(SpacePolicy.Member(), member)
    }

    @Test
    fun updateSpaceSendsTheWholePolicy() {
        val input = Space.UpdateSpace.Input(space, SpacePolicy.Managed("did:web:appview.test#activitytracker_space"))
        val json = AtJson.encodeToJsonElement(Space.UpdateSpace.Input.serializer(), input).jsonObject
        assertEquals(space.value, json["space"]?.jsonPrimitive?.content)
        assertEquals(setOf("space", "policy"), json.keys)
        assertEquals(setOf("\$type", "managingApp", "publishPolicy"), json["policy"]!!.jsonObject.keys)
        assertEquals(input, AtJson.decodeFromJsonElement(Space.UpdateSpace.Input.serializer(), json))
    }

    @Test
    fun getSpaceOutputDecodes() {
        val output =
            AtJson.decodeFromString(
                Space.GetSpace.method.output,
                """{"space":"${space.value}","policy":""" +
                    """{"${'$'}type":"com.atproto.managedspace.policy","managingApp":"did:web:a.test#x","publishPolicy":false}}""",
            )
        assertEquals(Space.GetSpace.Output(space, SpacePolicy.Managed("did:web:a.test#x")), output)
    }

    @Test
    fun unknownPolicyRoundTrips() {
        val policy = buildJsonObject {
            put("\$type", "com.atproto.groupspace.policy")
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

        val credential = Space.AuthorizeSpaceSync.Input(space, "did:key:zDnaeKey", delegationToken = "eyJ.e30.c2ln")
        val credentialJson = AtJson.encodeToJsonElement(Space.AuthorizeSpaceSync.Input.serializer(), credential).jsonObject
        assertEquals(setOf("space", "credentialKey", "delegationToken"), credentialJson.keys)
        assertEquals(credential, AtJson.decodeFromJsonElement(Space.AuthorizeSpaceSync.Input.serializer(), credentialJson))

        val notified = credential.copy(notify = Space.AuthorizeSpaceSync.Notify("https://appview.test", "t0k"))
        val notifiedJson = AtJson.encodeToJsonElement(Space.AuthorizeSpaceSync.Input.serializer(), notified).jsonObject
        assertEquals("""{"endpoint":"https://appview.test","token":"t0k"}""", notifiedJson["notify"].toString())
        val output = AtJson.decodeFromString(Space.AuthorizeSpaceSync.method.output, """{"credential":"eyJ","spaceRev":"3m"}""")
        assertEquals("3m", output.spaceRev)
        assertEquals(null, AtJson.decodeFromString(Space.AuthorizeSpaceSync.method.output, """{"credential":"eyJ"}""").spaceRev)
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
    fun checkAccessParamsDecodeFromQuery() {
        val query = mapOf("space" to listOf(space.value), "repo" to listOf("did:plc:bob"))
        val params = XrpcParams.decode(ManagedSpace.CheckAccess.method.params, query)
        assertEquals(ManagedSpace.CheckAccess.Params(space, Did("did:plc:bob")), params)

        val withClient = XrpcParams.decode(ManagedSpace.CheckAccess.method.params, query + ("clientId" to listOf("https://app.test/c")))
        assertEquals("https://app.test/c", withClient.clientId)
        // A request with no delegation token is asked about with no account.
        assertEquals(null, XrpcParams.decode(ManagedSpace.CheckAccess.method.params, query - "repo").repo)
    }

    @Test
    fun checkAccessRejectsMalformedParams() {
        val valid = mapOf("space" to listOf(space.value), "repo" to listOf("did:plc:bob"))
        listOf(
                valid + ("space" to listOf("at://did:plc:alice/app.activitytracker.feed.activity/3k")),
                valid + ("repo" to listOf("bob.test")),
                valid - "space",
            )
            .forEach { query ->
                assertFailsWith<IllegalArgumentException>(query.toString()) {
                    XrpcParams.decode(ManagedSpace.CheckAccess.method.params, query)
                }
            }
    }

    @Test
    fun checkAccessOutputRoundTrips() {
        val json =
            AtJson.encodeToString(ManagedSpace.CheckAccess.method.output, ManagedSpace.CheckAccess.Output(read = true, write = false))
        assertEquals("""{"read":true,"write":false}""", json)
        assertEquals(true, AtJson.decodeFromString(ManagedSpace.CheckAccess.method.output, """{"read":true,"write":true}""").write)
    }
}
