package io.github.solcott.activitytracker.data.profile

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.ProfileRecord
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Alice answers follow requests against a fake PDS (her repo's approvals, her `self` space's declines, her profile record) and an AppView
 * serving her pending requests and her followers.
 */
class FollowRequestsRepositoryTest {
    private val alice = Did("did:plc:alice")

    /** Pending follows of Alice as the AppView pages them, declined ones included. */
    private var requestPages = listOf(listOf("did:plc:bob", "did:plc:carol"))

    /** Alice's declines (rkeys), once her `self` space exists. */
    private val declines = mutableSetOf<String>()
    private var selfSpace = false
    private var scopeMissing = false

    /** Alice's followers as the AppView pages them, and the approvals already in her repo. */
    private var followerPages = listOf<List<String>>()
    private val approvals = mutableSetOf<String>()

    /** Fail every `applyWrites` after this many have succeeded; null never fails. */
    private var failApplyWritesAfter: Int? = null
    private var profile: ProfileRecord? = ProfileRecord(displayName = "Alice")

    /** Each write's method and body, in order. */
    private val writes = mutableListOf<Pair<String, JsonObject>>()

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                fun error(name: String, status: HttpStatusCode = HttpStatusCode.BadRequest) = respond("""{"error":"$name"}""", status, json)
                fun body() =
                    AtJson.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
                val page = request.url.parameters["cursor"]?.toInt() ?: 0
                fun next(size: Int) = if (page + 1 < size) ""","cursor":"${page + 1}"""" else ""
                fun profiles(dids: List<String>) =
                    dids.joinToString(",") { """{"did":"$it","handle":"${it.substringAfterLast(':')}.test"}""" }
                when ("${request.url.host}${request.url.encodedPath}") {
                    "pds.test/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)

                    "appview.test/xrpc/${Nsid.GRAPH_GET_FOLLOW_REQUESTS}" ->
                        respond("""{"requests":[${profiles(requestPages[page])}]${next(requestPages.size)}}""", headers = json)

                    "appview.test/xrpc/${Nsid.GRAPH_GET_FOLLOWERS}" ->
                        respond(
                            """{"subject":{"did":"$alice","handle":"alice.test"},""" +
                                """"followers":[${profiles(followerPages.getOrElse(page) { emptyList() })}]${next(followerPages.size)}}""",
                            headers = json,
                        )

                    "appview.test/.well-known/did.json" ->
                        respond(
                            """{"id":"did:web:appview.test","service":[
                               {"id":"#activitytracker_space","type":"AtprotoSpaceService","serviceEndpoint":"http://appview.test"}]}""",
                            headers = json,
                        )

                    "pds.test/xrpc/com.atproto.space.listRecords" -> {
                        val held = declines
                        when {
                            scopeMissing -> error(SpaceErrors.SCOPE_MISSING, HttpStatusCode.Forbidden)
                            held == null -> error("SpaceNotFound")
                            else -> {
                                val records =
                                    held.joinToString(",") { """{"collection":"${Nsid.GRAPH_FOLLOW_DECLINE}","rkey":"$it","cid":"bafy"}""" }
                                respond("""{"records":[$records]}""", headers = json)
                            }
                        }
                    }

                    "pds.test/xrpc/com.atproto.simplespace.createSpace" -> {
                        selfSpace = true
                        respond("""{"uri":"at://$alice/space/${Nsid.SPACE_ATHLETE}/self"}""", headers = json)
                    }

                    "pds.test/xrpc/com.atproto.space.putRecord" -> {
                        val input = body().also { writes += "space.putRecord" to it }
                        declines += input.getValue("rkey").jsonPrimitive.content
                        respond("""{"uri":"at://$alice/x/y","cid":"bafyrec"}""", headers = json)
                    }

                    "pds.test/xrpc/com.atproto.space.deleteRecord" -> {
                        val input = body().also { writes += "space.deleteRecord" to it }
                        declines.remove(input.getValue("rkey").jsonPrimitive.content)
                        respond("{}", headers = json)
                    }

                    "pds.test/xrpc/com.atproto.repo.listRecords" -> {
                        val records =
                            approvals.joinToString(",") {
                                """{"uri":"at://$alice/${Nsid.GRAPH_FOLLOW_APPROVAL}/$it","cid":"bafy","value":{}}"""
                            }
                        respond("""{"records":[$records]}""", headers = json)
                    }

                    "pds.test/xrpc/com.atproto.repo.applyWrites" -> {
                        val done = writes.count { it.first == "applyWrites" }
                        if (failApplyWritesAfter?.let { done >= it } == true) {
                            error("InternalServerError", HttpStatusCode.InternalServerError)
                        } else {
                            val input = body().also { writes += "applyWrites" to it }
                            input.getValue("writes").jsonArray.mapTo(approvals) { it.jsonObject.getValue("rkey").jsonPrimitive.content }
                            respond("{}", headers = json)
                        }
                    }

                    "pds.test/xrpc/com.atproto.repo.getRecord" -> {
                        val current = profile
                        if (current == null) error("RecordNotFound")
                        else
                            respond(
                                """{"uri":"at://$alice/${Nsid.ACTOR_PROFILE}/self","value":${
                                    AtJson.encodeToString(ProfileRecord.serializer(), current)
                                }}""",
                                headers = json,
                            )
                    }

                    "pds.test/xrpc/com.atproto.repo.putRecord" -> {
                        val input = body().also { writes += "putRecord" to it }
                        if (input.getValue("collection").jsonPrimitive.content == Nsid.ACTOR_PROFILE) {
                            profile = AtJson.decodeFromJsonElement(ProfileRecord.serializer(), input.getValue("record"))
                        }
                        respond("""{"uri":"at://$alice/x/y","cid":"bafyrec"}""", headers = json)
                    }

                    "pds.test/xrpc/com.atproto.repo.deleteRecord" -> {
                        writes += "deleteRecord" to body()
                        respond("{}", headers = json)
                    }

                    "pds.test/xrpc/com.atproto.space.getRecord" -> error("RecordNotFound")

                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        ) {
            expectSuccess = false
        }

    private suspend fun repository() =
        ProfileRepository(signedInTestSession(http, did = alice).clients, Clock.System, Logger, TrainingNarrowing {})

    private suspend fun requests() = FollowRequestsRepository(signedInTestSession(http, did = alice).clients, Clock.System, Logger)

    private fun edit(policy: String?) = ProfileEdit("Alice", "", ProfileDetails(), DetailsVisibility(), followPolicy = policy)

    @Test
    fun declinedRequestsAreSplitOff() = runTest {
        selfSpace = true
        declines += listOf("did:plc:carol", "did:plc:dave")
        val requests = requests().followRequests()
        assertEquals(listOf("did:plc:bob"), requests.pending.map { it.did.value })
        // Dave declined earlier but no longer asks: not listed at all.
        assertEquals(listOf("did:plc:carol"), requests.declined.map { it.did.value })
        assertFalse(requests.truncated)
    }

    @Test
    fun requestsAreReadUpToTheCap() = runTest {
        requestPages = List(7) { listOf("did:plc:a$it") }
        val requests = requests().followRequests()
        assertEquals(5, requests.pending.size)
        assertTrue(requests.truncated)
    }

    @Test
    fun aSessionWithoutTheDeclineScopeStillListsRequests() = runTest {
        scopeMissing = true
        val requests = requests().followRequests()
        assertEquals(2, requests.pending.size)
        assertTrue(requests.needsSignIn)
    }

    @Test
    fun approvingWritesTheApprovalAtTheFollowersDidAndClearsTheDecline() = runTest {
        selfSpace = true
        declines += "did:plc:carol"
        requests().approve(Did("did:plc:carol"), declined = true)

        val (method, put) = writes[0]
        assertEquals("putRecord", method)
        assertEquals(Nsid.GRAPH_FOLLOW_APPROVAL, put["collection"]?.jsonPrimitive?.content)
        assertEquals("did:plc:carol", put["rkey"]?.jsonPrimitive?.content)
        assertEquals("did:plc:carol", put.getValue("record").jsonObject["subject"]?.jsonPrimitive?.content)
        assertEquals("space.deleteRecord", writes[1].first)
        assertEquals(emptySet<String>(), declines.toSet())
    }

    @Test
    fun decliningWritesOnlyToTheSelfSpace() = runTest {
        requests().decline(Did("did:plc:bob"))
        assertEquals(listOf("space.putRecord"), writes.map { it.first })
        assertEquals(Nsid.GRAPH_FOLLOW_DECLINE, writes[0].second["collection"]?.jsonPrimitive?.content)
        assertTrue(writes[0].second["space"]?.jsonPrimitive?.content.orEmpty().endsWith("/self"))
        assertEquals(setOf("did:plc:bob"), declines.toSet())
    }

    @Test
    fun removingAFollowerDeletesTheirApproval() = runTest {
        requests().removeFollower(Did("did:plc:bob"))
        val (method, input) = writes.single()
        assertEquals("deleteRecord", method)
        assertEquals(Nsid.GRAPH_FOLLOW_APPROVAL, input["collection"]?.jsonPrimitive?.content)
        assertEquals("did:plc:bob", input["rkey"]?.jsonPrimitive?.content)
    }

    @Test
    fun onlyDidsThatAreRecordKeysCanBeAnswered() = runTest {
        val repo = requests()
        assertTrue(repo.canAnswer(Did("did:plc:bob")))
        assertFalse(repo.canAnswer(Did("did:web:localhost%3A8081")))
    }

    @Test
    fun turningApprovalOnApprovesCurrentFollowersFirst() = runTest {
        followerPages = listOf(List(150) { "did:plc:f$it" }, List(150) { "did:plc:g$it" })
        approvals += "did:plc:f0"
        repository().save(edit(FollowPolicy.APPROVE))

        assertEquals(listOf("applyWrites", "applyWrites", "putRecord"), writes.map { it.first })
        assertEquals(200, writes[0].second.getValue("writes").jsonArray.size)
        assertEquals(99, writes[1].second.getValue("writes").jsonArray.size, "f0 was approved already")
        assertEquals(300, approvals.size)
        assertEquals(FollowPolicy.APPROVE, profile?.followPolicy)
    }

    @Test
    fun aFailedApprovalBatchLeavesThePolicyOpenAndARetryWritesTheRest() = runTest {
        followerPages = listOf(List(300) { "did:plc:f$it" })
        failApplyWritesAfter = 1
        assertFailsWith<XrpcException> { repository().save(edit(FollowPolicy.APPROVE)) }
        assertNull(profile?.followPolicy)
        assertEquals(200, approvals.size)

        failApplyWritesAfter = null
        writes.clear()
        repository().save(edit(FollowPolicy.APPROVE))
        assertEquals(100, writes.first { it.first == "applyWrites" }.second.getValue("writes").jsonArray.size)
        assertEquals(FollowPolicy.APPROVE, profile?.followPolicy)
    }

    @Test
    fun turningApprovalOffJustSavesThePolicy() = runTest {
        profile = ProfileRecord(displayName = "Alice", followPolicy = FollowPolicy.APPROVE)
        followerPages = listOf(listOf("did:plc:bob"))
        repository().save(edit(FollowPolicy.OPEN))
        assertEquals(listOf("putRecord"), writes.map { it.first })
        assertNull(profile?.followPolicy)
    }

    @Test
    fun aSaveThatLeavesThePolicyKeepsIt() = runTest {
        profile = ProfileRecord(displayName = "Alice", followPolicy = FollowPolicy.APPROVE)
        repository().save(edit(null))
        assertEquals(FollowPolicy.APPROVE, profile?.followPolicy)
        assertEquals(FollowPolicy.APPROVE, repository().ownProfile().followPolicy)
    }
}
