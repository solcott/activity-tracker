package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.SpaceBlobs
import io.github.solcott.activitytracker.appview.api.StreamSource
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.atproto.xrpc.XrpcBytes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
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
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database

/** Non-public activities served by tier (`spaces/appview-syncer`, `feed/activity-visibility`), indexed as the syncer indexes them. */
class ActivityVisibilityTest {
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

    private fun record(day: Int, sport: String = "run", recordingOf: String? = null): String {
        val date = "2026-10-${day.toString().padStart(2, '0')}"
        return """{"sport":"$sport","startedAt":"${date}T07:00:00Z","elapsedTime":1800,""" +
            """"createdAt":"${date}T07:31:00Z"${recordingOf?.let { ""","recordingOf":"$it"""" }.orEmpty()}}"""
    }

    /** Alice posts an activity at [tier]: in her repo when public, in her tier space otherwise. */
    private fun post(rkey: String, tier: String, day: Int = 1, sport: String = "run", recordingOf: String? = null) {
        val json = record(day, sport, recordingOf)
        if (tier == Visibility.PUBLIC) {
            commit(alice, Nsid.FEED_ACTIVITY, rkey, json)
        } else {
            runBlocking {
                indexer.spaceWrite(space(tier), Nsid.FEED_ACTIVITY, rkey, "bafy$rkey", AtJson.parseToJsonElement(json).jsonObject)
            }
        }
    }

    private fun unpost(rkey: String, tier: String) {
        if (tier == Visibility.PUBLIC) commit(alice, Nsid.FEED_ACTIVITY, rkey, null)
        else runBlocking { indexer.spaceDelete(space(tier), Nsid.FEED_ACTIVITY, rkey) }
    }

    private fun space(tier: String) = SpaceUri.of(Did(alice), Nsid.SPACE_ATHLETE, tier)

    private fun unfollow(author: String, subject: String) = commit(author, Nsid.GRAPH_FOLLOW, "3kf${author.last()}${subject.last()}", null)

    /** Alice changes [rkey] from [from] to [to]: the new copy is written, then the old one deleted. */
    private fun move(rkey: String, from: String, to: String) {
        post(rkey, to)
        unpost(rkey, from)
    }

    /** [giver] likes alice's [rkey], in their repo or their interactions space; the like's rkey is `3kl` + the giver's initial. */
    private fun like(giver: String, rkey: String, inSpace: Boolean = false) {
        val likeRkey = "3kl${giver.last()}"
        val json = """{"subject":{"uri":"${uri(rkey)}","cid":"bafy$rkey"},"createdAt":"2026-10-01T09:00:00Z"}"""
        if (!inSpace) {
            commit(giver, Nsid.FEED_LIKE, likeRkey, json)
        } else {
            val interactions = SpaceUri.of(Did(giver), Nsid.SPACE_ATHLETE, AthleteSpace.INTERACTIONS)
            runBlocking {
                indexer.spaceWrite(interactions, Nsid.FEED_LIKE, likeRkey, "bafy$likeRkey", AtJson.parseToJsonElement(json).jsonObject)
            }
        }
    }

    private fun unlike(giver: String, inSpace: Boolean) {
        val likeRkey = "3kl${giver.last()}"
        if (!inSpace) {
            commit(giver, Nsid.FEED_LIKE, likeRkey, null)
        } else {
            val interactions = SpaceUri.of(Did(giver), Nsid.SPACE_ATHLETE, AthleteSpace.INTERACTIONS)
            runBlocking { indexer.spaceDelete(interactions, Nsid.FEED_LIKE, likeRkey) }
        }
    }

    /**
     * [author] comments on alice's [rkey]: in their repo with no [audience], else in their interactions space written for [audience]. The
     * comment's rkey is `3kc` + the author's initial.
     */
    private fun comment(author: String, rkey: String, audience: String? = null) {
        val commentRkey = "3kc${author.last()}"
        val json =
            """{"subject":{"uri":"${uri(rkey)}","cid":"bafy$rkey"},"text":"Nice","createdAt":"2026-10-01T09:00:00Z"""" +
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

    private fun uncomment(author: String, inSpace: Boolean) {
        val commentRkey = "3kc${author.last()}"
        if (!inSpace) {
            commit(author, Nsid.FEED_COMMENT, commentRkey, null)
        } else {
            val interactions = SpaceUri.of(Did(author), Nsid.SPACE_ATHLETE, AthleteSpace.INTERACTIONS)
            runBlocking { indexer.spaceDelete(interactions, Nsid.FEED_COMMENT, commentRkey) }
        }
    }

    private fun uri(rkey: String) = "at://$alice/${Nsid.FEED_ACTIVITY}/$rkey"

    private suspend fun HttpClient.raw(nsid: String, viewer: String?, query: String): HttpResponse =
        get("/xrpc/$nsid$query") {
            viewer?.let { header(HttpHeaders.Authorization, "Bearer ${keys.getValue(it).serviceJwt(it, service, nsid)}") }
        }

    private suspend fun <P, O> HttpClient.query(method: XrpcQuery<P, O>, viewer: String?, query: String): O {
        val response = raw(method.nsid, viewer, query)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return AtJson.decodeFromString(method.output, response.bodyAsText())
    }

    private suspend fun HttpClient.timeline(viewer: String, query: String = ""): List<String> =
        query(GetTimeline.method, viewer, "?limit=50$query").activities.map { it.uri.rkey!! }

    private suspend fun HttpClient.aliceActivities(viewer: String?): List<String> =
        query(GetAuthorActivities.method, viewer, "?actor=$alice").activities.map { it.uri.rkey!! }

    private suspend fun HttpClient.aliceByStart(viewer: String?): List<String> =
        query(GetAuthorActivitiesByStart.method, viewer, "?actor=$alice&since=2026-10-01T00:00:00Z&until=2026-11-01T00:00:00Z")
            .activities
            .map { it.uri.rkey!! }

    private suspend fun HttpClient.activityStatus(rkey: String, viewer: String?): HttpStatusCode =
        raw(GetActivity.method.nsid, viewer, "?uri=${uri(rkey).encodeURLParameter()}").status

    private suspend fun HttpClient.activity(rkey: String, viewer: String?) =
        query(GetActivity.method, viewer, "?uri=${uri(rkey).encodeURLParameter()}")

    private fun likesQuery(rkey: String, limit: Int = 50, cursor: String? = null) =
        "?uri=${uri(rkey).encodeURLParameter()}&limit=$limit" + cursor?.let { "&cursor=${it.encodeURLParameter()}" }.orEmpty()

    /** Every athlete `getLikes` lists for alice's [rkey], following cursors in pages of [limit]. */
    private suspend fun HttpClient.likers(rkey: String, viewer: String?, limit: Int = 50): List<String> {
        val all = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = query(GetLikes.method, viewer, likesQuery(rkey, limit, cursor))
            all += page.likes.map { it.did.value }
            cursor = page.cursor?.takeIf { page.likes.isNotEmpty() }
        } while (cursor != null)
        return all
    }

    /** The authors of the comments [viewer] is shown on alice's [rkey], after checking that the view's count matches them. */
    private suspend fun HttpClient.commenters(rkey: String, viewer: String?): List<String> {
        val count = activity(rkey, viewer).activity.commentCount
        val comments = query(GetComments.method, viewer, "?uri=${uri(rkey).encodeURLParameter()}&limit=100").comments
        assertEquals(count, comments.count { it.hidden != true }.toLong(), "the count matches the list")
        return comments.map { it.author.did.value }
    }

    private suspend fun HttpClient.count(viewer: String?): Long? = query(GetProfile.method, viewer, "?actor=$alice").activitiesCount

    private fun ApplicationTestBuilder.start() {
        application { appView(config, db, verifier) }
    }

    @Test
    fun effectiveFollowerSeesFollowersActivities() = testApplication {
        start()
        follow(bob, alice)
        post("3kpub", Visibility.PUBLIC, day = 1)
        post("3kfol", Visibility.FOLLOWERS, day = 2)
        post("3kmut", Visibility.MUTUALS, day = 3)
        post("3kme", Visibility.SELF, day = 4)

        assertEquals(listOf("3kfol", "3kpub"), client.timeline(bob))
        assertEquals(listOf("3kfol", "3kpub"), client.aliceActivities(bob))
        assertEquals(listOf("3kpub", "3kfol"), client.aliceByStart(bob))
        assertEquals(HttpStatusCode.OK, client.activityStatus("3kfol", bob))
        assertEquals(HttpStatusCode.BadRequest, client.activityStatus("3kmut", bob), "bob isn't a mutual")
        val view = client.query(GetActivity.method, bob, "?uri=${uri("3kfol").encodeURLParameter()}").activity
        assertEquals(Visibility.FOLLOWERS, view.visibility)
    }

    @Test
    fun mutualsSeeMutualsActivities() = testApplication {
        start()
        follow(bob, alice)
        follow(alice, bob)
        follow(carol, alice)
        post("3kmut", Visibility.MUTUALS)
        assertEquals(listOf("3kmut"), client.timeline(bob))
        assertEquals(emptyList(), client.timeline(carol), "carol follows alice one way")
        assertEquals(HttpStatusCode.BadRequest, client.activityStatus("3kmut", carol))
    }

    @Test
    fun pendingFollowerSeesNoNonPublicActivity() = testApplication {
        start()
        commit(alice, Nsid.ACTOR_PROFILE, "self", """{"displayName":"Alice","followPolicy":"approve"}""")
        follow(bob, alice)
        follow(alice, bob)
        post("3kpub", Visibility.PUBLIC, day = 1)
        post("3kfol", Visibility.FOLLOWERS, day = 2)
        post("3kmut", Visibility.MUTUALS, day = 3)

        assertEquals(listOf("3kpub"), client.aliceActivities(bob))
        assertEquals(HttpStatusCode.BadRequest, client.activityStatus("3kfol", bob))
        assertEquals(HttpStatusCode.BadRequest, client.activityStatus("3kmut", bob))
        commit(alice, Nsid.GRAPH_FOLLOW_APPROVAL, bob, """{"subject":"$bob","createdAt":"2026-10-02T09:00:00Z"}""")
        assertEquals(listOf("3kmut", "3kfol", "3kpub"), client.aliceActivities(bob), "approved: follower, and mutual")
    }

    @Test
    fun onlyMeIsOnlyForTheAthlete() = testApplication {
        start()
        follow(bob, alice)
        follow(alice, bob)
        post("3kme", Visibility.SELF)
        assertEquals(listOf("3kme"), client.aliceActivities(alice))
        assertEquals(listOf("3kme"), client.timeline(alice))
        assertEquals(HttpStatusCode.OK, client.activityStatus("3kme", alice))
        assertEquals(emptyList(), client.aliceActivities(bob))
        assertEquals(HttpStatusCode.BadRequest, client.activityStatus("3kme", bob))
        assertEquals(HttpStatusCode.BadRequest, client.activityStatus("3kme", null))
    }

    @Test
    fun activityCountDependsOnTheViewer() = testApplication {
        start()
        follow(bob, alice)
        (1..3).forEach { post("3kpub$it", Visibility.PUBLIC, day = it) }
        (1..2).forEach { post("3kfol$it", Visibility.FOLLOWERS, day = it) }
        post("3kme", Visibility.SELF)
        assertEquals(5, client.count(bob))
        assertEquals(3, client.count(carol))
        assertEquals(6, client.count(alice))
        assertEquals(3, client.count(null))
    }

    @Test
    fun unauthenticatedViewerSeesPublicOnly() = testApplication {
        start()
        post("3kpub", Visibility.PUBLIC, day = 1)
        post("3kfol", Visibility.FOLLOWERS, day = 2)
        assertEquals(listOf("3kpub"), client.aliceActivities(null))
        assertEquals(listOf("3kpub"), client.aliceByStart(null))
    }

    @Test
    fun filtersAndPagingStillHold() = testApplication {
        start()
        follow(bob, alice)
        post("3k1", Visibility.PUBLIC, day = 1)
        post("3k2", Visibility.FOLLOWERS, day = 2)
        post("3k3", Visibility.MUTUALS, day = 3)
        post("3k4", Visibility.FOLLOWERS, day = 4, sport = "ride")
        post("3k5", Visibility.SELF, day = 5)
        post("3k6", Visibility.PUBLIC, day = 6)

        val paged = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page =
                client.query(GetTimeline.method, bob, "?limit=1&type=run" + cursor?.let { "&cursor=${it.encodeURLParameter()}" }.orEmpty())
            paged += page.activities.map { it.uri.rkey!! }
            cursor = page.cursor
        } while (cursor != null)
        assertEquals(listOf("3k6", "3k2", "3k1"), paged)
    }

    @Test
    fun recordingsCountOnlyAtTheirPrimarysVisibility() = testApplication {
        start()
        follow(bob, alice)
        post("3kme", Visibility.SELF, day = 1)
        post("3kpub", Visibility.PUBLIC, day = 1, recordingOf = uri("3kme"))
        // A public recording of an Only-me activity stands on its own, and says nothing about the other.
        assertEquals(listOf("3kpub"), client.aliceActivities(bob))
        val mine = client.query(GetAuthorActivities.method, alice, "?actor=$alice").activities
        assertEquals(setOf("3kme", "3kpub"), mine.map { it.uri.rkey }.toSet(), "the athlete sees both, unlinked")
        assertEquals(null, mine.single { it.uri.rkey == "3kme" }.recordings)

        post("3kfol", Visibility.FOLLOWERS, day = 2)
        post("3kfol2", Visibility.FOLLOWERS, day = 2, recordingOf = uri("3kfol"))
        val feed = client.query(GetAuthorActivities.method, bob, "?actor=$alice").activities
        assertEquals(listOf("3kfol", "3kpub"), feed.map { it.uri.rkey })
        assertEquals(listOf("3kfol2"), feed.first().recordings?.map { it.uri.rkey })
    }

    @Test
    fun commentsOnANonPublicActivityAreServedToItsAudience() = testApplication {
        start()
        follow(bob, alice)
        follow(dave, alice)
        post("3kact", Visibility.FOLLOWERS)
        comment(bob, "3kact", audience = Visibility.FOLLOWERS)

        assertEquals(listOf(bob), client.commenters("3kact", bob))
        assertEquals(listOf(bob), client.commenters("3kact", dave))
        assertEquals(listOf(bob), client.commenters("3kact", alice), "alice sees comments on her own activity")
        assertEquals(HttpStatusCode.BadRequest, client.activityStatus("3kact", carol), "carol can't see it, nor its comments")

        // A delete of the rkey from bob's repo, where it isn't, leaves the space's comment.
        uncomment(bob, inSpace = false)
        assertEquals(listOf(bob), client.commenters("3kact", dave))
        uncomment(bob, inSpace = true)
        assertEquals(emptyList(), client.commenters("3kact", dave))
    }

    @Test
    fun commentsStayWithinTheirAudienceWhenWidened() = testApplication {
        start()
        follow(bob, alice)
        follow(dave, alice)
        post("3kact", Visibility.FOLLOWERS)
        comment(bob, "3kact", audience = Visibility.FOLLOWERS)

        move("3kact", Visibility.FOLLOWERS, Visibility.PUBLIC)
        assertEquals(Visibility.PUBLIC, client.activity("3kact", carol).activity.visibility)
        assertEquals(emptyList(), client.commenters("3kact", carol), "carol isn't in the audience bob wrote for")
        assertEquals(emptyList(), client.commenters("3kact", null), "nor is anyone signed out")
        assertEquals(listOf(bob), client.commenters("3kact", dave))
        assertEquals(listOf(bob), client.commenters("3kact", alice))
    }

    @Test
    fun commentsFollowTheActivityWhenNarrowed() = testApplication {
        start()
        follow(bob, alice)
        follow(dave, alice)
        post("3kact", Visibility.PUBLIC)
        comment(bob, "3kact")
        comment(carol, "3kact")
        assertEquals(setOf(bob, carol), client.commenters("3kact", null).toSet())

        move("3kact", Visibility.PUBLIC, Visibility.FOLLOWERS)
        assertEquals(listOf(bob), client.commenters("3kact", dave), "carol can't see the activity now, so neither can anyone her comment")
        assertEquals(listOf(bob), client.commenters("3kact", alice))

        // Made public again: both were written for Public, so both come back.
        move("3kact", Visibility.FOLLOWERS, Visibility.PUBLIC)
        assertEquals(setOf(bob, carol), client.commenters("3kact", null).toSet())
    }

    @Test
    fun aCommenterWhoUnfollowsIsNoLongerShown() = testApplication {
        start()
        follow(bob, alice)
        follow(dave, alice)
        post("3kact", Visibility.FOLLOWERS)
        comment(bob, "3kact", audience = Visibility.FOLLOWERS)
        assertEquals(listOf(bob), client.commenters("3kact", dave))

        unfollow(bob, alice)
        assertEquals(emptyList(), client.commenters("3kact", dave))
        assertEquals(emptyList(), client.commenters("3kact", alice))

        follow(bob, alice)
        assertEquals(listOf(bob), client.commenters("3kact", dave))
    }

    @Test
    fun aCommentIsNeverShownWiderThanItsAudience() = testApplication {
        start()
        // dave is alice's mutual; bob only follows her.
        follow(dave, alice)
        follow(alice, dave)
        follow(bob, alice)
        post("3kact", Visibility.FOLLOWERS)
        comment(dave, "3kact", audience = Visibility.MUTUALS)
        comment(carol, "3kact", audience = "friends")
        follow(carol, alice)

        assertEquals(emptyList(), client.commenters("3kact", bob), "bob isn't alice's mutual")
        assertEquals(listOf(dave), client.commenters("3kact", dave))
        assertEquals(setOf(dave, carol), client.commenters("3kact", alice).toSet(), "an unknown audience is alice's only")
        assertEquals(emptyList(), client.commenters("3kact", carol), "carol isn't a mutual, and not even she sees her unknown audience")
    }

    @Test
    fun aDeletedCommentIsGoneForEveryViewerWhicheverDeleteArrivesFirst() = testApplication {
        start()
        follow(bob, alice)
        follow(dave, alice)
        post("3kpub", Visibility.PUBLIC)
        post("3kact", Visibility.FOLLOWERS, day = 2)
        // The client deletes a comment from both places; only the delete from where it's stored counts.
        comment(bob, "3kpub")
        comment(dave, "3kact", audience = Visibility.FOLLOWERS)

        uncomment(bob, inSpace = true)
        assertEquals(listOf(bob), client.commenters("3kpub", null), "a space delete leaves bob's repo comment")
        uncomment(bob, inSpace = false)
        for (viewer in listOf(null, carol, alice)) assertEquals(emptyList(), client.commenters("3kpub", viewer))

        uncomment(dave, inSpace = true)
        uncomment(dave, inSpace = false)
        assertEquals(emptyList(), client.commenters("3kact", alice))
        assertEquals(emptyList(), client.commenters("3kact", bob))
        assertEquals(setOf("3kpub", "3kact"), client.aliceActivities(alice).toSet(), "the activities are untouched")
    }

    @Test
    fun anAthleteCommentsOnTheirOwnOnlyMeActivity() = testApplication {
        start()
        follow(bob, alice)
        follow(alice, bob)
        post("3kme", Visibility.SELF)
        comment(alice, "3kme", audience = Visibility.SELF)
        assertEquals(listOf(alice), client.commenters("3kme", alice))
        assertEquals(HttpStatusCode.BadRequest, client.activityStatus("3kme", bob))
    }

    @Test
    fun likesOnANonPublicActivityAreServedToItsAudience() = testApplication {
        start()
        follow(bob, alice)
        follow(dave, alice)
        post("3kact", Visibility.FOLLOWERS)
        like(bob, "3kact", inSpace = true)

        val bobs = client.activity("3kact", bob).activity
        assertEquals(1, bobs.likeCount)
        assertEquals("at://$bob/${Nsid.FEED_LIKE}/3klb", bobs.viewer?.like?.value, "liked, for bob")
        assertEquals(1, client.activity("3kact", dave).activity.likeCount)
        assertEquals(null, client.activity("3kact", dave).activity.viewer?.like)
        assertEquals(1, client.activity("3kact", alice).activity.likeCount, "alice sees likes on her own activity")
        assertEquals(listOf(bob), client.likers("3kact", dave))

        // Unliked: bob's client deletes the rkey from his repo and his interactions space; only the space held it.
        unlike(bob, inSpace = false)
        assertEquals(1, client.activity("3kact", dave).activity.likeCount, "a delete from the repo leaves the space's like")
        unlike(bob, inSpace = true)
        assertEquals(0, client.activity("3kact", dave).activity.likeCount)
        assertEquals(null, client.activity("3kact", bob).activity.viewer?.like)
    }

    @Test
    fun likesFollowTheActivityWhenNarrowed() = testApplication {
        start()
        follow(bob, alice)
        follow(dave, alice)
        post("3kact", Visibility.PUBLIC)
        like(bob, "3kact")
        like(carol, "3kact")
        like(dave, "3kact")
        assertEquals(3, client.activity("3kact", bob).activity.likeCount)

        move("3kact", Visibility.PUBLIC, Visibility.FOLLOWERS)
        assertEquals(2, client.activity("3kact", bob).activity.likeCount, "carol doesn't follow alice")
        assertEquals(setOf(bob, dave), client.likers("3kact", bob).toSet())
        // Paging skips carol and still reaches every admitted giver.
        assertEquals(setOf(bob, dave), client.likers("3kact", bob, limit = 1).toSet())
        assertEquals(HttpStatusCode.BadRequest, client.activityStatus("3kact", carol), "carol can't see it")
        assertEquals(HttpStatusCode.BadRequest, client.raw(GetLikes.method.nsid, carol, likesQuery("3kact")).status)
    }

    @Test
    fun likesFollowTheActivityWhenWidened() = testApplication {
        start()
        follow(bob, alice)
        post("3kact", Visibility.FOLLOWERS)
        like(bob, "3kact", inSpace = true)
        assertEquals(HttpStatusCode.BadRequest, client.activityStatus("3kact", null))

        move("3kact", Visibility.FOLLOWERS, Visibility.PUBLIC)
        assertEquals(1, client.activity("3kact", null).activity.likeCount, "signed out")
        assertEquals(1, client.activity("3kact", carol).activity.likeCount)
        assertEquals(listOf(bob), client.likers("3kact", null))
        assertEquals(listOf(bob), client.likers("3kact", carol))
    }

    @Test
    fun aGiverWhoUnfollowsIsNoLongerCounted() = testApplication {
        start()
        follow(bob, alice)
        follow(dave, alice)
        post("3kact", Visibility.FOLLOWERS)
        like(bob, "3kact", inSpace = true)
        assertEquals(1, client.activity("3kact", dave).activity.likeCount)

        unfollow(bob, alice)
        assertEquals(0, client.activity("3kact", dave).activity.likeCount)
        assertEquals(0, client.activity("3kact", alice).activity.likeCount)
        assertEquals(emptyList(), client.likers("3kact", dave))

        follow(bob, alice)
        assertEquals(1, client.activity("3kact", dave).activity.likeCount)
        assertEquals(listOf(bob), client.likers("3kact", dave))
    }

    @Test
    fun aLikeFromSomeoneWhoCantSeeTheActivityIsNeverCounted() = testApplication {
        start()
        // dave is alice's mutual; bob follows alice, but alice doesn't follow him back.
        follow(dave, alice)
        follow(alice, dave)
        follow(bob, alice)
        post("3kact", Visibility.MUTUALS)
        like(bob, "3kact", inSpace = true)
        like(dave, "3kact", inSpace = true)
        assertEquals(1, client.activity("3kact", dave).activity.likeCount)
        assertEquals(listOf(dave), client.likers("3kact", alice))
    }

    @Test
    fun aLikeIndexedBeforeItsGiversFollowCountsOnceTheFollowIs() = testApplication {
        start()
        follow(dave, alice)
        post("3kact", Visibility.FOLLOWERS)
        like(bob, "3kact", inSpace = true)
        assertEquals(0, client.activity("3kact", dave).activity.likeCount)
        follow(bob, alice)
        assertEquals(1, client.activity("3kact", dave).activity.likeCount)
    }

    @Test
    fun getLikesOnAnActivityTheViewerCantSeeIsNotFound() = testApplication {
        start()
        follow(bob, alice)
        post("3kme", Visibility.SELF)
        assertEquals(HttpStatusCode.BadRequest, client.raw(GetLikes.method.nsid, bob, likesQuery("3kme")).status)
        post("3kact", Visibility.FOLLOWERS)
        like(bob, "3kact", inSpace = true)
        assertEquals(HttpStatusCode.BadRequest, client.raw(GetLikes.method.nsid, carol, likesQuery("3kact")).status)
        assertEquals(HttpStatusCode.BadRequest, client.raw(GetLikes.method.nsid, null, likesQuery("3kact")).status)
    }

    @Test
    fun unknownTierIsOnlyForTheAthlete() = testApplication {
        start()
        follow(bob, alice)
        follow(alice, bob)
        runBlocking {
            indexer.spaceWrite(
                SpaceUri.of(Did(alice), Nsid.SPACE_ATHLETE, "friends"),
                Nsid.FEED_ACTIVITY,
                "3kodd",
                "bafy3kodd",
                AtJson.parseToJsonElement(record(1)).jsonObject,
            )
        }
        assertEquals(emptyList(), client.aliceActivities(bob))
        assertEquals(listOf("3kodd"), client.aliceActivities(alice))
        assertEquals(AthleteSpace.SELF, Visibility.known("friends"))
    }

    private val photoCid = "bafkreihwzzyxpay7qndclffr6usq5te3of4fra7yucklue2b5ckxhq7aeq"
    private val photoBytes = byteArrayOf(1, 2, 3, 4)
    private val fetchedFrom = mutableListOf<Pair<SpaceUri, String>>()
    private val spaceBlobs = SpaceBlobs { space, repo, cid ->
        fetchedFrom += space to cid
        if (repo.value == alice && cid == photoCid) XrpcBytes(photoBytes, "image/jpeg") else null
    }

    private fun withPhoto(day: Int = 1) =
        record(day).removeSuffix("}") +
            ""","media":[{"${'$'}type":"${Nsid.FEED_ACTIVITY}#image","image":{"${'$'}type":"blob","ref":{"${'$'}link":"$photoCid"},""" +
            """"mimeType":"image/jpeg","size":4}}]}"""

    private fun ApplicationTestBuilder.startSigning(streams: StreamSource = StreamSource { _, _, _ -> null }) {
        application { appView(config.copy(signingKey = TEST_SIGNING_KEY), db, verifier, streamSource = streams, spaceBlobs = spaceBlobs) }
    }

    private fun postSpace(rkey: String, tier: String, json: String) = runBlocking {
        indexer.spaceWrite(space(tier), Nsid.FEED_ACTIVITY, rkey, "bafy$rkey", AtJson.parseToJsonElement(json).jsonObject)
    }

    @Test
    fun followerViewsAPhotoOfAFollowersActivity() = testApplication {
        startSigning()
        follow(bob, alice)
        postSpace("3kpic", Visibility.FOLLOWERS, withPhoto())
        val url = client.query(GetActivity.method, bob, "?uri=${uri("3kpic").encodeURLParameter()}").activity.media!!.single()
        val fullsize = (url as ActivityMediaView.Image).fullsize
        assertTrue(fullsize.contains("viewer=${bob.encodeURLParameter()}"), fullsize)
        val response = client.get(fullsize.removePrefix(config.publicUrl))
        assertEquals(HttpStatusCode.OK, response.status)
        assertContentEquals(photoBytes, response.readRawBytes())
        assertEquals("private, max-age=3600", response.headers[HttpHeaders.CacheControl])
        assertEquals(listOf(space(Visibility.FOLLOWERS) to photoCid), fetchedFrom)
    }

    @Test
    fun photoUrlsAreServedOnlyToViewersWhoMaySeeTheActivity() = testApplication {
        startSigning()
        follow(bob, alice)
        postSpace("3kpic", Visibility.FOLLOWERS, withPhoto())
        val bobs =
            (client.query(GetActivity.method, bob, "?uri=${uri("3kpic").encodeURLParameter()}").activity.media!!.single()
                    as ActivityMediaView.Image)
                .fullsize
                .removePrefix(config.publicUrl)
        val bare = "/img/media/$alice/$photoCid"
        assertEquals(HttpStatusCode.NotFound, client.get(bare).status, "unsigned: no redirect to the PDS either")
        assertEquals(HttpStatusCode.NotFound, client.get(bobs.replace(bob.encodeURLParameter(), carol.encodeURLParameter())).status)
        assertEquals(HttpStatusCode.NotFound, client.get(bobs.dropLast(2) + "xx").status, "bad signature")
        // Bob unfollows: his URL stops working at once, signature or not.
        commit(bob, Nsid.GRAPH_FOLLOW, "3kf${bob.last()}${alice.last()}", null)
        assertEquals(HttpStatusCode.NotFound, client.get(bobs).status)
        assertEquals(emptyList(), fetchedFrom, "nothing fetched for a refused request")
    }

    @Test
    fun publicPhotosStillRedirect() = testApplication {
        startSigning()
        commit(alice, Nsid.FEED_ACTIVITY, "3kpub", withPhoto())
        val media = client.query(GetActivity.method, null, "?uri=${uri("3kpub").encodeURLParameter()}").activity.media!!.single()
        val fullsize = (media as ActivityMediaView.Image).fullsize
        assertEquals("${config.publicUrl}/img/media/$alice/$photoCid", fullsize)
    }

    @Test
    fun spaceHeldStreamsComeFromTheSpaceByTier() = testApplication {
        val origins = mutableListOf<String>()
        startSigning(
            StreamSource { _, _, origin ->
                origins += origin
                """{"time":[0,1],"heartRate":[120,121]}""".encodeToByteArray()
            }
        )
        follow(bob, alice)
        postSpace("3kfol", Visibility.FOLLOWERS, record(1))
        runBlocking {
            indexer.spaceWrite(
                space(Visibility.FOLLOWERS),
                Nsid.FEED_ACTIVITY_STREAMS,
                "3kfol",
                "bafystreams",
                AtJson.parseToJsonElement(
                        """{"activity":"${uri("3kfol")}","visibility":"public","channels":["heartRate"],""" +
                            """"createdAt":"2026-10-01T07:31:00Z","data":{"${'$'}type":"blob","ref":{"${'$'}link":"$photoCid"},""" +
                            """"mimeType":"application/json","size":40}}"""
                    )
                    .jsonObject,
            )
        }
        val streamsUri = "?uri=${uri("3kfol").encodeURLParameter()}"
        val bobs = client.query(GetActivityStreams.method, bob, streamsUri)
        assertEquals(Visibility.FOLLOWERS, bobs.visibility, "capped at the tier")
        assertEquals(listOf(120, 121), bobs.data.heartRate)
        assertEquals(listOf(space(Visibility.FOLLOWERS).value), origins)
        assertEquals(HttpStatusCode.NotFound, client.raw(GetActivityStreams.method.nsid, carol, streamsUri).status)
    }

    @Test
    fun publicStreamsOfAnOnlyMeActivityStayHidden() = testApplication {
        startSigning(StreamSource { _, _, _ -> """{"time":[0]}""".encodeToByteArray() })
        postSpace("3kme", Visibility.SELF, record(1))
        commit(
            alice,
            Nsid.FEED_ACTIVITY_STREAMS,
            "3kme",
            """{"activity":"${uri("3kme")}","visibility":"public","channels":[],"createdAt":"2026-10-01T07:31:00Z",""" +
                """"data":{"${'$'}type":"blob","ref":{"${'$'}link":"$photoCid"},"mimeType":"application/json","size":12}}""",
        )
        val streamsUri = "?uri=${uri("3kme").encodeURLParameter()}"
        assertEquals(HttpStatusCode.NotFound, client.raw(GetActivityStreams.method.nsid, carol, streamsUri).status)
        assertEquals(HttpStatusCode.OK, client.raw(GetActivityStreams.method.nsid, alice, streamsUri).status)
    }
}
