package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.index.HandleLookup
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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

/** Index events the way Jetstream delivers them, then read them back through the XRPC API. */
class AppViewTest {
    private val service = "did:web:localhost%3A8081"
    private val alice = "did:plc:alice"
    private val bob = "did:plc:bob"
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
            """{"sport":"run","startedAt":"2026-09-30T10:00:00Z","elapsedTime":1800,"createdAt":"2026-09-30T10:31:00Z","distance":5000}""",
        )
        commit(bob, Nsid.ACTOR_PROFILE, "self", """{"displayName":"Bob"}""")
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

        val profile = client.get("/xrpc/${GetProfile.method.nsid}?actor=bob.test")
        val view = AtJson.decodeFromString(GetProfile.method.output, profile.bodyAsText())
        assertEquals(1, view.activitiesCount)
        assertEquals(1, view.followersCount)

        // Deleting the kudos record removes it from counts.
        commit(alice, Nsid.FEED_KUDOS, "3kk1", "", op = "delete")
        val after = client.get("/xrpc/${GetActivity.method.nsid}?uri=$activityUri")
        assertEquals(
            0,
            AtJson.decodeFromString(GetActivity.Output.serializer(), after.bodyAsText()).activity.kudosCount,
        )
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
