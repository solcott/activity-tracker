package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.index.HandleLookup
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetFollowers
import io.github.solcott.activitytracker.lexicon.app.GetFollows
import io.github.solcott.activitytracker.lexicon.app.GetKudos
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Index events the way Jetstream delivers them, then read them back through the XRPC API. */
class AppViewTest {
    private val service = "did:web:localhost%3A8081"
    private val alice = "did:plc:alice"
    private val bob = "did:plc:bob"
    private val carol = "did:plc:carol"
    private val keys = mapOf(alice to TestAccountKey(), bob to TestAccountKey())

    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val indexer = Indexer(db, HandleLookup { did -> did.value.substringAfterLast(':') + ".test" })
    private val config = AppViewConfig.fromEnv(emptyMap())
    private val verifier = ServiceAuthVerifier(service, keys = { keys[it.value]?.multibase })

    private fun commit(
        did: String,
        collection: String,
        rkey: String,
        record: String,
        op: String = "create",
    ) = runBlocking {
        indexer.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-09-30T12:00:00.000000Z",
                operation = op,
                collection = collection,
                rkey = rkey,
                record = if (op == "delete") null else AtJson.parseToJsonElement(record).jsonObject,
                cid = "bafy$rkey",
            )
        )
    }

    /** Bob posts a run that Alice follows, gives kudos to and comments on, plus one garbage record. Returns the run's URI. */
    private fun seedBobsRunWithAlicesFollowKudosAndComment(): String {
        val activityUri = "at://$bob/${Nsid.FEED_ACTIVITY}/3kbob1"
        commit(
            bob,
            Nsid.FEED_ACTIVITY,
            "3kbob1",
            """{"sport":"run","startedAt":"2026-09-30T10:00:00Z","elapsedTime":1800,"createdAt":"2026-09-30T10:31:00Z",""" +
                """"distance":5000,"avgTemperature":-55,"subSport":"trail"}""",
        )
        commit(bob, Nsid.ACTOR_PROFILE, "self", """{"displayName":"Bob","units":"imperial"}""")
        commit(
            alice,
            Nsid.GRAPH_FOLLOW,
            "3kf1",
            """{"subject":"$bob","createdAt":"2026-09-30T09:00:00Z"}""",
        )
        commit(
            alice,
            Nsid.FEED_KUDOS,
            "3kk1",
            """{"subject":{"uri":"$activityUri","cid":"bafy3kbob1"},"createdAt":"2026-09-30T11:00:00Z"}""",
        )
        commit(
            alice,
            Nsid.FEED_COMMENT,
            "3kc1",
            """{"subject":{"uri":"$activityUri","cid":"bafy3kbob1"},"text":"Nice!","createdAt":"2026-09-30T11:01:00Z"}""",
        )
        // Garbage records are ignored rather than failing the stream.
        commit(alice, Nsid.FEED_ACTIVITY, "3kbad", """{"sport":"run"}""")
        return activityUri
    }

    @Test
    fun timelineShowsFollowedActivitiesWithCounts() = testApplication {
        application { appView(config, db, verifier) }
        val activityUri = seedBobsRunWithAlicesFollowKudosAndComment()

        val nsid = GetTimeline.method.nsid
        val unauthenticated = client.get("/xrpc/$nsid")
        assertEquals(HttpStatusCode.Unauthorized, unauthenticated.status)

        val response =
            client.get("/xrpc/$nsid?limit=10") {
                header(
                    HttpHeaders.Authorization,
                    "Bearer ${keys.getValue(alice).serviceJwt(alice, service, nsid)}",
                )
            }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val timeline = AtJson.decodeFromString(GetTimeline.Output.serializer(), response.bodyAsText())
        val activity = timeline.activities.single()
        assertEquals(activityUri, activity.uri.value)
        assertEquals("Bob", activity.author.displayName)
        assertEquals("bob.test", activity.author.handle.value)
        assertEquals(1, activity.kudosCount)
        assertEquals(1, activity.commentCount)
        assertNotNull(activity.viewer?.kudos)

        val detail = client.get("/xrpc/${GetActivity.method.nsid}?uri=$activityUri")
        val output = AtJson.decodeFromString(GetActivity.Output.serializer(), detail.bodyAsText())
        assertEquals("Nice!", output.comments.single().text)
        assertEquals(-55L, output.activity.record.avgTemperature)
        assertEquals("trail", output.activity.record.subSport)
        assertEquals("trail", transaction(db) { Activities.selectAll().single()[Activities.subSport] })

        val profile = client.get("/xrpc/${GetProfile.method.nsid}?actor=bob.test")
        val view = AtJson.decodeFromString(GetProfile.method.output, profile.bodyAsText())
        assertEquals(1, view.activitiesCount)
        assertEquals(1, view.followersCount)
        assertEquals("imperial", view.units)

        // Deleting the kudos record removes it from counts.
        commit(alice, Nsid.FEED_KUDOS, "3kk1", "", op = "delete")
        val after = client.get("/xrpc/${GetActivity.method.nsid}?uri=$activityUri")
        assertEquals(
            0,
            AtJson.decodeFromString(GetActivity.Output.serializer(), after.bodyAsText()).activity.kudosCount,
        )
    }

    @Test
    fun kudosCountAthletesAndIgnoreSelfKudos() = testApplication {
        application { appView(config, db, verifier) }
        val activityUri = seedBobsRunWithAlicesFollowKudosAndComment()
        val kudos = """{"subject":{"uri":"$activityUri","cid":"bafy3kbob1"},"createdAt":"2026-09-30T11:05:00Z"}"""
        // A second kudos record from Alice, and Bob kudos-ing his own run: both written by other clients, neither counts.
        commit(alice, Nsid.FEED_KUDOS, "3kk2", kudos)
        commit(bob, Nsid.FEED_KUDOS, "3kk3", kudos)

        val detail = client.get("/xrpc/${GetActivity.method.nsid}?uri=$activityUri")
        assertEquals(1, AtJson.decodeFromString(GetActivity.Output.serializer(), detail.bodyAsText()).activity.kudosCount)
    }

    @Test
    fun listsKudosAthletesOnce() = testApplication {
        application { appView(config, db, verifier) }
        val activityUri = seedBobsRunWithAlicesFollowKudosAndComment()
        val kudos = """{"subject":{"uri":"$activityUri","cid":"bafy3kbob1"},"createdAt":"2026-09-30T11:05:00Z"}"""
        commit(alice, Nsid.FEED_KUDOS, "3kk2", kudos)
        commit(carol, Nsid.FEED_KUDOS, "3kk3", kudos)
        commit(bob, Nsid.FEED_KUDOS, "3kk4", kudos)

        suspend fun list(query: String): GetKudos.Output {
            val response = client.get("/xrpc/${GetKudos.method.nsid}?$query")
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            return AtJson.decodeFromString(GetKudos.Output.serializer(), response.bodyAsText())
        }

        val all = list("uri=${activityUri.encodeURLParameter()}")
        assertEquals(activityUri, all.uri.value)
        // Alice's duplicate is listed once and Bob's self-kudos isn't indexed.
        assertEquals(setOf(alice, carol), all.kudos.map { it.did.value }.toSet())
        assertEquals(2, all.kudos.size)
        assertEquals("carol.test", all.kudos.single { it.did.value == carol }.handle.value)

        // limit=1 walks every athlete through the cursor exactly once, despite Alice's duplicate.
        val paged = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page =
                list("uri=${activityUri.encodeURLParameter()}&limit=1" + (cursor?.let { "&cursor=${it.encodeURLParameter()}" } ?: ""))
            paged += page.kudos.map { it.did.value }
            cursor = page.cursor
        } while (cursor != null)
        assertEquals(listOf(alice, carol).sorted(), paged.sorted())

        commit(carol, Nsid.FEED_KUDOS, "3kk3", "", op = "delete")
        assertEquals(listOf(alice), list("uri=${activityUri.encodeURLParameter()}").kudos.map { it.did.value })

        val missing = client.get("/xrpc/${GetKudos.method.nsid}?uri=${"at://$bob/${Nsid.FEED_ACTIVITY}/nope".encodeURLParameter()}")
        assertEquals(HttpStatusCode.BadRequest, missing.status)
        assertEquals(true, missing.bodyAsText().contains("NotFound"))
    }

    private fun follow(author: String, subject: String, rkey: String) =
        commit(author, Nsid.GRAPH_FOLLOW, rkey, """{"subject":"$subject","createdAt":"2026-09-30T09:00:00Z"}""")

    @Test
    fun listsFollowersAndFollowsWithPagination() = testApplication {
        application { appView(config, db, verifier) }
        follow(alice, bob, "3kf1")
        follow(carol, bob, "3kf2")
        follow(bob, alice, "3kf3")
        commit(bob, Nsid.ACTOR_PROFILE, "self", """{"displayName":"Bob"}""")

        suspend fun followers(query: String): GetFollowers.Output {
            val response = client.get("/xrpc/${GetFollowers.method.nsid}?$query")
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            return AtJson.decodeFromString(GetFollowers.Output.serializer(), response.bodyAsText())
        }

        val all = followers("actor=bob.test")
        assertEquals("Bob", all.subject.displayName)
        assertEquals(setOf(alice, carol), all.followers.map { it.did.value }.toSet())
        assertEquals("carol.test", all.followers.single { it.did.value == carol }.handle.value)

        // limit=1 walks every follower through the cursor, without repeats.
        val paged = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = followers("actor=$bob&limit=1" + (cursor?.let { "&cursor=${it.encodeURLParameter()}" } ?: ""))
            paged += page.followers.map { it.did.value }
            cursor = page.cursor
        } while (cursor != null)
        assertEquals(setOf(alice, carol), paged.toSet())
        assertEquals(2, paged.size)

        val follows = client.get("/xrpc/${GetFollows.method.nsid}?actor=bob.test")
        assertEquals(
            listOf(alice),
            AtJson.decodeFromString(GetFollows.Output.serializer(), follows.bodyAsText()).follows.map { it.did.value },
        )

        // Unfollowing removes the row.
        commit(carol, Nsid.GRAPH_FOLLOW, "3kf2", "", op = "delete")
        assertEquals(listOf(alice), followers("actor=bob.test").followers.map { it.did.value })

        val unknown = client.get("/xrpc/${GetFollowers.method.nsid}?actor=nobody.test")
        assertEquals(HttpStatusCode.BadRequest, unknown.status)
        assertEquals(true, unknown.bodyAsText().contains("ActorNotFound"))
    }

    @Test
    fun servesDidDocumentAndClientMetadata() = testApplication {
        application { appView(config, db, verifier) }
        val doc = AtJson.parseToJsonElement(client.get("/.well-known/did.json").bodyAsText()) as JsonObject
        assertEquals(config.serviceDid, doc["id"]?.toString()?.trim('"'))
        val metadata = client.get("/oauth/client-metadata.json").bodyAsText()
        assertEquals(true, metadata.contains("\"dpop_bound_access_tokens\":true"))
    }
}
