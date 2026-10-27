package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.MediaUrls
import io.github.solcott.activitytracker.appview.api.PrivateMediaTokens
import io.github.solcott.activitytracker.appview.api.SpaceBlobs
import io.github.solcott.activitytracker.appview.db.ModerationAccess
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.moderation.OzoneClient
import io.github.solcott.activitytracker.atproto.xrpc.XrpcBytes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetModerationRole
import io.github.solcott.activitytracker.lexicon.app.GetSubject
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.http.formUrlEncode
import io.ktor.http.headersOf
import io.ktor.server.testing.testApplication
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.jdbc.selectAll

/** `moderation/private-subjects`: moderators open reported non-public subjects, only while under review, every view logged. */
class ModeratorSubjectsTest : ModerationTestBase() {
    private val moderator = carol
    private val triage = "did:plc:triage"

    /** The labeler's team, by DID: Ozone role names. */
    private val team = mutableMapOf(moderator to "tools.ozone.team.defs#roleModerator", triage to "tools.ozone.team.defs#roleTriage")

    /** Ozone's review state per subject (an AT-URI or a DID), with its status id. */
    private val reviews = mutableMapOf<String, Pair<Long, String>>()
    private var ozoneDown = false
    private val ozoneCalls = mutableListOf<String>()

    private val ozoneHttp =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                val path = request.url.encodedPath
                when {
                    path.endsWith("createSession") -> respond("""{"accessJwt":"triage-session","did":"$triage"}""", headers = json)
                    path.endsWith("getServiceAuth") -> respond("""{"token":"for-${request.url.parameters["lxm"]}"}""", headers = json)
                    ozoneDown -> throw IOException("connection refused")
                    path.endsWith(OzoneClient.LIST_MEMBERS) -> {
                        ozoneCalls += OzoneClient.LIST_MEMBERS
                        val members = team.entries.joinToString(",") { (did, role) -> """{"did":"$did","role":"$role"}""" }
                        respond("""{"members":[$members]}""", headers = json)
                    }
                    path.endsWith(OzoneClient.QUERY_STATUSES) -> {
                        val subject = request.url.parameters["subject"]!!
                        ozoneCalls += "${OzoneClient.QUERY_STATUSES} $subject"
                        val statuses =
                            reviews[subject]?.let { (id, state) -> """[{"id":$id,"reviewState":"tools.ozone.moderation.defs#$state"}]""" }
                        respond("""{"subjectStatuses":${statuses ?: "[]"}}""", headers = json)
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        )
    private val ozone =
        OzoneClient(ozoneHttp, AppViewConfig.Labeler(labeler, "http://ozone.test"), AppViewConfig.Triage(triage, "app-password")) { _, _ ->
            "http://pds.test"
        }

    private val photoCid = "bafkreihwzzyxpay7qndclffr6usq5te3of4fra7yucklue2b5ckxhq7aeq"
    private val shoesCid = "bafkreiab5jo52o7fi55mh2xcgzx2cidemfoeqwsuc2bnwptc2kbsiecvuy"
    private val spaceBlobs = SpaceBlobs { _, repo, cid ->
        if (repo.value == bob && cid in setOf(photoCid, shoesCid)) XrpcBytes(byteArrayOf(7), "image/jpeg") else null
    }
    private val mediaClock = MutableClock(Instant.parse("2026-10-15T12:00:01Z"))
    private val mediaTokens = PrivateMediaTokens(TEST_SIGNING_KEY, mediaClock)

    private fun postWithPhoto(rkey: String, tier: String) {
        val json =
            """{"sport":"run","startedAt":"2026-10-02T07:00:00Z","elapsedTime":1800,"createdAt":"2026-10-02T07:31:00Z",""" +
                """"media":[{"${'$'}type":"${Nsid.FEED_ACTIVITY}#image","image":{"${'$'}type":"blob","ref":{"${'$'}link":"$photoCid"},""" +
                """"mimeType":"image/jpeg","size":1}}]}"""
        runBlocking {
            indexer.spaceWrite(
                SpaceUri.of(Did(bob), Nsid.SPACE_ATHLETE, tier),
                Nsid.FEED_ACTIVITY,
                rkey,
                "bafy$rkey",
                AtJson.parseToJsonElement(json).jsonObject,
            )
        }
    }

    /** [json] at [rkey] of [collection] in bob's [tier] space. */
    private fun spacePut(tier: String, collection: String, rkey: String, json: String) = runBlocking {
        val space = SpaceUri.of(Did(bob), Nsid.SPACE_ATHLETE, tier)
        indexer.spaceWrite(space, collection, rkey, "bafy$rkey", AtJson.parseToJsonElement(json).jsonObject)
    }

    /** Bob's gear item [rkey] at [tier], with the photo [cid]. */
    private fun gearWithPhoto(rkey: String, tier: String, type: String, cid: String) =
        spacePut(
            tier,
            Nsid.GEAR_ITEM,
            rkey,
            """{"name":"$rkey","type":"$type","photo":{"${'$'}type":"${Nsid.FEED_ACTIVITY}#image","image":{"${'$'}type":"blob",""" +
                """"ref":{"${'$'}link":"$cid"},"mimeType":"image/jpeg","size":1}},"createdAt":"2026-10-01T09:00:00Z"}""",
        )

    /** The path of a photo URL signed for [viewer] to bob's [cid], as views build it. */
    private fun signedPath(cid: String, viewer: String) =
        "${MediaUrls.PATH}/$bob/$cid?" + mediaTokens.params(bob, cid, Did(viewer)).formUrlEncode()

    private fun io.ktor.server.testing.ApplicationTestBuilder.startModerated() =
        start(ozone = ozone, spaceBlobs = spaceBlobs, mediaTokens = mediaTokens)

    private suspend fun HttpClient.subject(uri: String, viewer: String = moderator) =
        raw(GetSubject.method.nsid, viewer, "?uri=${uri.encodeURLParameter()}")

    private suspend fun audit() = db.tx {
        ModerationAccess.selectAll().map {
            listOf(it[ModerationAccess.moderatorDid], it[ModerationAccess.subjectUri], it[ModerationAccess.reportRef])
        }
    }

    @Test
    fun aModeratorOpensAReportedFollowersActivityAndItsPhoto() = testApplication {
        startModerated()
        listOf(alice, bob, carol).forEach(::profile)
        postWithPhoto("3kf", Visibility.FOLLOWERS)
        reviews[activityUri(bob, "3kf")] = 7L to "reviewOpen"

        val response = client.subject(activityUri(bob, "3kf"))
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val output = AtJson.decodeFromString(GetSubject.method.output, response.bodyAsText())
        assertEquals(Visibility.FOLLOWERS, output.visibility)
        val photo = (output.activity!!.media!!.single() as ActivityMediaView.Image).fullsize
        assertTrue(photo.contains("viewer=${moderator.encodeURLParameter()}"), "signed for the moderator: $photo")
        assertEquals(HttpStatusCode.OK, client.get(photo.removePrefix(config.publicUrl)).status, "and it works")
        assertEquals(listOf(listOf(moderator, activityUri(bob, "3kf"), "7")), audit(), "logged without any of its content")

        // Past the URL's lifetime the link stops working, as viewers' do.
        mediaClock.now += 31.minutes
        assertEquals(HttpStatusCode.NotFound, client.get(photo.removePrefix(config.publicUrl)).status)
    }

    @Test
    fun aModeratorSeesTheGearPhotosOfTheSubjectTheyOpened() = testApplication {
        startModerated()
        listOf(bob, carol).forEach(::profile)
        gearWithPhoto("3bike", Visibility.FOLLOWERS, "bike", photoCid)
        gearWithPhoto("3shoes", AthleteSpace.SELF, "shoes", shoesCid)
        val bike = "at://$bob/${Nsid.GEAR_ITEM}/3bike"
        spacePut(
            Visibility.FOLLOWERS,
            Nsid.FEED_ACTIVITY,
            "3kg",
            """{"sport":"ride","startedAt":"2026-10-02T07:00:00Z","elapsedTime":1800,"gear":["$bike"],""" +
                """"createdAt":"2026-10-02T07:31:00Z"}""",
        )
        spacePut(AthleteSpace.INTERACTIONS, Nsid.GRAPH_BLOCK, moderator, """{"subject":"$moderator","createdAt":"2026-10-21T09:00:00Z"}""")
        reviews[activityUri(bob, "3kg")] = 7L to "reviewOpen"

        val output = AtJson.decodeFromString(GetSubject.method.output, client.subject(activityUri(bob, "3kg")).bodyAsText())
        val photo = output.activity!!.gear!!.single().photo!!
        assertTrue(photo.contains("viewer=${moderator.encodeURLParameter()}"), "signed for the moderator: $photo")
        assertEquals(HttpStatusCode.OK, client.get(photo.removePrefix(config.publicUrl)).status, "the bike's photo works")

        // The shoes are on no activity the moderator opened; the same link works for bob, so only the grant is missing.
        assertEquals(HttpStatusCode.NotFound, client.get(signedPath(shoesCid, moderator)).status, "the shoes' photo is refused")
        assertEquals(HttpStatusCode.OK, client.get(signedPath(shoesCid, bob)).status)

        mediaClock.now += 31.minutes
        assertEquals(HttpStatusCode.NotFound, client.get(photo.removePrefix(config.publicUrl)).status, "and the bike's expires")
    }

    @Test
    fun aBlockBetweenModeratorAndAthleteDoesntHideTheSubject() = testApplication {
        startModerated()
        listOf(bob, carol).forEach(::profile)
        postWithPhoto("3km", AthleteSpace.MUTUALS)
        runBlocking {
            val interactions = SpaceUri.of(Did(bob), Nsid.SPACE_ATHLETE, AthleteSpace.INTERACTIONS)
            val block = """{"subject":"$moderator","createdAt":"2026-10-21T09:00:00Z"}"""
            indexer.spaceWrite(interactions, Nsid.GRAPH_BLOCK, moderator, "bafyb", AtJson.parseToJsonElement(block).jsonObject)
        }
        // A report on bob's account covers his content too.
        reviews[bob] = 9L to "reviewEscalated"
        assertEquals(HttpStatusCode.OK, client.subject(activityUri(bob, "3km")).status)
    }

    @Test
    fun onlyModeratorsAndAdminsMayCall() = testApplication {
        startModerated()
        listOf(alice, bob).forEach(::profile)
        post(bob, "3kb")
        reviews[activityUri(bob, "3kb")] = 1L to "reviewOpen"
        val refused = client.subject(activityUri(bob, "3kb"), viewer = alice)
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertTrue(refused.bodyAsText().contains("NotModerator"))
        assertEquals(GetModerationRole.Output(null), client.query(GetModerationRole.method, alice, ""))
        assertEquals(GetModerationRole.Output("moderator"), client.query(GetModerationRole.method, moderator, ""))
    }

    @Test
    fun aSubjectNobodyReportedOrWhoseReportClosedIsNotFound() = testApplication {
        startModerated()
        profile(bob)
        post(bob, "3km", AthleteSpace.MUTUALS)
        assertEquals(notFound, client.subject(activityUri(bob, "3km")).status, "no report")
        reviews[activityUri(bob, "3km")] = 4L to "reviewClosed"
        assertEquals(notFound, client.subject(activityUri(bob, "3km")).status, "resolved and closed")
        assertEquals(emptyList(), audit())
    }

    @Test
    fun anUnreachableLabelerRefuses() = testApplication {
        startModerated()
        profile(bob)
        post(bob, "3kf", Visibility.FOLLOWERS)
        ozoneDown = true
        val response = client.subject(activityUri(bob, "3kf"))
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("LabelerUnavailable"))
    }

    @Test
    fun aPublicSubjectNeedsNoReportAndIsntLogged() = testApplication {
        startModerated()
        profile(bob)
        post(bob, "3kp")
        assertEquals(HttpStatusCode.OK, client.subject(activityUri(bob, "3kp")).status)
        assertEquals(emptyList(), audit())
        assertTrue(ozoneCalls.none { it.startsWith(OzoneClient.QUERY_STATUSES) }, "no review check for a Public subject")
    }

    @Test
    fun aPublicActivityWithOnlyMeDetailsNeedsAReport() = testApplication {
        startModerated()
        profile(bob)
        post(bob, "3kh")
        spacePut(
            AthleteSpace.SELF,
            Nsid.FEED_ACTIVITY_DETAILS,
            "3kh",
            """{"activity":"${activityUri(bob, "3kh")}","avgHeartRate":151,"createdAt":"2026-10-02T07:31:00Z"}""",
        )
        assertEquals(notFound, client.subject(activityUri(bob, "3kh")).status, "its Only-me heart rate needs a report")
        assertEquals(emptyList(), audit())

        reviews[activityUri(bob, "3kh")] = 5L to "reviewOpen"
        val output = AtJson.decodeFromString(GetSubject.method.output, client.subject(activityUri(bob, "3kh")).bodyAsText())
        assertEquals(Visibility.PUBLIC, output.visibility)
        assertTrue(output.activity!!.details.orEmpty().isNotEmpty(), "served once reported")
        assertEquals(listOf(listOf(moderator, activityUri(bob, "3kh"), "5")), audit())
    }

    @Test
    fun aReportedCommentOpensWithItsVisibility() = testApplication {
        startModerated()
        listOf(alice, bob).forEach(::profile)
        post(bob, "3kp")
        comment(alice, bob, "3kp")
        val output = AtJson.decodeFromString(GetSubject.method.output, client.subject(commentUri(alice, bob)).bodyAsText())
        assertEquals(Visibility.PUBLIC, output.visibility)
        assertEquals("Nice", output.comment?.text)
    }

    @Test
    fun theTeamIsReadOnceEveryFewMinutes() = testApplication {
        startModerated()
        profile(bob)
        post(bob, "3kp")
        repeat(3) { client.subject(activityUri(bob, "3kp")) }
        assertEquals(1, ozoneCalls.count { it == OzoneClient.LIST_MEMBERS })
    }
}
