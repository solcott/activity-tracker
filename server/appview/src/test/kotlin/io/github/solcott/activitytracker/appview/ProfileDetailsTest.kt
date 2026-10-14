package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.PdsLookup
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.index.HandleLookup
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.Location
import io.github.solcott.activitytracker.lexicon.app.PowerZoneModel
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SportTraining
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.ThresholdEntry
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * Profile details come only from the indexed profile record: non-public ones live in athletes' tier spaces, which the AppView never stores.
 */
class ProfileDetailsTest {
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
    private val pds = PdsLookup { did -> "http://pds.test".takeIf { did.value == alice } }

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

    private fun follow(author: String, subject: String) =
        commit(
            author,
            Nsid.GRAPH_FOLLOW,
            "3kf${author.last()}${subject.last()}",
            """{"subject":"$subject","createdAt":"2026-09-30T09:00:00Z"}""",
        )

    private fun auth(did: String, nsid: String) = "Bearer ${keys.getValue(did).serviceJwt(did, service, nsid)}"

    private suspend fun HttpClient.profile(viewer: String?): ProfileView {
        val nsid = GetProfile.method.nsid
        val response = get("/xrpc/$nsid?actor=$alice") { viewer?.let { header(HttpHeaders.Authorization, auth(it, nsid)) } }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return AtJson.decodeFromString(GetProfile.method.output, response.bodyAsText())
    }

    /** Alice: a public city, weight and avatar in her record. */
    private fun ApplicationTestBuilder.seedAlice() {
        application { appView(config, db, verifier, pds) }
        commit(
            alice,
            Nsid.ACTOR_PROFILE,
            "self",
            """{"displayName":"Alice",""" +
                """"avatar":{"${'$'}type":"blob","ref":{"${'$'}link":"bafkavatar"},"mimeType":"image/jpeg","size":2048},""" +
                """"details":{"location":{"locality":"Portland","country":"US"},"weight":12}}""",
        )
    }

    @Test
    fun everyViewerSeesOnlyTheRecordsDetails() = testApplication {
        seedAlice()
        follow(bob, alice) // Bob follows Alice, but she doesn't follow him back.
        follow(alice, carol)
        follow(carol, alice) // Carol and Alice are mutuals: they read her Mutuals tier space themselves, not through the AppView.

        val public = ProfileDetails(location = Location(locality = "Portland", country = "US"), weight = 12)
        for (viewer in listOf(null, bob, carol, alice)) assertEquals(public, client.profile(viewer).details, "viewer $viewer")
    }

    @Test
    fun avatarRedirectsToPds() = testApplication {
        seedAlice()
        val view = client.profile(viewer = null)
        assertEquals("${config.publicUrl}/img/avatar/$alice/bafkavatar", view.avatar)

        val noRedirects = createClient { followRedirects = false }
        val redirect = noRedirects.get("/img/avatar/$alice/bafkavatar")
        assertEquals(HttpStatusCode.Found, redirect.status)
        assertEquals(
            "http://pds.test/xrpc/com.atproto.sync.getBlob?did=did%3Aplc%3Aalice&cid=bafkavatar",
            redirect.headers[HttpHeaders.Location],
        )
        // Only the athlete's current avatar is served, not any blob.
        assertEquals(HttpStatusCode.NotFound, noRedirects.get("/img/avatar/$alice/bafkother").status)
    }

    @Test
    fun theAppViewNoLongerTakesPrivateDetails() = testApplication {
        seedAlice()
        val put = "app.activitytracker.actor.putPrivateDetails"
        val response =
            client.post("/xrpc/$put") {
                header(HttpHeaders.Authorization, auth(alice, put))
                contentType(ContentType.Application.Json)
                setBody("""{"details":{"weight":61500},"visibility":{"weight":"mutuals"}}""")
            }
        assertEquals(HttpStatusCode.NotFound, response.status)
        val get = "app.activitytracker.actor.getPrivateDetails"
        assertEquals(HttpStatusCode.NotFound, client.get("/xrpc/$get") { header(HttpHeaders.Authorization, auth(alice, get)) }.status)
    }

    @Test
    fun heightInTheRecordIsShownAndKeptInRange() = testApplication {
        seedAlice()
        commit(alice, Nsid.ACTOR_PROFILE, "self", """{"details":{"height":1700}}""")
        assertEquals(1_700, client.profile(viewer = null).details?.height)
        commit(alice, Nsid.ACTOR_PROFILE, "self", """{"details":{"height":9000}}""")
        assertNull(client.profile(viewer = bob).details?.height)
    }

    @Test
    fun trainingInTheRecordIsSanitized() = testApplication {
        seedAlice()
        // A public copy in the record is indexed and shown to everyone; an out-of-range value in it is dropped.
        commit(
            alice,
            Nsid.ACTOR_PROFILE,
            "self",
            """{"details":{"training":{"functionalThresholdPower":250,"maxHeartRate":999,"powerZoneModel":"coggan7"}}}""",
        )
        assertEquals(
            TrainingDetails(functionalThresholdPower = 250, powerZoneModel = PowerZoneModel.COGGAN_7),
            client.profile(viewer = null).details?.training,
        )
    }

    @Test
    fun sportOverridesInTheRecordAreSanitized() = testApplication {
        seedAlice()
        // A repeated sport and an out-of-range speed are dropped.
        val sports = """[{"sport":"run","thresholdSpeed":3271},{"sport":"run","thresholdSpeed":1},{"sport":"swim","thresholdSpeed":5}]"""
        commit(alice, Nsid.ACTOR_PROFILE, "self", """{"details":{"training":{"sports":$sports}}}""")
        assertEquals(
            TrainingDetails(sports = listOf(SportTraining(sport = Sport.RUN, thresholdSpeed = 3271), SportTraining(sport = Sport.SWIM))),
            client.profile(viewer = null).details?.training,
        )
    }

    @Test
    fun thresholdHistoryInTheRecordIsSanitized() = testApplication {
        seedAlice()
        // An out-of-range value is dropped and a long device name truncated.
        val at = Instant.parse("2026-10-01T07:00:00Z")
        val device = "x".repeat(700)
        val history =
            """[{"threshold":"maxHeartRate","value":190,"setAt":"2026-10-01T07:00:00Z","device":"$device"},""" +
                """{"threshold":"maxHeartRate","value":99999,"setAt":"2026-10-02T07:00:00Z"}]"""
        commit(alice, Nsid.ACTOR_PROFILE, "self", """{"details":{"training":{"history":$history}}}""")
        assertEquals(
            TrainingDetails(history = listOf(ThresholdEntry(Threshold.MAX_HEART_RATE, 190, at, device = device.take(640)))),
            client.profile(viewer = null).details?.training,
        )
    }
}
