package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.Blocks
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.Mutes
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.BlockSource
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.GetBlocks
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.app.GetFollowRequests
import io.github.solcott.activitytracker.lexicon.app.GetFollowers
import io.github.solcott.activitytracker.lexicon.app.GetLikes
import io.github.solcott.activitytracker.lexicon.app.GetMutes
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.SearchActors
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
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
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Blocks and mutes (`graph/blocks`, `graph/mutes`), indexed as Jetstream and the syncer index them, and read back through the API. */
class BlocksTest {
    private val service = "did:web:localhost%3A8081"
    private val alice = "did:plc:alice"
    private val bob = "did:plc:bob"
    private val carol = "did:plc:carol"
    private val keys = listOf(alice, bob, carol).associateWith { TestAccountKey() }

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
                time = "2026-10-21T12:00:00.000000Z",
                operation = if (record == null) "delete" else "create",
                collection = collection,
                rkey = rkey,
                record = record?.let { AtJson.parseToJsonElement(it).jsonObject },
                cid = "bafy$rkey",
            )
        )
    }

    private fun interactions(did: String) = SpaceUri.of(Did(did), Nsid.SPACE_ATHLETE, AthleteSpace.INTERACTIONS)

    private fun spaceWrite(space: SpaceUri, collection: String, rkey: String, json: String) = runBlocking {
        indexer.spaceWrite(space, collection, rkey, "bafy$rkey", AtJson.parseToJsonElement(json).jsonObject)
    }

    private fun profile(did: String) = commit(did, Nsid.ACTOR_PROFILE, "self", """{"displayName":"${did.last()}"}""")

    private fun follow(author: String, subject: String) =
        commit(
            author,
            Nsid.GRAPH_FOLLOW,
            "3kf${author.last()}${subject.last()}",
            """{"subject":"$subject","createdAt":"2026-10-01T09:00:00Z"}""",
        )

    /** [author] blocks [subject] in their interactions space, at rkey [rkey] (the subject, unless a test says otherwise). */
    private fun block(author: String, subject: String, rkey: String = subject, at: String = "2026-10-21T09:00:00Z") =
        spaceWrite(interactions(author), Nsid.GRAPH_BLOCK, rkey, """{"subject":"$subject","createdAt":"$at"}""")

    private fun unblock(author: String, subject: String) = runBlocking {
        indexer.spaceDelete(interactions(author), Nsid.GRAPH_BLOCK, subject)
    }

    private fun bskyBlock(author: String, subject: String, rkey: String = "3kbs${subject.last()}", at: String = "2025-05-01T09:00:00Z") =
        commit(author, Nsid.BSKY_GRAPH_BLOCK, rkey, """{"subject":"$subject","createdAt":"$at"}""")

    private fun mute(author: String, subject: String) =
        spaceWrite(interactions(author), Nsid.GRAPH_MUTE, subject, """{"subject":"$subject","createdAt":"2026-10-21T09:00:00Z"}""")

    private fun unmute(author: String, subject: String) = runBlocking {
        indexer.spaceDelete(interactions(author), Nsid.GRAPH_MUTE, subject)
    }

    private fun activityUri(author: String, rkey: String) = "at://$author/${Nsid.FEED_ACTIVITY}/$rkey"

    private fun post(author: String, rkey: String, tier: String = Visibility.PUBLIC) {
        val json = """{"sport":"run","startedAt":"2026-10-02T07:00:00Z","elapsedTime":1800,"createdAt":"2026-10-02T07:31:00Z"}"""
        if (tier == Visibility.PUBLIC) commit(author, Nsid.FEED_ACTIVITY, rkey, json)
        else spaceWrite(SpaceUri.of(Did(author), Nsid.SPACE_ATHLETE, tier), Nsid.FEED_ACTIVITY, rkey, json)
    }

    /** [author] comments, publicly, on [owner]'s [rkey]; the comment's rkey is `3kc` + both initials. */
    private fun comment(author: String, owner: String, rkey: String) =
        commit(
            author,
            Nsid.FEED_COMMENT,
            "3kc${author.last()}${owner.last()}",
            """{"subject":{"uri":"${activityUri(owner, rkey)}","cid":"bafy$rkey"},"text":"Nice","createdAt":"2026-10-03T09:00:00Z"}""",
        )

    private fun like(author: String, owner: String, rkey: String) =
        commit(
            author,
            Nsid.FEED_LIKE,
            "3kl${author.last()}${owner.last()}",
            """{"subject":{"uri":"${activityUri(owner, rkey)}","cid":"bafy$rkey"},"createdAt":"2026-10-03T09:00:00Z"}""",
        )

    private suspend fun HttpClient.raw(nsid: String, viewer: String?, query: String): HttpResponse =
        get("/xrpc/$nsid$query") {
            viewer?.let { header(HttpHeaders.Authorization, "Bearer ${keys.getValue(it).serviceJwt(it, service, nsid)}") }
        }

    private suspend fun <P, O> HttpClient.query(method: XrpcQuery<P, O>, viewer: String?, query: String): O {
        val response = raw(method.nsid, viewer, query)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return AtJson.decodeFromString(method.output, response.bodyAsText())
    }

    private suspend fun HttpClient.timeline(viewer: String): List<String> =
        query(GetTimeline.method, viewer, "?limit=50").activities.map { it.uri.value }

    private suspend fun HttpClient.activitiesOf(author: String, viewer: String?): List<String> =
        query(GetAuthorActivities.method, viewer, "?actor=$author").activities.map { it.uri.rkey!! }

    private suspend fun HttpClient.activityStatus(uri: String, viewer: String?): HttpStatusCode =
        raw(GetActivity.method.nsid, viewer, "?uri=${uri.encodeURLParameter()}").status

    /** The commenters [viewer] is shown on [uri], after checking that the view's count matches them. */
    private suspend fun HttpClient.commenters(uri: String, viewer: String?): List<String> {
        val count = query(GetActivity.method, viewer, "?uri=${uri.encodeURLParameter()}").activity.commentCount
        val comments = query(GetComments.method, viewer, "?uri=${uri.encodeURLParameter()}&limit=100").comments
        assertEquals(count, comments.count { it.hidden != true }.toLong(), "the count matches the list")
        return comments.map { it.author.did.value }.sorted()
    }

    /** The likers [viewer] is shown on [uri], after checking that the view's count matches them. */
    private suspend fun HttpClient.likers(uri: String, viewer: String?): List<String> {
        val count = query(GetActivity.method, viewer, "?uri=${uri.encodeURLParameter()}").activity.likeCount
        val likes = query(GetLikes.method, viewer, "?uri=${uri.encodeURLParameter()}").likes.map { it.did.value }.sorted()
        assertEquals(count, likes.size.toLong(), "the count matches the list")
        return likes
    }

    private fun ApplicationTestBuilder.start() {
        application { appView(config, db, verifier) }
    }

    @Test
    fun blockHidesActivitiesBothWays() = testApplication {
        start()
        listOf(alice, bob).forEach(::profile)
        follow(bob, alice)
        follow(alice, bob)
        post(alice, "3kpa")
        post(alice, "3kfa", Visibility.FOLLOWERS)
        post(bob, "3kpb")
        assertEquals(setOf(activityUri(alice, "3kpa"), activityUri(alice, "3kfa"), activityUri(bob, "3kpb")), client.timeline(bob).toSet())

        block(alice, bob)
        assertEquals(listOf(activityUri(bob, "3kpb")), client.timeline(bob), "bob sees none of alice's activities")
        assertEquals(listOf(activityUri(alice, "3kpa"), activityUri(alice, "3kfa")).toSet(), client.timeline(alice).toSet())
        assertEquals(emptyList(), client.activitiesOf(alice, bob))
        assertEquals(emptyList(), client.activitiesOf(bob, alice), "the blocker doesn't see the blocked either")
        val unknown = client.activityStatus(activityUri(alice, "3kmissing"), bob)
        assertEquals(unknown, client.activityStatus(activityUri(alice, "3kpa"), bob), "a Public activity answers as one that doesn't exist")
        assertEquals(unknown, client.activityStatus(activityUri(bob, "3kpb"), alice))
        assertEquals(HttpStatusCode.OK, client.activityStatus(activityUri(alice, "3kpa"), null), "signed-out viewers are in no block")

        unblock(alice, bob)
        assertEquals(3, client.timeline(bob).size, "unblocked: follows count again without new records")
    }

    @Test
    fun blockMakesFollowsIneffective() = testApplication {
        start()
        listOf(alice, bob, carol).forEach(::profile)
        follow(bob, alice)
        follow(carol, alice)
        block(bob, alice)

        val aliceProfile = client.query(GetProfile.method, carol, "?actor=$alice")
        assertEquals(1L, aliceProfile.followersCount, "bob's follow doesn't count")
        assertEquals(listOf(carol), client.query(GetFollowers.method, carol, "?actor=$alice").followers.map { it.did.value })
        assertEquals(emptyList(), client.query(GetFollowRequests.method, alice, "").requests, "a blocked follow isn't a request")

        val bobsView = client.query(GetProfile.method, bob, "?actor=$alice").viewer!!
        assertEquals(null, bobsView.followRequested, "the follow neither counts nor waits while blocked")
        assertEquals("at://$bob/${Nsid.GRAPH_BLOCK}/$alice", bobsView.blocking?.value)

        unblock(bob, alice)
        assertEquals(2L, client.query(GetProfile.method, carol, "?actor=$alice").followersCount)
    }

    @Test
    fun blockedProfileAnswersAsNotFound() = testApplication {
        start()
        listOf(alice, bob, carol).forEach(::profile)
        follow(carol, alice)
        follow(bob, carol)
        block(alice, bob)

        val unknown = client.raw(GetProfile.method.nsid, bob, "?actor=did:plc:nobody")
        val blocked = client.raw(GetProfile.method.nsid, bob, "?actor=$alice")
        assertEquals(unknown.status, blocked.status)
        assertEquals(unknown.bodyAsText().replace("did:plc:nobody", alice), blocked.bodyAsText(), "nothing says bob is blocked")
        assertEquals(unknown.status, client.raw(GetFollowers.method.nsid, bob, "?actor=$alice").status)

        val alicesView = client.query(GetProfile.method, alice, "?actor=$bob")
        assertEquals("at://$alice/${Nsid.GRAPH_BLOCK}/$bob", alicesView.viewer?.blocking?.value, "the blocker sees their own block")
        assertNull(client.query(GetProfile.method, carol, "?actor=$bob").viewer?.blocking, "a block is only the blocker's to see")

        // Carol's followers are bob and alice: each is left out of the list the other sees.
        follow(alice, carol)
        assertEquals(listOf(alice, bob), client.query(GetFollowers.method, carol, "?actor=$carol").followers.map { it.did.value }.sorted())
        assertEquals(listOf(alice), client.query(GetFollowers.method, alice, "?actor=$carol").followers.map { it.did.value })
        assertEquals(listOf(bob), client.query(GetFollowers.method, bob, "?actor=$carol").followers.map { it.did.value })
    }

    @Test
    fun searchLeavesOutBlockedAccounts() = testApplication {
        start()
        listOf(alice, bob, carol).forEach(::profile)
        block(alice, bob)
        assertEquals(emptyList(), client.query(SearchActors.method, bob, "?q=alice").actors)
        assertEquals(emptyList(), client.query(SearchActors.method, alice, "?q=bob").actors)
        assertEquals(listOf(alice), client.query(SearchActors.method, carol, "?q=alice").actors.map { it.did.value })
    }

    @Test
    fun blockHidesLikesAndCommentsBetweenThem() = testApplication {
        start()
        listOf(alice, bob, carol).forEach(::profile)
        post(alice, "3kpa")
        post(carol, "3kpc")
        comment(bob, alice, "3kpa")
        like(bob, alice, "3kpa")
        comment(alice, carol, "3kpc")
        comment(bob, carol, "3kpc")
        like(alice, carol, "3kpc")
        like(bob, carol, "3kpc")

        block(alice, bob)
        assertEquals(
            emptyList(),
            client.commenters(activityUri(alice, "3kpa"), carol),
            "bob's comment on alice's activity is gone for everyone",
        )
        assertEquals(emptyList(), client.likers(activityUri(alice, "3kpa"), carol))
        assertEquals(emptyList(), client.commenters(activityUri(alice, "3kpa"), alice))

        val carols = activityUri(carol, "3kpc")
        assertEquals(listOf(alice), client.commenters(carols, alice), "alice isn't shown bob's comment")
        assertEquals(listOf(bob), client.commenters(carols, bob), "bob isn't shown alice's")
        assertEquals(listOf(alice, bob), client.commenters(carols, carol), "carol sees both")
        assertEquals(listOf(alice), client.likers(carols, alice))
        assertEquals(listOf(alice, bob), client.likers(carols, carol))

        unblock(alice, bob)
        assertEquals(listOf(bob), client.commenters(activityUri(alice, "3kpa"), carol), "unblocked: earlier comments come back")
    }

    @Test
    fun blueskyBlocksCountOnlyForAthletes() = testApplication {
        start()
        profile(alice)
        bskyBlock("did:plc:stranger", alice)
        assertTrue(db.tx { Actors.selectAll().where { Actors.did eq "did:plc:stranger" }.empty() }, "a non-athlete gains no actors row")
        assertTrue(db.tx { Blocks.selectAll().empty() }, "and their block isn't kept")

        bskyBlock(alice, carol)
        assertEquals(1, db.tx { Blocks.selectAll().count() }, "an athlete's block of a non-athlete is kept")
        profile(carol)
        post(carol, "3kpc")
        assertEquals(emptyList(), client.activitiesOf(carol, alice), "it applies once carol joins")

        commit(alice, Nsid.BSKY_GRAPH_BLOCK, "3kbs${carol.last()}", null)
        assertEquals(listOf("3kpc"), client.activitiesOf(carol, alice))
    }

    @Test
    fun bothKindsOfBlockAreListedAndKeptApart() = testApplication {
        start()
        listOf(alice, bob, carol).forEach(::profile)
        block(alice, bob, at = "2026-10-21T09:00:00Z")
        bskyBlock(alice, bob, at = "2025-01-01T00:00:00Z")
        bskyBlock(alice, carol, at = "2026-10-22T09:00:00Z")

        val blocks = client.query(GetBlocks.method, alice, "").blocks
        assertEquals(listOf(carol, bob), blocks.map { it.actor.did.value }, "newest first")
        assertEquals(listOf(BlockSource.BLUESKY), blocks[0].sources)
        assertEquals(listOf(BlockSource.APP, BlockSource.BLUESKY), blocks[1].sources)
        assertEquals(emptyList(), client.query(GetBlocks.method, bob, "").blocks, "only ever the caller's own")
        assertEquals(HttpStatusCode.Unauthorized, client.raw(GetBlocks.method.nsid, null, "").status)

        post(bob, "3kpb")
        unblock(alice, bob)
        assertEquals(emptyList(), client.activitiesOf(bob, alice), "the Bluesky block still holds")
    }

    @Test
    fun blocksOnlyFromTheInteractionsSpaceAtTheirSubjectsKey() = testApplication {
        start()
        listOf(alice, bob).forEach(::profile)
        block(alice, bob, rkey = "elsewhere")
        spaceWrite(
            SpaceUri.of(Did(alice), Nsid.SPACE_ATHLETE, AthleteSpace.FOLLOWERS),
            Nsid.GRAPH_BLOCK,
            bob,
            """{"subject":"$bob","createdAt":"2026-10-21T09:00:00Z"}""",
        )
        commit(alice, Nsid.GRAPH_BLOCK, bob, """{"subject":"$bob","createdAt":"2026-10-21T09:00:00Z"}""")
        block(alice, alice)
        assertTrue(db.tx { Blocks.selectAll().empty() }, "another rkey, a tier space, the repo and a self-block are all ignored")

        block(alice, bob)
        mute(alice, carol)
        runBlocking { indexer.dropSpace(interactions(alice)) }
        assertTrue(db.tx { Blocks.selectAll().empty() && Mutes.selectAll().empty() }, "a deleted space takes its blocks and mutes")
    }

    @Test
    fun muteHidesOnlyFromTheMuter() = testApplication {
        start()
        listOf(alice, bob, carol).forEach(::profile)
        follow(alice, bob)
        follow(carol, bob)
        follow(bob, alice)
        post(bob, "3kpb")
        post(alice, "3kfa", Visibility.FOLLOWERS)
        post(carol, "3kpc")
        comment(bob, carol, "3kpc")

        mute(alice, bob)
        assertEquals(emptyList(), client.timeline(alice).filter { it.startsWith("at://$bob/") }, "bob leaves alice's timeline")
        assertEquals(listOf("3kpb"), client.activitiesOf(bob, alice), "but his profile still lists them")
        assertEquals(emptyList(), client.commenters(activityUri(carol, "3kpc"), alice), "alice isn't shown bob's comment")
        assertEquals(listOf(bob), client.commenters(activityUri(carol, "3kpc"), carol), "carol is")
        assertEquals(
            listOf(activityUri(alice, "3kfa")),
            client.timeline(bob).filter { it.startsWith("at://$alice/") },
            "bob still sees alice",
        )
        assertEquals("at://$alice/${Nsid.GRAPH_MUTE}/$bob", client.query(GetProfile.method, alice, "?actor=$bob").viewer?.muting?.value)
        assertNull(client.query(GetProfile.method, bob, "?actor=$alice").viewer?.muting, "nothing tells bob")
        assertEquals(listOf(bob), client.query(GetMutes.method, alice, "").mutes.map { it.did.value })
        assertEquals(emptyList(), client.query(GetMutes.method, bob, "").mutes)

        unmute(alice, bob)
        assertEquals(listOf(activityUri(bob, "3kpb")), client.timeline(alice).filter { it.startsWith("at://$bob/") })
    }
}
