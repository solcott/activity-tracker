package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.atproto.commentHideRkey
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivitiesByStart
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.app.GetLikes
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database

/** Combined activities (`feed/combined-activities`): which members count, and how feeds, views, likes and comments treat them. */
class CombinedActivitiesTest {
    private val service = "did:web:localhost%3A8081"
    private val alice = "did:plc:alice"
    private val bob = "did:plc:bob"
    private val carol = "did:plc:carol"
    private val dave = "did:plc:dave"
    private val keys = listOf(alice, bob, carol, dave).associateWith { TestAccountKey() }

    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val indexer = Indexer(db, { did -> did.value.substringAfterLast(':') + ".test" })
    private val config = AppViewConfig.fromEnv(emptyMap())
    private val verifier = ServiceAuthVerifier(service, keys = { keys[it.value]?.multibase })

    private fun commit(did: String, collection: String, rkey: String, record: String?) = runBlocking {
        indexer.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-10-14T12:00:00.000000Z",
                operation = if (record == null) "delete" else "create",
                collection = collection,
                rkey = rkey,
                record = record?.let { AtJson.parseToJsonElement(it).jsonObject },
                cid = "bafy$rkey",
            )
        )
    }

    private fun follow(author: String, subject: String) =
        commit(
            author,
            Nsid.GRAPH_FOLLOW,
            "3kf${author.last()}${subject.last()}",
            """{"subject":"$subject","createdAt":"2026-10-01T09:00:00Z"}""",
        )

    private fun uri(rkey: String, did: String = alice) = "at://$did/${Nsid.FEED_ACTIVITY}/$rkey"

    /** An activity record starting at [hour] on 2026-10-14, combining [members] (alice's rkeys or whole URIs) when given. */
    private fun record(hour: Int, members: List<String> = emptyList(), device: String? = null): String {
        val start = "2026-10-14T${hour.toString().padStart(2, '0')}:00:00Z"
        val combined =
            members
                .takeIf { it.isNotEmpty() }
                ?.joinToString(",", prefix = ""","combined":{"members":[""", postfix = """],"sources":[]}""") {
                    "\"${if (it.startsWith("at://")) it else uri(it)}\""
                }
                .orEmpty()
        val dev = device?.let { ""","device":"$it"""" }.orEmpty()
        return """{"sport":"ride","startedAt":"$start","elapsedTime":3600,"createdAt":"$start"$dev$combined}"""
    }

    /** [did] posts an activity at [tier]: in their repo when public, in their tier space otherwise. */
    private fun post(rkey: String, json: String, tier: String = Visibility.PUBLIC, did: String = alice) {
        if (tier == Visibility.PUBLIC) {
            commit(did, Nsid.FEED_ACTIVITY, rkey, json)
        } else {
            val space = SpaceUri.of(Did(did), Nsid.SPACE_ATHLETE, tier)
            runBlocking { indexer.spaceWrite(space, Nsid.FEED_ACTIVITY, rkey, "bafy$rkey", AtJson.parseToJsonElement(json).jsonObject) }
        }
    }

    private fun like(giver: String, rkey: String, likeRkey: String = "3kl${giver.last()}$rkey") =
        commit(
            giver,
            Nsid.FEED_LIKE,
            likeRkey,
            """{"subject":{"uri":"${uri(rkey)}","cid":"bafy$rkey"},"createdAt":"2026-10-14T15:00:00Z"}""",
        )

    /** [author] comments on alice's [rkey], in their repo with no [audience], else in their interactions space written for [audience]. */
    private fun comment(author: String, rkey: String, commentRkey: String, audience: String? = null) {
        val json =
            """{"subject":{"uri":"${uri(rkey)}","cid":"bafy$rkey"},"text":"Nice","createdAt":"2026-10-14T15:00:00Z"""" +
                audience?.let { ""","audience":"$it"""" }.orEmpty() +
                "}"
        if (audience == null) {
            commit(author, Nsid.FEED_COMMENT, commentRkey, json)
        } else {
            val interactions = SpaceUri.of(Did(author), Nsid.SPACE_ATHLETE, AthleteSpace.INTERACTIONS)
            runBlocking {
                indexer.spaceWrite(
                    interactions,
                    Nsid.FEED_COMMENT,
                    commentRkey,
                    "bafy$commentRkey",
                    AtJson.parseToJsonElement(json).jsonObject,
                )
            }
        }
    }

    private suspend fun <P, O> HttpClient.query(method: XrpcQuery<P, O>, viewer: String?, query: String): O {
        val response =
            get("/xrpc/${method.nsid}$query") {
                viewer?.let { header(HttpHeaders.Authorization, "Bearer ${keys.getValue(it).serviceJwt(it, service, method.nsid)}") }
            }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return AtJson.decodeFromString(method.output, response.bodyAsText())
    }

    private suspend fun HttpClient.timeline(viewer: String): List<String> =
        query(GetTimeline.method, viewer, "?limit=50").activities.map { it.uri.rkey!! }

    private suspend fun HttpClient.aliceActivities(viewer: String?): List<String> =
        query(GetAuthorActivities.method, viewer, "?actor=$alice").activities.map { it.uri.rkey!! }

    private suspend fun HttpClient.byStart(viewer: String?) =
        query(GetAuthorActivitiesByStart.method, viewer, "?actor=$alice&since=2026-10-14T00:00:00Z&until=2026-10-15T00:00:00Z").activities

    private suspend fun HttpClient.activity(rkey: String, viewer: String?) =
        query(GetActivity.method, viewer, "?uri=${uri(rkey).encodeURLParameter()}").activity

    private suspend fun HttpClient.members(rkey: String, viewer: String? = null): List<String>? =
        activity(rkey, viewer).members?.map { it.uri.rkey!! }

    private suspend fun HttpClient.count(viewer: String?): Long? = query(GetProfile.method, viewer, "?actor=$alice").activitiesCount

    private suspend fun HttpClient.likers(rkey: String, viewer: String?): List<String> =
        query(GetLikes.method, viewer, "?uri=${uri(rkey).encodeURLParameter()}").likes.map { it.did.value }

    /** The comments [viewer] is listed on alice's [rkey], after checking that the view's count matches them. */
    private suspend fun HttpClient.comments(rkey: String, viewer: String?): List<String> {
        val count = activity(rkey, viewer).commentCount
        val comments = query(GetComments.method, viewer, "?uri=${uri(rkey).encodeURLParameter()}&limit=100").comments
        assertEquals(count, comments.count { it.hidden != true }.toLong(), "the count matches the list")
        return comments.map { it.uri.rkey!! }
    }

    private fun ApplicationTestBuilder.start() {
        application { appView(config, db, verifier) }
    }

    @Test
    fun feedsAndCountShowTheCombinedActivityInPlaceOfItsMembers() = testApplication {
        start()
        follow(bob, alice)
        post("3kwatch", record(9, device = "Fenix 7"))
        post("3kedge", record(9, device = "Edge 840"))
        post("3kother", record(6))
        post("3kcomb", record(9, members = listOf("3kwatch", "3kedge")))

        assertEquals(setOf("3kcomb", "3kother"), client.timeline(bob).toSet())
        assertEquals(setOf("3kcomb", "3kother"), client.aliceActivities(null).toSet())
        assertEquals(2, client.count(bob))
        // Oldest first, then by URI.
        assertEquals(listOf("3kedge", "3kwatch"), client.members("3kcomb"))
        assertEquals("Edge 840", client.activity("3kcomb", bob).members?.first()?.device)
        assertNull(client.activity("3kother", bob).members)
        assertEquals(uri("3kcomb"), client.activity("3kwatch", bob).combinedInto?.value, "a member's page links to the combined activity")

        // Duplicate searches still find members, marked as such.
        val byStart = client.byStart(bob).associateBy { it.uri.rkey!! }
        assertEquals(setOf("3kwatch", "3kedge", "3kother", "3kcomb"), byStart.keys)
        assertEquals(uri("3kcomb"), byStart.getValue("3kedge").combinedInto?.value)
        assertNull(byStart.getValue("3kcomb").combinedInto)
    }

    @Test
    fun splittingOrDroppingAMemberShowsItAgain() = testApplication {
        start()
        post("3kwatch", record(9))
        post("3kedge", record(9))
        post("3kphone", record(10))
        post("3kcomb", record(9, members = listOf("3kwatch", "3kedge", "3kphone")))
        assertEquals(listOf("3kcomb"), client.aliceActivities(null))

        // The combined record rewritten without the phone.
        post("3kcomb", record(9, members = listOf("3kwatch", "3kedge")))
        assertEquals(setOf("3kcomb", "3kphone"), client.aliceActivities(null).toSet())
        assertEquals(listOf("3kedge", "3kwatch"), client.members("3kcomb"))

        // Split: the combined activity is deleted.
        commit(alice, Nsid.FEED_ACTIVITY, "3kcomb", null)
        assertEquals(setOf("3kwatch", "3kedge", "3kphone"), client.aliceActivities(null).toSet())
        assertNull(client.activity("3kwatch", null).combinedInto)
    }

    @Test
    fun aMemberIndexedAfterItsCombinedActivityCounts() = testApplication {
        start()
        post("3kcomb", record(9, members = listOf("3kwatch", "3kedge")))
        assertEquals(emptyList(), client.members("3kcomb").orEmpty())
        post("3kwatch", record(9))
        post("3kedge", record(9))
        assertEquals(listOf("3kcomb"), client.aliceActivities(null))
        assertEquals(listOf("3kedge", "3kwatch"), client.members("3kcomb"))
    }

    @Test
    fun claimsOnSomeoneElsesActivityAreIgnored() = testApplication {
        start()
        post("3kbob", record(9), did = bob)
        post("3kwatch", record(9))
        post("3kcomb", record(9, members = listOf("3kwatch", uri("3kbob", bob))))

        val bobs = client.query(GetAuthorActivities.method, null, "?actor=$bob").activities.map { it.uri.rkey }
        assertEquals(listOf("3kbob"), bobs, "bob's ride still shows in his feed")
        assertEquals(listOf("3kwatch"), client.members("3kcomb"))
    }

    @Test
    fun aMemberAtAnotherVisibilityStandsOnItsOwn() = testApplication {
        start()
        follow(bob, alice)
        post("3kwatch", record(9))
        post("3kfol", record(9), tier = Visibility.FOLLOWERS)
        post("3kcomb", record(9, members = listOf("3kwatch", "3kfol")))

        assertEquals(setOf("3kcomb", "3kfol"), client.timeline(bob).toSet())
        assertEquals(listOf("3kwatch"), client.members("3kcomb", bob))
        assertNull(client.activity("3kfol", bob).combinedInto)
    }

    @Test
    fun aMemberClaimedTwiceBelongsToTheEarlierCombinedActivity() = testApplication {
        start()
        post("3kwatch", record(9))
        post("3kedge", record(9))
        post("3kphone", record(9))
        // TIDs sort by time: 3kcomb1 was created first.
        post("3kcomb2", record(9, members = listOf("3kwatch", "3kphone")))
        post("3kcomb1", record(9, members = listOf("3kwatch", "3kedge")))

        assertEquals(listOf("3kedge", "3kwatch"), client.members("3kcomb1"))
        assertEquals(listOf("3kphone"), client.members("3kcomb2"))
        assertEquals(uri("3kcomb1"), client.activity("3kwatch", null).combinedInto?.value)
        assertEquals(setOf("3kcomb1", "3kcomb2"), client.aliceActivities(null).toSet())
    }

    @Test
    fun aCombinedActivityIsNeverAMember() = testApplication {
        start()
        post("3kwatch", record(9))
        post("3kedge", record(9))
        post("3kcomb", record(9, members = listOf("3kwatch", "3kedge")))
        post("3kouter", record(9, members = listOf("3kcomb", "3kwatch")))

        assertEquals(emptyList(), client.members("3kouter").orEmpty(), "3kcomb is combined, and 3kwatch is 3kcomb's")
        assertEquals(setOf("3kcomb", "3kouter"), client.aliceActivities(null).toSet())
    }

    @Test
    fun atMostTenMembersAreIndexed() = testApplication {
        start()
        val rkeys = (10..21).map { "3km$it" }
        rkeys.forEach { post(it, record(9)) }
        post("3kcomb", record(9, members = rkeys + "3kcomb"))

        assertEquals(10, client.members("3kcomb")?.size, "itself and anything past ten are dropped")
        assertEquals(setOf("3kcomb", "3km20", "3km21"), client.aliceActivities(null).toSet())
    }

    @Test
    fun likesOnMembersCountTowardTheCombinedActivity() = testApplication {
        start()
        post("3kwatch", record(9))
        post("3kedge", record(9))
        // Bob liked the watch ride before it was combined.
        like(bob, "3kwatch")
        post("3kcomb", record(9, members = listOf("3kwatch", "3kedge")))
        like(carol, "3kcomb")
        like(carol, "3kedge")

        assertEquals(2, client.activity("3kcomb", dave).likeCount, "bob once, carol once though she liked twice")
        assertEquals(setOf(bob, carol), client.likers("3kcomb", dave).toSet())
        assertEquals(listOf(bob), client.likers("3kwatch", dave), "a member's own page lists only its own likes")
    }

    @Test
    fun commentsOnMembersFollowTheSameRules() = testApplication {
        start()
        follow(bob, alice)
        follow(dave, alice)
        follow(alice, bob)
        post("3kwatch", record(9), tier = Visibility.FOLLOWERS)
        post("3kedge", record(9), tier = Visibility.FOLLOWERS)
        post("3kcomb", record(9, members = listOf("3kwatch", "3kedge")), tier = Visibility.FOLLOWERS)
        comment(dave, "3kwatch", "3kcd1", audience = Visibility.FOLLOWERS)
        // Written for alice's mutuals: bob is one, dave isn't.
        comment(bob, "3kedge", "3kcb1", audience = Visibility.MUTUALS)
        comment(dave, "3kcomb", "3kcd2", audience = Visibility.FOLLOWERS)

        assertEquals(setOf("3kcd1", "3kcb1", "3kcd2"), client.comments("3kcomb", bob).toSet())
        assertEquals(setOf("3kcd1", "3kcd2"), client.comments("3kcomb", dave).toSet())

        // Alice hides dave's comment on the watch ride: it's no longer counted on the combined ride.
        val hidden = AtUri("at://$dave/${Nsid.FEED_COMMENT}/3kcd1")
        val hideRkey = runBlocking { commentHideRkey(hidden) }
        commit(alice, Nsid.FEED_COMMENT_HIDE, hideRkey, """{"subject":"${hidden.value}","createdAt":"2026-10-14T16:00:00Z"}""")
        assertEquals(setOf("3kcb1", "3kcd2"), client.comments("3kcomb", bob).toSet())
    }
}
