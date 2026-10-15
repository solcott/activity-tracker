package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.FollowApprovals
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.HandleLookup
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
import io.github.solcott.activitytracker.lexicon.app.GetFollowRequests
import io.github.solcott.activitytracker.lexicon.app.GetFollowers
import io.github.solcott.activitytracker.lexicon.app.GetFollows
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Follow requests: an athlete with followPolicy `approve` counts a follow only once they approve it. */
class FollowRequestsTest {
    private val service = "did:web:localhost%3A8081"
    private val alice = "did:plc:alice"
    private val bob = "did:plc:bob"
    private val carol = "did:plc:carol"
    private val keys = mapOf(alice to TestAccountKey(), bob to TestAccountKey(), carol to TestAccountKey())

    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val indexer = Indexer(db, HandleLookup { did -> did.value.substringAfterLast(':') + ".test" })
    private val config = AppViewConfig.fromEnv(emptyMap())
    private val verifier = ServiceAuthVerifier(service, keys = { keys[it.value]?.multibase })

    private fun commit(did: String, collection: String, rkey: String, record: String?, op: String = "create") = runBlocking {
        indexer.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-09-30T12:00:00.000000Z",
                operation = op,
                collection = collection,
                rkey = rkey,
                record = record?.let { AtJson.parseToJsonElement(it).jsonObject },
                cid = "bafy$rkey",
            )
        )
    }

    private fun followRkey(author: String, subject: String) = "3kf${author.last()}${subject.last()}"

    private fun follow(author: String, subject: String) =
        commit(author, Nsid.GRAPH_FOLLOW, followRkey(author, subject), """{"subject":"$subject","createdAt":"2026-09-30T09:00:00Z"}""")

    private fun unfollow(author: String, subject: String) = commit(author, Nsid.GRAPH_FOLLOW, followRkey(author, subject), null, "delete")

    private fun approve(author: String, subject: String, rkey: String = subject) =
        commit(author, Nsid.GRAPH_FOLLOW_APPROVAL, rkey, """{"subject":"$subject","createdAt":"2026-09-30T09:00:00Z"}""")

    private fun unapprove(author: String, subject: String) = commit(author, Nsid.GRAPH_FOLLOW_APPROVAL, subject, null, "delete")

    private fun policy(did: String, policy: String?) =
        commit(did, Nsid.ACTOR_PROFILE, "self", """{"displayName":"x"${policy?.let { ""","followPolicy":"$it"""" }.orEmpty()}}""")

    private fun activity(did: String) =
        commit(
            did,
            Nsid.FEED_ACTIVITY,
            "3kride${did.last()}",
            """{"sport":"run","startedAt":"2026-09-30T10:00:00Z","elapsedTime":1800,"createdAt":"2026-09-30T10:31:00Z"}""",
        )

    private suspend fun <P, O> HttpClient.query(method: XrpcQuery<P, O>, viewer: String?, query: String = ""): O {
        val response =
            get("/xrpc/${method.nsid}$query") {
                viewer?.let { header(HttpHeaders.Authorization, "Bearer ${keys.getValue(it).serviceJwt(it, service, method.nsid)}") }
            }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return AtJson.decodeFromString(method.output, response.bodyAsText())
    }

    private suspend fun HttpClient.profile(actor: String, viewer: String?): ProfileView = query(GetProfile.method, viewer, "?actor=$actor")

    private suspend fun HttpClient.requests(viewer: String, query: String = ""): List<String> =
        query(GetFollowRequests.method, viewer, query).requests.map { it.did.value }

    private suspend fun HttpClient.followers(actor: String): List<String> =
        query(GetFollowers.method, null, "?actor=$actor").followers.map { it.did.value }

    private suspend fun HttpClient.follows(actor: String): List<String> =
        query(GetFollows.method, null, "?actor=$actor").follows.map { it.did.value }

    private suspend fun HttpClient.timelineAuthors(viewer: String): List<String> =
        query(GetTimeline.method, viewer).activities.map { it.author.did.value }

    private fun ApplicationTestBuilder.start() {
        application { appView(config, db, verifier) }
    }

    @Test
    fun approvalsAreIndexedOnlyAtTheSubjectsRkey() = testApplication {
        start()
        approve(alice, bob)
        approve(alice, carol, rkey = "3kapprove")
        val subjects = runBlocking { db.tx { FollowApprovals.select(FollowApprovals.subjectDid).map { it[FollowApprovals.subjectDid] } } }
        assertEquals(listOf(bob), subjects)

        unapprove(alice, bob)
        assertEquals(0, runBlocking { db.tx { FollowApprovals.selectAll().count() } })
    }

    @Test
    fun onlyApproveIsStoredAsAPolicy() = testApplication {
        start()
        fun stored() = runBlocking {
            db.tx { Actors.select(Actors.followPolicy).where { Actors.did eq alice }.single()[Actors.followPolicy] }
        }
        policy(alice, "approve")
        assertEquals(FollowPolicy.APPROVE, stored())
        policy(alice, "friends")
        assertNull(stored())
        policy(alice, "approve")
        commit(alice, Nsid.ACTOR_PROFILE, "self", null, "delete")
        assertNull(stored())
    }

    @Test
    fun openPolicyChangesNothing() = testApplication {
        start()
        follow(bob, alice)
        activity(alice)
        assertEquals(FollowPolicy.OPEN, client.profile(alice, null).followPolicy)
        assertEquals(1, client.profile(alice, null).followersCount)
        assertEquals(listOf(bob), client.followers(alice))
        assertEquals(listOf(alice), client.timelineAuthors(bob))
        assertNull(client.profile(alice, bob).viewer?.followRequested)
        assertEquals(emptyList(), client.requests(alice))
    }

    @Test
    fun pendingFollowerIsLeftOutOfEverything() = testApplication {
        start()
        policy(alice, "approve")
        follow(bob, alice)
        activity(alice)

        val seenByBob = client.profile(alice, bob)
        assertEquals(FollowPolicy.APPROVE, seenByBob.followPolicy)
        assertEquals(0, seenByBob.followersCount)
        assertEquals(true, seenByBob.viewer?.followRequested)
        assertEquals("at://$bob/${Nsid.GRAPH_FOLLOW}/${followRkey(bob, alice)}", seenByBob.viewer?.following?.value)
        assertEquals(0, client.profile(bob, null).followsCount)
        assertEquals(emptyList(), client.followers(alice))
        assertEquals(emptyList(), client.follows(bob))
        assertEquals(emptyList(), client.timelineAuthors(bob))
        assertEquals(listOf(bob), client.requests(alice))

        approve(alice, bob)
        val approved = client.profile(alice, bob)
        assertEquals(1, approved.followersCount)
        assertNull(approved.viewer?.followRequested)
        assertEquals(listOf(bob), client.followers(alice))
        assertEquals(listOf(alice), client.follows(bob))
        assertEquals(listOf(alice), client.timelineAuthors(bob))
        assertEquals(emptyList(), client.requests(alice))
    }

    @Test
    fun followedByReportsAnOpenPolicyViewersFollower() = testApplication {
        start()
        activity(alice)
        follow(bob, alice)
        assertNull(client.profile(alice, bob).viewer?.followedBy)
        follow(alice, bob)
        val seenByBob = client.profile(alice, bob).viewer
        assertEquals("at://$alice/${Nsid.GRAPH_FOLLOW}/${followRkey(alice, bob)}", seenByBob?.followedBy?.value)
        assertEquals("at://$bob/${Nsid.GRAPH_FOLLOW}/${followRkey(bob, alice)}", seenByBob?.following?.value)
        assertNull(client.profile(alice, null).viewer)
        unfollow(alice, bob)
        assertNull(client.profile(alice, bob).viewer?.followedBy)
    }

    @Test
    fun followedByWaitsForTheViewersApproval() = testApplication {
        start()
        activity(alice)
        policy(bob, "approve")
        follow(alice, bob)
        assertNull(client.profile(alice, bob).viewer?.followedBy)
        approve(bob, alice)
        assertEquals("at://$alice/${Nsid.GRAPH_FOLLOW}/${followRkey(alice, bob)}", client.profile(alice, bob).viewer?.followedBy?.value)
        unapprove(bob, alice)
        assertNull(client.profile(alice, bob).viewer?.followedBy)
    }

    @Test
    fun approvalIndexedBeforeTheFollowCounts() = testApplication {
        start()
        policy(alice, "approve")
        approve(alice, bob)
        follow(bob, alice)
        assertEquals(listOf(bob), client.followers(alice))
        assertEquals(emptyList(), client.requests(alice))
    }

    @Test
    fun refollowNeedsNoNewApproval() = testApplication {
        start()
        policy(alice, "approve")
        follow(bob, alice)
        approve(alice, bob)
        unfollow(bob, alice)
        assertNull(client.profile(alice, bob).viewer?.following)
        follow(bob, alice)
        assertEquals(listOf(bob), client.followers(alice))
    }

    @Test
    fun removingAnApprovalMakesTheFollowPendingAgain() = testApplication {
        start()
        policy(alice, "approve")
        follow(bob, alice)
        approve(alice, bob)
        unapprove(alice, bob)
        assertEquals(emptyList(), client.followers(alice))
        assertEquals(listOf(bob), client.requests(alice))
    }

    @Test
    fun turningApprovalOffMakesPendingFollowsCount() = testApplication {
        start()
        policy(alice, "approve")
        follow(bob, alice)
        follow(carol, alice)
        policy(alice, "open")
        assertEquals(setOf(bob, carol), client.followers(alice).toSet())
        assertEquals(emptyList(), client.requests(alice))
    }

    @Test
    fun requestsArePagedNewestFirstAndOnlyTheCallers() = testApplication {
        start()
        policy(alice, "approve")
        policy(bob, "approve")
        follow(bob, alice)
        follow(carol, alice)
        follow(carol, bob)
        val first = client.query(GetFollowRequests.method, alice, "?limit=1")
        assertEquals(1, first.requests.size)
        val second = client.query(GetFollowRequests.method, alice, "?limit=1&cursor=${first.cursor}")
        assertEquals(setOf(bob, carol), (first.requests + second.requests).map { it.did.value }.toSet())
        assertEquals(listOf(carol), client.requests(bob))
    }

    @Test
    fun requestsNeedAuthentication() = testApplication {
        start()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/xrpc/${GetFollowRequests.method.nsid}").status)
    }
}
